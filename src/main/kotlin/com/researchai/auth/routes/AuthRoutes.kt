package com.researchai.auth.routes

import com.researchai.auth.data.provider.GoogleAuthProvider
import com.researchai.auth.domain.models.AuthSession
import com.researchai.auth.service.AuthService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import java.util.*

/**
 * Роуты для аутентификации
 */
fun Route.authRoutes(
    googleAuthProvider: GoogleAuthProvider,
    authService: AuthService
) {
    route("/auth") {
        // Google OAuth - начало авторизации
        get("/google") {
            try {
                // Генерируем state для защиты от CSRF
                val state = UUID.randomUUID().toString()

                // Сохраняем state в сессии для проверки в callback
                call.sessions.set("oauth_state", state)

                // Редиректим на страницу авторизации Google
                val authUrl = googleAuthProvider.getAuthorizationUrl(state)
                call.respondRedirect(authUrl)
            } catch (e: Exception) {
                call.application.environment.log.error("Failed to initiate Google OAuth", e)
                call.respondRedirect("/?error=auth_failed")
            }
        }

        // Google OAuth - callback после авторизации
        get("/google/callback") {
            try {
                // Получаем параметры из callback
                val code = call.parameters["code"]
                val state = call.parameters["state"]
                val error = call.parameters["error"]

                // Проверяем наличие ошибок от Google
                if (error != null) {
                    call.application.environment.log.error("Google OAuth error: $error")
                    call.respondRedirect("/?error=$error")
                    return@get
                }

                // Проверяем наличие code
                if (code == null) {
                    call.respondRedirect("/?error=missing_code")
                    return@get
                }

                // Проверяем state для защиты от CSRF
                val savedState = call.sessions.get("oauth_state") as? String
                if (state == null || state != savedState) {
                    call.application.environment.log.error("OAuth state mismatch")
                    call.respondRedirect("/?error=invalid_state")
                    return@get
                }

                // Очищаем state из сессии
                call.sessions.clear("oauth_state")

                // Обмениваем code на access token
                val tokenResult = googleAuthProvider.exchangeCodeForToken(code)
                if (tokenResult.isFailure) {
                    call.application.environment.log.error(
                        "Failed to exchange code for token",
                        tokenResult.exceptionOrNull()
                    )
                    call.respondRedirect("/?error=token_exchange_failed")
                    return@get
                }

                val accessToken = tokenResult.getOrThrow()

                // Получаем информацию о пользователе
                val userInfoResult = googleAuthProvider.getUserInfo(accessToken)
                if (userInfoResult.isFailure) {
                    call.application.environment.log.error(
                        "Failed to get user info",
                        userInfoResult.exceptionOrNull()
                    )
                    call.respondRedirect("/?error=user_info_failed")
                    return@get
                }

                val oauthUserInfo = userInfoResult.getOrThrow()

                // Аутентифицируем пользователя (создаем или обновляем)
                val authResult = authService.authenticateWithOAuth(oauthUserInfo)
                if (authResult.isFailure) {
                    call.application.environment.log.error(
                        "Failed to authenticate user",
                        authResult.exceptionOrNull()
                    )
                    call.respondRedirect("/?error=authentication_failed")
                    return@get
                }

                val (user, jwtToken) = authResult.getOrThrow()

                // Сохраняем сессию
                val authSession = AuthSession(
                    userId = user.id,
                    email = user.email,
                    name = user.name,
                    provider = user.provider
                )
                call.sessions.set(authSession)

                // Логируем успешный вход
                call.application.environment.log.info(
                    "User authenticated: ${user.email} (${user.provider})"
                )

                // Редиректим на login.html с success флагом и токеном
                // login.html обработает токен и редиректнет на главную
                call.respondRedirect("/login.html?success=true&token=$jwtToken")
            } catch (e: Exception) {
                call.application.environment.log.error("Unexpected error in Google OAuth callback", e)
                call.respondRedirect("/?error=unexpected_error")
            }
        }

        // Logout
        get("/logout") {
            try {
                // Очищаем сессию
                call.sessions.clear<AuthSession>()
                call.respondRedirect("/?logged_out=true")
            } catch (e: Exception) {
                call.application.environment.log.error("Failed to logout", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to logout"))
            }
        }

        // Получить текущего пользователя
        get("/me") {
            try {
                val session = call.sessions.get<AuthSession>()
                if (session == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Not authenticated"))
                    return@get
                }

                call.respond(UserInfoResponse(
                    userId = session.userId,
                    email = session.email,
                    name = session.name,
                    provider = session.provider.name
                ))
            } catch (e: Exception) {
                call.application.environment.log.error("Failed to get current user", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to get user info"))
            }
        }
    }
}

/**
 * Ответ с информацией о пользователе
 */
@Serializable
data class UserInfoResponse(
    val userId: String,
    val email: String,
    val name: String,
    val provider: String
)

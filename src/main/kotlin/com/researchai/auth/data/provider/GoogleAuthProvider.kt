package com.researchai.auth.data.provider

import com.researchai.auth.domain.models.OAuthProvider
import com.researchai.auth.domain.models.OAuthUserInfo
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Конфигурация Google OAuth
 */
data class GoogleOAuthConfig(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String
)

/**
 * Провайдер для аутентификации через Google OAuth 2.0
 */
class GoogleAuthProvider(
    private val config: GoogleOAuthConfig,
    private val httpClient: HttpClient
) {

    companion object {
        private const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val USER_INFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo"
    }

    /**
     * Получить URL для редиректа на страницу авторизации Google
     */
    fun getAuthorizationUrl(state: String): String {
        return URLBuilder(AUTH_URL).apply {
            parameters.append("client_id", config.clientId)
            parameters.append("redirect_uri", config.redirectUri)
            parameters.append("response_type", "code")
            parameters.append("scope", "openid email profile")
            parameters.append("state", state)
            parameters.append("access_type", "offline")
            parameters.append("prompt", "consent")
        }.buildString()
    }

    /**
     * Обменять authorization code на access token
     */
    suspend fun exchangeCodeForToken(code: String): Result<String> {
        return try {
            val response = httpClient.post(TOKEN_URL) {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    "code=$code" +
                    "&client_id=${config.clientId}" +
                    "&client_secret=${config.clientSecret}" +
                    "&redirect_uri=${config.redirectUri}" +
                    "&grant_type=authorization_code"
                )
            }

            val tokenResponse = response.body<GoogleTokenResponse>()
            Result.success(tokenResponse.accessToken)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to exchange code for token: ${e.message}", e))
        }
    }

    /**
     * Получить информацию о пользователе по access token
     */
    suspend fun getUserInfo(accessToken: String): Result<OAuthUserInfo> {
        return try {
            val response = httpClient.get(USER_INFO_URL) {
                header("Authorization", "Bearer $accessToken")
            }

            val googleUser = response.body<GoogleUserInfo>()

            val userInfo = OAuthUserInfo(
                providerId = googleUser.id,
                email = googleUser.email,
                name = googleUser.name ?: googleUser.email,
                avatar = googleUser.picture,
                provider = OAuthProvider.GOOGLE
            )

            Result.success(userInfo)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to get user info: ${e.message}", e))
        }
    }
}

/**
 * Ответ от Google Token API
 */
@Serializable
private data class GoogleTokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_in")
    val expiresIn: Int,
    @SerialName("token_type")
    val tokenType: String,
    @SerialName("refresh_token")
    val refreshToken: String? = null
)

/**
 * Информация о пользователе от Google
 */
@Serializable
private data class GoogleUserInfo(
    val id: String,
    val email: String,
    val name: String? = null,
    @SerialName("given_name")
    val givenName: String? = null,
    @SerialName("family_name")
    val familyName: String? = null,
    val picture: String? = null,
    @SerialName("verified_email")
    val verifiedEmail: Boolean = false
)

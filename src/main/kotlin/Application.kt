package com.researchai

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.researchai.auth.data.provider.GoogleOAuthConfig
import com.researchai.auth.domain.models.AuthSession
import com.researchai.auth.domain.models.JWTConfig
import com.researchai.config.DotenvLoader
import com.researchai.config.getClaudeConfig
import com.researchai.config.getOpenAIConfig
import com.researchai.config.getHuggingFaceConfig
import com.researchai.di.AppModule
import com.researchai.services.ClaudeService
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.sessions.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

fun main(args: Array<String>) {
    // Загружаем переменные окружения из .env файла (если существует)
    DotenvLoader.load()

    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Инициализация конфигурации
    val claudeConfig = getClaudeConfig()
    val openAIConfig = getOpenAIConfig()
    val huggingFaceConfig = getHuggingFaceConfig()

    // Вспомогательная функция для получения env переменных (поддерживает .env загрузку)
    fun getEnv(key: String): String? = System.getenv(key) ?: System.getProperty(key)

    // JWT конфигурация
    val jwtConfig = JWTConfig(
        secret = getEnv("JWT_SECRET") ?: throw IllegalStateException("JWT_SECRET not set"),
        issuer = getEnv("JWT_ISSUER") ?: "researchai",
        audience = getEnv("JWT_AUDIENCE") ?: "researchai-users",
        realm = getEnv("JWT_REALM") ?: "ResearchAI",
        expirationMs = getEnv("JWT_EXPIRATION_MS")?.toLongOrNull() ?: 3600000
    )

    // Google OAuth конфигурация
    val googleOAuthConfig = if (getEnv("GOOGLE_CLIENT_ID") != null) {
        GoogleOAuthConfig(
            clientId = getEnv("GOOGLE_CLIENT_ID")!!,
            clientSecret = getEnv("GOOGLE_CLIENT_SECRET")!!,
            redirectUri = getEnv("GOOGLE_REDIRECT_URI") ?: "http://localhost:8080/auth/google/callback"
        )
    } else null

    // Email whitelist конфигурация
    val allowedEmails = getEnv("ALLOWED_EMAILS")

    // Вывод информации о доступных провайдерах
    println("✅ Claude API: Configured")
    if (openAIConfig != null) {
        println("✅ OpenAI API: Configured")
        println("   - Organization: ${openAIConfig.organizationId ?: "not specified"}")
        println("   - Project: ${openAIConfig.projectId ?: "not specified"}")
        println("   - Model: ${openAIConfig.model}")
    } else {
        println("⚠️  OpenAI API: Not configured (add OPENAI_API_KEY to .env)")
    }
    if (huggingFaceConfig != null) {
        println("✅ HuggingFace API: Configured")
        println("   - Model: ${huggingFaceConfig.model}")
    } else {
        println("⚠️  HuggingFace API: Not configured (add HUGGINGFACE_API_KEY to .env)")
    }
    if (googleOAuthConfig != null) {
        println("✅ Google OAuth: Configured")
    } else {
        println("⚠️  Google OAuth: Not configured (add GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET to .env)")
    }
    if (allowedEmails != null) {
        val emailCount = allowedEmails.split(',').filter { it.isNotBlank() }.size
        println("✅ Email Whitelist: Enabled ($emailCount allowed emails)")
    } else {
        println("⚠️  Email Whitelist: Disabled (all emails allowed)")
    }

    // Инициализация DI контейнера
    val appModule = AppModule(claudeConfig, openAIConfig, huggingFaceConfig, jwtConfig, googleOAuthConfig, allowedEmails)

    // Инициализация Legacy ClaudeService (для обратной совместимости)
    val claudeService = ClaudeService(claudeConfig)

    // Закрытие ресурсов при остановке приложения
    monitor.subscribe(ApplicationStopped) {
        claudeService.close()
        appModule.close()
    }

    // Установка плагинов
    install(CallLogging) {
        level = Level.INFO
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val uri = call.request.uri
            "$httpMethod $uri - $status"
        }
    }

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
            isLenient = true
            coerceInputValues = true
        })
    }

    install(CORS) {
        anyHost()
        allowHeader("Content-Type")
        allowHeader("Authorization")
    }

    // Сессии
    install(Sessions) {
        cookie<AuthSession>("auth_session") {
            cookie.path = "/"
            cookie.maxAgeInSeconds = jwtConfig.expirationMs / 1000
            cookie.httpOnly = true
            cookie.secure = false // true в production с HTTPS
        }
        cookie<String>("oauth_state") {
            cookie.path = "/"
            cookie.maxAgeInSeconds = 300 // 5 минут для OAuth state
            cookie.httpOnly = true
        }
    }

    // JWT Authentication
    install(Authentication) {
        jwt("jwt-auth") {
            realm = jwtConfig.realm
            verifier(
                JWT.require(Algorithm.HMAC256(jwtConfig.secret))
                    .withAudience(jwtConfig.audience)
                    .withIssuer(jwtConfig.issuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(jwtConfig.audience)) {
                    JWTPrincipal(credential.payload)
                } else null
            }
        }
    }

    // Конфигурация роутинга
    configureRouting(claudeService, claudeConfig, appModule)
}

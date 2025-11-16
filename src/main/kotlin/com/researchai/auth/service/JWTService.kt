package com.researchai.auth.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import com.researchai.auth.domain.models.JWTConfig
import com.researchai.auth.domain.models.User
import java.util.*

/**
 * Сервис для работы с JWT токенами
 */
class JWTService(private val config: JWTConfig) {

    private val algorithm = Algorithm.HMAC256(config.secret)

    /**
     * Генерировать JWT токен для пользователя
     */
    fun generateToken(user: User): String {
        val expiresAt = Date(System.currentTimeMillis() + config.expirationMs)

        return JWT.create()
            .withAudience(config.audience)
            .withIssuer(config.issuer)
            .withClaim("userId", user.id)
            .withClaim("email", user.email)
            .withClaim("name", user.name)
            .withClaim("provider", user.provider.name)
            .withExpiresAt(expiresAt)
            .sign(algorithm)
    }

    /**
     * Валидировать JWT токен
     */
    fun verifyToken(token: String): DecodedJWT? {
        return try {
            val verifier = JWT.require(algorithm)
                .withAudience(config.audience)
                .withIssuer(config.issuer)
                .build()

            verifier.verify(token)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Получить userId из токена
     */
    fun getUserIdFromToken(token: String): String? {
        return verifyToken(token)?.getClaim("userId")?.asString()
    }

    /**
     * Получить email из токена
     */
    fun getEmailFromToken(token: String): String? {
        return verifyToken(token)?.getClaim("email")?.asString()
    }
}

package com.researchai.auth.domain.models

import io.ktor.server.auth.*
import kotlinx.serialization.Serializable

/**
 * Сессия аутентификации пользователя
 * Используется для хранения данных между запросами
 */
@Serializable
data class AuthSession(
    val userId: String,
    val email: String,
    val name: String,
    val provider: OAuthProvider
) : Principal

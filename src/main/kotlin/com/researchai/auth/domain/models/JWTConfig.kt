package com.researchai.auth.domain.models

/**
 * Конфигурация JWT токенов
 */
data class JWTConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val realm: String,
    val expirationMs: Long = 3600000 // 1 час по умолчанию
)

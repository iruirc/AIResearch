package com.researchai.auth.domain.models

/**
 * Информация о пользователе, полученная от OAuth провайдера
 */
data class OAuthUserInfo(
    val providerId: String,
    val email: String,
    val name: String,
    val avatar: String? = null,
    val provider: OAuthProvider
)

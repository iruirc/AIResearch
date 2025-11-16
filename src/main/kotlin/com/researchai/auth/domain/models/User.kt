package com.researchai.auth.domain.models

import kotlinx.serialization.Serializable

/**
 * Модель пользователя системы
 */
@Serializable
data class User(
    val id: String,
    val email: String,
    val name: String,
    val provider: OAuthProvider,
    val providerId: String, // ID пользователя в OAuth провайдере
    val avatar: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis()
) {
    /**
     * Обновить время последнего входа
     */
    fun updateLastLogin(): User = copy(lastLoginAt = System.currentTimeMillis())
}

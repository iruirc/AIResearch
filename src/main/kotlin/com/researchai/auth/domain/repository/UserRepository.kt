package com.researchai.auth.domain.repository

import com.researchai.auth.domain.models.OAuthProvider
import com.researchai.auth.domain.models.User

/**
 * Интерфейс репозитория для работы с пользователями
 */
interface UserRepository {
    /**
     * Получить пользователя по ID
     */
    suspend fun getUserById(userId: String): User?

    /**
     * Получить пользователя по email
     */
    suspend fun getUserByEmail(email: String): User?

    /**
     * Получить пользователя по провайдеру и provider ID
     */
    suspend fun getUserByProvider(provider: OAuthProvider, providerId: String): User?

    /**
     * Сохранить пользователя
     */
    suspend fun saveUser(user: User): Result<User>

    /**
     * Обновить пользователя
     */
    suspend fun updateUser(user: User): Result<User>

    /**
     * Удалить пользователя
     */
    suspend fun deleteUser(userId: String): Result<Boolean>

    /**
     * Получить всех пользователей
     */
    suspend fun getAllUsers(): List<User>
}

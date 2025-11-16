package com.researchai.auth.service

import com.researchai.auth.domain.models.OAuthUserInfo
import com.researchai.auth.domain.models.User
import com.researchai.auth.domain.repository.UserRepository
import java.util.*

/**
 * Сервис для управления аутентификацией пользователей
 */
class AuthService(
    private val userRepository: UserRepository,
    private val jwtService: JWTService
) {

    /**
     * Аутентифицировать пользователя через OAuth
     * Если пользователь не существует - создать нового
     * Если существует - обновить время последнего входа
     */
    suspend fun authenticateWithOAuth(userInfo: OAuthUserInfo): Result<Pair<User, String>> {
        return try {
            // Проверяем, существует ли пользователь с таким провайдером
            val existingUser = userRepository.getUserByProvider(userInfo.provider, userInfo.providerId)

            val user = if (existingUser != null) {
                // Пользователь существует - обновляем время последнего входа
                val updatedUser = existingUser.updateLastLogin()
                userRepository.updateUser(updatedUser)
                    .getOrThrow()
            } else {
                // Пользователь не существует - создаем нового
                val newUser = User(
                    id = generateUserId(),
                    email = userInfo.email,
                    name = userInfo.name,
                    provider = userInfo.provider,
                    providerId = userInfo.providerId,
                    avatar = userInfo.avatar
                )
                userRepository.saveUser(newUser)
                    .getOrThrow()
            }

            // Генерируем JWT токен
            val token = jwtService.generateToken(user)

            Result.success(Pair(user, token))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Получить пользователя по JWT токену
     */
    suspend fun getUserFromToken(token: String): User? {
        val userId = jwtService.getUserIdFromToken(token) ?: return null
        return userRepository.getUserById(userId)
    }

    /**
     * Валидировать JWT токен
     */
    fun validateToken(token: String): Boolean {
        return jwtService.verifyToken(token) != null
    }

    /**
     * Генерировать уникальный ID для пользователя
     */
    private fun generateUserId(): String {
        return "user_${UUID.randomUUID()}"
    }
}

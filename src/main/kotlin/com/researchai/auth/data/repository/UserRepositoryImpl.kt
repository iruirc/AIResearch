package com.researchai.auth.data.repository

import com.researchai.auth.domain.models.OAuthProvider
import com.researchai.auth.domain.models.User
import com.researchai.auth.domain.repository.UserRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Реализация UserRepository с хранением в JSON файлах
 * Использует паттерн похожий на ChatSessionManager
 */
class UserRepositoryImpl(
    private val storageDir: String = "data/users"
) : UserRepository {
    private val users = mutableMapOf<String, User>()
    private val mutex = Mutex()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        // Создаем директорию для хранения если её нет
        File(storageDir).mkdirs()

        // Загружаем всех пользователей из файлов
        loadAllUsers()
    }

    /**
     * Загрузить всех пользователей из файловой системы
     */
    private fun loadAllUsers() {
        val dir = File(storageDir)
        if (!dir.exists()) return

        dir.listFiles { file -> file.extension == "json" }?.forEach { file ->
            try {
                val userJson = file.readText()
                val user = json.decodeFromString<User>(userJson)
                users[user.id] = user
            } catch (e: Exception) {
                println("⚠️  Failed to load user from ${file.name}: ${e.message}")
            }
        }

        println("✅ Loaded ${users.size} users from storage")
    }

    override suspend fun getUserById(userId: String): User? = mutex.withLock {
        users[userId]
    }

    override suspend fun getUserByEmail(email: String): User? = mutex.withLock {
        users.values.find { it.email.equals(email, ignoreCase = true) }
    }

    override suspend fun getUserByProvider(provider: OAuthProvider, providerId: String): User? = mutex.withLock {
        users.values.find { it.provider == provider && it.providerId == providerId }
    }

    override suspend fun saveUser(user: User): Result<User> = mutex.withLock {
        try {
            // Проверяем, не существует ли уже пользователь с таким email
            val existingByEmail = users.values.find { it.email.equals(user.email, ignoreCase = true) }
            if (existingByEmail != null && existingByEmail.id != user.id) {
                return@withLock Result.failure(Exception("User with email ${user.email} already exists"))
            }

            // Проверяем, не существует ли уже пользователь с таким провайдером
            val existingByProvider = users.values.find {
                it.provider == user.provider && it.providerId == user.providerId && it.id != user.id
            }
            if (existingByProvider != null) {
                return@withLock Result.failure(Exception("User with provider ${user.provider}:${user.providerId} already exists"))
            }

            // Сохраняем в память
            users[user.id] = user

            // Сохраняем в файл
            saveToFile(user)

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateUser(user: User): Result<User> = mutex.withLock {
        try {
            if (!users.containsKey(user.id)) {
                return@withLock Result.failure(Exception("User ${user.id} not found"))
            }

            // Обновляем в памяти
            users[user.id] = user

            // Сохраняем в файл
            saveToFile(user)

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteUser(userId: String): Result<Boolean> = mutex.withLock {
        try {
            // Удаляем из памяти
            users.remove(userId)

            // Удаляем файл
            val file = File(storageDir, "$userId.json")
            if (file.exists()) {
                file.delete()
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAllUsers(): List<User> = mutex.withLock {
        users.values.toList()
    }

    /**
     * Сохранить пользователя в файл
     */
    private fun saveToFile(user: User) {
        try {
            val file = File(storageDir, "${user.id}.json")
            val userJson = json.encodeToString(user)
            file.writeText(userJson)
        } catch (e: Exception) {
            println("⚠️  Failed to save user ${user.id} to file: ${e.message}")
            throw e
        }
    }
}

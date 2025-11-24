package com.researchai.data.provider.ollama

import com.researchai.domain.models.OllamaConnection
import com.researchai.domain.models.ProviderConfig
import com.researchai.domain.tokenizer.TokenCounter
import com.researchai.models.UserPreferences
import com.researchai.persistence.PreferencesStorage
import io.ktor.client.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Менеджер для управления множественными Ollama подключениями
 */
class OllamaConnectionManager(
    private val httpClient: HttpClient,
    private val preferencesStorage: PreferencesStorage,
    private val tokenCounter: TokenCounter
) {
    private val logger = LoggerFactory.getLogger(OllamaConnectionManager::class.java)

    // Кэш провайдеров для каждого подключения
    private val providers = ConcurrentHashMap<String, OllamaProvider>()

    // ID активного подключения
    private var activeConnectionId: String? = null

    /**
     * Инициализация: загрузить подключения из preferences
     */
    suspend fun initialize() {
        try {
            val preferences = preferencesStorage.load() ?: UserPreferences.default()
            val connections = preferences.ollamaConnections

            logger.info("Loading ${connections.size} Ollama connections")

            // Создать провайдеры для всех подключений
            connections.forEach { connection ->
                createProvider(connection)
            }

            // Установить активное подключение
            activeConnectionId = preferences.activeOllamaConnectionId
                ?: connections.firstOrNull { it.isDefault }?.id
                ?: connections.firstOrNull()?.id

            logger.info("Active Ollama connection: $activeConnectionId")
        } catch (e: Exception) {
            logger.error("Failed to initialize OllamaConnectionManager", e)
        }
    }

    /**
     * Добавить новое подключение
     */
    suspend fun addConnection(connection: OllamaConnection): Result<OllamaConnection> {
        return try {
            // Валидация
            val errors = connection.validate()
            if (errors.isNotEmpty()) {
                return Result.failure(Exception(errors.joinToString(", ")))
            }

            // Создать провайдер
            createProvider(connection)

            // Сохранить в preferences
            val preferences = preferencesStorage.load() ?: UserPreferences.default()
            val updatedConnections = preferences.ollamaConnections + connection

            preferencesStorage.save(
                preferences.copy(
                    ollamaConnections = updatedConnections,
                    activeOllamaConnectionId = activeConnectionId ?: connection.id
                )
            )

            // Если это первое подключение, сделать его активным
            if (activeConnectionId == null) {
                activeConnectionId = connection.id
            }

            logger.info("Added Ollama connection: ${connection.name}")
            Result.success(connection)

        } catch (e: Exception) {
            logger.error("Failed to add connection", e)
            Result.failure(e)
        }
    }

    /**
     * Обновить существующее подключение
     */
    suspend fun updateConnection(id: String, connection: OllamaConnection): Result<OllamaConnection> {
        return try {
            // Валидация
            val errors = connection.validate()
            if (errors.isNotEmpty()) {
                return Result.failure(Exception(errors.joinToString(", ")))
            }

            // Обновить провайдер
            providers.remove(id)
            createProvider(connection.copy(id = id))

            // Обновить в preferences
            val preferences = preferencesStorage.load() ?: UserPreferences.default()
            val updatedConnections = preferences.ollamaConnections.map {
                if (it.id == id) connection.copy(id = id) else it
            }

            preferencesStorage.save(
                preferences.copy(ollamaConnections = updatedConnections)
            )

            logger.info("Updated Ollama connection: ${connection.name}")
            Result.success(connection.copy(id = id))

        } catch (e: Exception) {
            logger.error("Failed to update connection", e)
            Result.failure(e)
        }
    }

    /**
     * Удалить подключение
     */
    suspend fun removeConnection(id: String): Result<Unit> {
        return try {
            // Удалить провайдер из кэша
            providers.remove(id)

            // Удалить из preferences
            val preferences = preferencesStorage.load() ?: UserPreferences.default()
            val updatedConnections = preferences.ollamaConnections.filter { it.id != id }

            // Если удаляем активное подключение, выбрать другое
            var newActiveId = activeConnectionId
            if (activeConnectionId == id) {
                newActiveId = updatedConnections.firstOrNull()?.id
                activeConnectionId = newActiveId
            }

            preferencesStorage.save(
                preferences.copy(
                    ollamaConnections = updatedConnections,
                    activeOllamaConnectionId = newActiveId
                )
            )

            logger.info("Removed Ollama connection: $id")
            Result.success(Unit)

        } catch (e: Exception) {
            logger.error("Failed to remove connection", e)
            Result.failure(e)
        }
    }

    /**
     * Получить провайдер для конкретного подключения
     */
    fun getProvider(id: String): OllamaProvider? {
        return providers[id]
    }

    /**
     * Получить активный провайдер
     */
    fun getActiveProvider(): OllamaProvider? {
        return activeConnectionId?.let { providers[it] }
    }

    /**
     * Установить активное подключение
     */
    suspend fun setActiveConnection(id: String): Result<Unit> {
        return try {
            if (!providers.containsKey(id)) {
                return Result.failure(Exception("Connection not found: $id"))
            }

            activeConnectionId = id

            // Сохранить в preferences
            val preferences = preferencesStorage.load() ?: UserPreferences.default()
            preferencesStorage.save(
                preferences.copy(activeOllamaConnectionId = id)
            )

            logger.info("Set active Ollama connection: $id")
            Result.success(Unit)

        } catch (e: Exception) {
            logger.error("Failed to set active connection", e)
            Result.failure(e)
        }
    }

    /**
     * Проверить подключение
     */
    suspend fun testConnection(id: String): Result<Boolean> {
        val provider = providers[id] ?: return Result.failure(Exception("Connection not found"))
        return provider.testConnection()
    }

    /**
     * Получить список всех подключений
     */
    suspend fun listConnections(): List<OllamaConnection> {
        return try {
            val preferences = preferencesStorage.load() ?: UserPreferences.default()
            preferences.ollamaConnections
        } catch (e: Exception) {
            logger.error("Failed to list connections", e)
            emptyList()
        }
    }

    /**
     * Получить активное подключение
     */
    suspend fun getActiveConnection(): OllamaConnection? {
        return try {
            val preferences = preferencesStorage.load() ?: UserPreferences.default()
            preferences.ollamaConnections.find { it.id == activeConnectionId }
        } catch (e: Exception) {
            logger.error("Failed to get active connection", e)
            null
        }
    }

    /**
     * Создать провайдер для подключения
     */
    private fun createProvider(connection: OllamaConnection) {
        val config = ProviderConfig.OllamaConfig(
            baseUrl = connection.baseUrl,
            keepAlive = connection.keepAlive
        )

        val provider = OllamaProvider(httpClient, config, tokenCounter)
        providers[connection.id] = provider
    }

    /**
     * Закрытие менеджера
     */
    fun close() {
        providers.clear()
        logger.info("OllamaConnectionManager closed")
    }
}

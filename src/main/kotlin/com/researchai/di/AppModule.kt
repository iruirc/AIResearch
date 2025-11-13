package com.researchai.di

import com.researchai.config.ClaudeConfig
import com.researchai.config.OpenAIConfig
import com.researchai.config.HuggingFaceConfig
import com.researchai.data.provider.AIProviderFactoryImpl
import com.researchai.data.repository.ConfigRepositoryImpl
import com.researchai.data.repository.SessionRepositoryImpl
import com.researchai.domain.provider.AIProviderFactory
import com.researchai.domain.repository.ConfigRepository
import com.researchai.domain.repository.SessionRepository
import com.researchai.domain.usecase.GetModelsUseCase
import com.researchai.domain.usecase.SendMessageUseCase
import com.researchai.services.AgentManager
import com.researchai.services.ChatCompressionService
import com.researchai.services.ChatSessionManager
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Dependency Injection контейнер для приложения
 */
class AppModule(
    private val claudeConfig: ClaudeConfig,
    private val openAIConfig: OpenAIConfig? = null,
    private val huggingFaceConfig: HuggingFaceConfig? = null
) {
    // HTTP Client
    val httpClient: HttpClient by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 300_000 // 5 минут
                connectTimeoutMillis = 10_000  // 10 секунд
                socketTimeoutMillis = 300_000  // 5 минут
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                })
            }
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.INFO
            }
        }
    }

    // Legacy services
    val chatSessionManager: ChatSessionManager by lazy {
        ChatSessionManager()
    }

    val agentManager: AgentManager by lazy {
        AgentManager()
    }

    // Repositories
    val sessionRepository: SessionRepository by lazy {
        SessionRepositoryImpl(chatSessionManager)
    }

    val configRepository: ConfigRepository by lazy {
        ConfigRepositoryImpl(claudeConfig, openAIConfig, huggingFaceConfig)
    }

    // Provider Factory
    val providerFactory: AIProviderFactory by lazy {
        AIProviderFactoryImpl(httpClient)
    }

    // Use Cases
    val sendMessageUseCase: SendMessageUseCase by lazy {
        SendMessageUseCase(
            providerFactory = providerFactory,
            sessionRepository = sessionRepository,
            configRepository = configRepository,
            agentManager = agentManager
        )
    }

    val getModelsUseCase: GetModelsUseCase by lazy {
        GetModelsUseCase(
            providerFactory = providerFactory,
            configRepository = configRepository
        )
    }

    // Compression Service
    val compressionService: ChatCompressionService by lazy {
        ChatCompressionService(
            providerFactory = providerFactory,
            configRepository = configRepository
        )
    }

    /**
     * Очистка ресурсов
     */
    fun close() {
        httpClient.close()
    }
}

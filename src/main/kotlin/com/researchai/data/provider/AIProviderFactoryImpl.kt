package com.researchai.data.provider

import com.researchai.data.provider.claude.ClaudeProvider
import com.researchai.data.provider.openai.OpenAIProvider
import com.researchai.data.provider.huggingface.HuggingFaceProvider
import com.researchai.domain.models.AIError
import com.researchai.domain.models.ProviderConfig
import com.researchai.domain.models.ProviderType
import com.researchai.domain.provider.AIProvider
import com.researchai.domain.provider.AIProviderFactory
import io.ktor.client.*

/**
 * Реализация фабрики провайдеров
 */
class AIProviderFactoryImpl(
    private val httpClient: HttpClient
) : AIProviderFactory {

    private val creators = mutableMapOf<ProviderType, (ProviderConfig) -> AIProvider>()

    init {
        // Регистрируем встроенные провайдеры
        register(ProviderType.CLAUDE) { config ->
            ClaudeProvider(httpClient, config as ProviderConfig.ClaudeConfig)
        }
        register(ProviderType.OPENAI) { config ->
            OpenAIProvider(httpClient, config as ProviderConfig.OpenAIConfig)
        }
        register(ProviderType.HUGGINGFACE) { config ->
            HuggingFaceProvider(httpClient, config as ProviderConfig.HuggingFaceConfig)
        }
    }

    override fun create(type: ProviderType, config: ProviderConfig): AIProvider {
        val creator = creators[type]
            ?: throw AIError.UnsupportedProviderException("Provider $type not registered")
        return creator(config)
    }

    override fun register(type: ProviderType, creator: (ProviderConfig) -> AIProvider) {
        creators[type] = creator
    }
}

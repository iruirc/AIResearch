package com.researchai.data.provider

import com.researchai.data.provider.claude.ClaudeProvider
import com.researchai.data.provider.openai.OpenAIProvider
import com.researchai.data.provider.huggingface.HuggingFaceProvider
import com.researchai.data.tokenizer.JTokkitTokenCounter
import com.researchai.domain.models.AIError
import com.researchai.domain.models.ProviderConfig
import com.researchai.domain.models.ProviderType
import com.researchai.domain.provider.AIProvider
import com.researchai.domain.provider.AIProviderFactory
import com.researchai.domain.tokenizer.TokenCounter
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
            val tokenCounter = createTokenCounter(config)
            ClaudeProvider(httpClient, config as ProviderConfig.ClaudeConfig, tokenCounter)
        }
        register(ProviderType.OPENAI) { config ->
            val tokenCounter = createTokenCounter(config)
            OpenAIProvider(httpClient, config as ProviderConfig.OpenAIConfig, tokenCounter)
        }
        register(ProviderType.HUGGINGFACE) { config ->
            val tokenCounter = createTokenCounter(config)
            HuggingFaceProvider(httpClient, config as ProviderConfig.HuggingFaceConfig, tokenCounter)
        }
    }

    /**
     * Создаёт TokenCounter для конкретной модели провайдера
     */
    private fun createTokenCounter(config: ProviderConfig): TokenCounter {
        val modelName = when (config) {
            is ProviderConfig.ClaudeConfig -> config.defaultModel
            is ProviderConfig.OpenAIConfig -> config.defaultModel
            is ProviderConfig.HuggingFaceConfig -> config.defaultModel
            else -> "gpt-4" // fallback
        }
        return JTokkitTokenCounter.forModel(modelName)
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

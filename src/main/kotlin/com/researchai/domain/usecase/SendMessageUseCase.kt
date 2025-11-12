package com.researchai.domain.usecase

import com.researchai.domain.models.*
import com.researchai.domain.provider.AIProviderFactory
import com.researchai.domain.repository.ChatSession
import com.researchai.domain.repository.ConfigRepository
import com.researchai.domain.repository.SessionRepository
import org.slf4j.LoggerFactory

/**
 * Use case для отправки сообщения AI-провайдеру
 */
class SendMessageUseCase(
    private val providerFactory: AIProviderFactory,
    private val sessionRepository: SessionRepository,
    private val configRepository: ConfigRepository
) {
    private val logger = LoggerFactory.getLogger(SendMessageUseCase::class.java)

    suspend operator fun invoke(
        message: String,
        sessionId: String? = null,
        providerId: ProviderType = ProviderType.CLAUDE,
        model: String? = null,
        parameters: RequestParameters = RequestParameters()
    ): Result<MessageResult> {
        return try {
            logger.info("SendMessageUseCase: Processing message for provider $providerId")

            // 1. Получаем или создаем сессию
            val session = sessionId?.let {
                logger.info("Getting existing session: $it")
                sessionRepository.getSession(it).getOrThrow()
            } ?: run {
                logger.info("Creating new session for provider: $providerId")
                sessionRepository.createSession(providerId).getOrThrow()
            }

            logger.info("Using session: ${session.id}")

            // 2. Получаем конфигурацию провайдера
            val config = configRepository.getProviderConfig(providerId)
                .getOrNull() ?: return Result.failure(
                    AIError.ConfigurationException("Provider $providerId not configured")
                )

            logger.info("Provider config loaded for: $providerId")

            // 3. Создаем провайдера
            val provider = providerFactory.create(providerId, config)

            // 4. Добавляем пользовательское сообщение в историю
            val userMessage = Message(
                role = MessageRole.USER,
                content = MessageContent.Text(message)
            )
            sessionRepository.addMessage(session.id, userMessage).getOrThrow()

            logger.info("User message added to session")

            // 5. Получаем обновленную историю
            val messages = sessionRepository.getMessages(session.id).getOrThrow()

            logger.info("Message history retrieved: ${messages.size} messages")

            // 6. Получаем системный промпт от агента если есть
            val systemPrompt = session.agentId?.let { agentId ->
                // Здесь можно интегрировать AgentManager
                null
            }

            // 7. Определяем модель
            val selectedModel = model ?: when (config) {
                is ProviderConfig.ClaudeConfig -> config.defaultModel
                is ProviderConfig.OpenAIConfig -> config.defaultModel
                is ProviderConfig.HuggingFaceConfig -> config.defaultModel
                is ProviderConfig.GeminiConfig -> config.defaultModel
                is ProviderConfig.CustomConfig -> "default"
            }

            // 8. Создаем запрос
            val request = AIRequest(
                messages = messages,
                model = selectedModel,
                parameters = parameters,
                systemPrompt = systemPrompt,
                sessionId = session.id
            )

            logger.info("Sending request to provider: $providerId, model: $selectedModel")

            // 9. Отправляем запрос
            val response = provider.sendMessage(request).getOrThrow()

            logger.info("Response received from provider: ${response.usage.totalTokens} tokens")

            // 10. Сохраняем ответ в историю
            val assistantMessage = Message(
                role = MessageRole.ASSISTANT,
                content = MessageContent.Text(response.content)
            )
            sessionRepository.addMessage(session.id, assistantMessage).getOrThrow()

            // 11. Обновляем lastAccessedAt
            sessionRepository.updateSession(
                session.copy(lastAccessedAt = System.currentTimeMillis())
            ).getOrThrow()

            logger.info("Message sent successfully")

            Result.success(
                MessageResult(
                    response = response.content,
                    sessionId = session.id,
                    usage = response.usage,
                    model = response.model,
                    providerId = providerId,
                    estimatedInputTokens = response.estimatedInputTokens,
                    estimatedOutputTokens = response.estimatedOutputTokens
                )
            )
        } catch (e: Exception) {
            logger.error("Error in SendMessageUseCase: ${e.message}", e)
            Result.failure(AIError.fromException(e as? Exception ?: Exception(e.message)))
        }
    }
}

/**
 * Результат отправки сообщения
 */
data class MessageResult(
    val response: String,
    val sessionId: String,
    val usage: TokenUsage, // Токены от API провайдера
    val model: String,
    val providerId: ProviderType,
    val estimatedInputTokens: Int = 0, // Локально подсчитанные входные токены
    val estimatedOutputTokens: Int = 0 // Локально подсчитанные выходные токены
)

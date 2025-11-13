package com.researchai.services

import com.researchai.domain.compression.*
import com.researchai.domain.models.*
import com.researchai.domain.provider.AIProviderFactory
import com.researchai.domain.repository.ConfigRepository
import com.researchai.models.ChatSession
import org.slf4j.LoggerFactory

/**
 * Сервис для сжатия диалогов
 */
class ChatCompressionService(
    private val providerFactory: AIProviderFactory,
    private val configRepository: ConfigRepository
) {
    private val logger = LoggerFactory.getLogger(ChatCompressionService::class.java)

    // Алгоритмы сжатия
    private val algorithms = mapOf(
        CompressionStrategy.FULL_REPLACEMENT to FullReplacementCompression(),
        CompressionStrategy.SLIDING_WINDOW to SlidingWindowCompression(),
        CompressionStrategy.TOKEN_BASED to TokenBasedCompression()
    )

    /**
     * Проверяет, нужно ли сжатие для данной сессии
     */
    fun shouldCompress(session: ChatSession, contextWindowSize: Int? = null): Boolean {
        val algorithm = algorithms[session.compressionConfig.strategy]
            ?: return false

        return algorithm.shouldCompress(
            messages = session.messages,
            config = session.compressionConfig,
            contextWindowSize = contextWindowSize
        )
    }

    /**
     * Выполняет сжатие диалога для указанной сессии
     * @param session Сессия чата
     * @param providerId ID провайдера для генерации суммаризации
     * @param model Модель для генерации суммаризации (опционально)
     * @return Результат сжатия
     */
    suspend fun compressSession(
        session: ChatSession,
        providerId: ProviderType = ProviderType.CLAUDE,
        model: String? = null
    ): Result<CompressionResult> {
        return try {
            logger.info("Starting compression for session ${session.id} with strategy ${session.compressionConfig.strategy}")

            val algorithm = algorithms[session.compressionConfig.strategy]
                ?: return Result.failure(
                    IllegalArgumentException("Unknown compression strategy: ${session.compressionConfig.strategy}")
                )

            // Создаем функцию для суммаризации через AI
            val summarizeFn: suspend (List<Message>) -> String = { messages ->
                summarizeMessages(messages, providerId, model)
            }

            // Выполняем сжатие
            val result = algorithm.compress(
                messages = session.messages,
                config = session.compressionConfig,
                summarize = summarizeFn
            )

            // Применяем результат к сессии
            session.archiveMessages(result.archivedMessages)
            session.replaceMessages(result.newMessages)
            session.compressionCount++

            logger.info(
                "Compression completed for session ${session.id}. " +
                        "Original: ${result.originalMessageCount}, New: ${result.newMessageCount}, " +
                        "Ratio: ${"%.2f".format(result.compressionRatio * 100)}%"
            )

            Result.success(result)
        } catch (e: Exception) {
            logger.error("Error during compression for session ${session.id}: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Генерирует суммаризацию списка сообщений через AI
     */
    private suspend fun summarizeMessages(
        messages: List<Message>,
        providerId: ProviderType,
        model: String?
    ): String {
        return try {
            logger.info("Generating summary for ${messages.size} messages using provider $providerId")

            // Получаем конфигурацию провайдера
            val config = configRepository.getProviderConfig(providerId)
                .getOrNull() ?: throw IllegalStateException("Provider $providerId not configured")

            // Создаем провайдера
            val provider = providerFactory.create(providerId, config)

            // Определяем модель
            val selectedModel = model ?: when (config) {
                is ProviderConfig.ClaudeConfig -> config.defaultModel
                is ProviderConfig.OpenAIConfig -> config.defaultModel
                is ProviderConfig.HuggingFaceConfig -> config.defaultModel
                is ProviderConfig.GeminiConfig -> config.defaultModel
                is ProviderConfig.CustomConfig -> "default"
            }

            // Формируем промпт для суммаризации
            val summaryPrompt = Message(
                role = MessageRole.USER,
                content = MessageContent.Text(
                    """
                    Пожалуйста, создай краткую, но информативную суммаризацию следующего диалога.

                    Требования к суммаризации:
                    1. Сохрани ключевые темы и контекст обсуждения
                    2. Укажи основные вопросы пользователя и ответы ассистента
                    3. Не упусти важные детали (имена, даты, технические термины)
                    4. Структурируй информацию для легкого восприятия
                    5. Будь максимально кратким, но точным

                    Формат ответа: напиши только суммаризацию, без дополнительных комментариев.
                    """.trimIndent()
                )
            )

            // Создаем запрос
            val request = AIRequest(
                messages = messages + summaryPrompt,
                model = selectedModel,
                parameters = RequestParameters(
                    maxTokens = 1024, // Ограничиваем длину суммаризации
                    temperature = 0.3  // Низкая температура для более точной суммаризации
                )
            )

            // Отправляем запрос
            val response = provider.sendMessage(request).getOrThrow()

            logger.info("Summary generated: ${response.content.length} characters")

            response.content
        } catch (e: Exception) {
            logger.error("Error generating summary: ${e.message}", e)
            // В случае ошибки возвращаем базовую суммаризацию
            generateFallbackSummary(messages)
        }
    }

    /**
     * Создает базовую суммаризацию без AI (fallback)
     */
    private fun generateFallbackSummary(messages: List<Message>): String {
        val userMessages = messages.filter { it.role == MessageRole.USER }.size
        val assistantMessages = messages.filter { it.role == MessageRole.ASSISTANT }.size

        return """
            Диалог содержит $userMessages сообщений от пользователя и $assistantMessages ответов ассистента.

            Основные темы обсуждения:
            ${messages.take(3).joinToString("\n") { msg ->
                val content = when (val c = msg.content) {
                    is MessageContent.Text -> c.text.take(100)
                    is MessageContent.MultiModal -> c.text?.take(100) ?: "[Мультимодальное сообщение]"
                }
                "- ${msg.role}: $content..."
            }}

            [Автоматически сгенерированная суммаризация]
        """.trimIndent()
    }

    /**
     * Обновляет стратегию сжатия для сессии
     */
    fun updateCompressionStrategy(
        session: ChatSession,
        newConfig: CompressionConfig
    ) {
        session.compressionConfig = newConfig
        logger.info("Compression strategy updated for session ${session.id} to ${newConfig.strategy}")
    }
}

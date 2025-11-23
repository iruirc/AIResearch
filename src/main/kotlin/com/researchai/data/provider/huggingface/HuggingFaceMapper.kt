package com.researchai.data.provider.huggingface

import com.researchai.domain.models.*

/**
 * Маппер для преобразования между domain моделями и HuggingFace API моделями
 * Поддерживает обработку reasoning тегов <think>...</think>
 */
class HuggingFaceMapper {

    /**
     * Преобразование domain запроса в HuggingFace API запрос
     */
    fun toHuggingFaceRequest(
        request: AIRequest,
        config: ProviderConfig.HuggingFaceConfig
    ): HuggingFaceApiRequest {
        val messages = request.messages.map { message ->
            val content = when (val msgContent = message.content) {
                is MessageContent.Text -> msgContent.text
                is MessageContent.MultiModal -> msgContent.text ?: ""
                is MessageContent.Structured -> {
                    // HuggingFace не поддерживает structured content, конвертируем в text
                    msgContent.blocks.joinToString("\n") { block ->
                        when (block) {
                            is ContentBlock.Text -> block.text
                            is ContentBlock.ToolUseBlock -> "[Tool: ${block.name}]"
                            is ContentBlock.ToolResultBlock -> "[Tool Result: ${block.content}]"
                        }
                    }
                }
            }

            HuggingFaceApiMessage(
                role = when (message.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                    MessageRole.SYSTEM -> "system"
                },
                content = content
            )
        }.toMutableList()

        // HuggingFace API требует минимум 1 сообщение
        // Если messages пустой, добавляем user message с system prompt
        if (messages.isEmpty() && request.systemPrompt != null) {
            messages.add(
                HuggingFaceApiMessage(
                    role = "user",
                    content = request.systemPrompt
                )
            )
        }

        return HuggingFaceApiRequest(
            model = request.model,
            messages = messages,
            temperature = request.parameters.temperature,
            maxTokens = request.parameters.maxTokens
        )
    }

    /**
     * Преобразование HuggingFace API ответа в domain модель
     * Извлекает reasoning теги и добавляет их в отдельное поле
     */
    fun fromHuggingFaceResponse(response: HuggingFaceApiResponse): AIResponse {
        val choice = response.choices.firstOrNull()
            ?: throw AIError.ParseException("No choices in HuggingFace response")

        val rawContent = choice.message.content

        // Извлекаем reasoning из <think>...</think> тегов
        val reasoning = extractReasoning(rawContent)

        // Получаем основной контент без reasoning тегов
        val cleanedContent = removeReasoningTags(rawContent)

        // Формируем финальный контент: показываем reasoning пользователю
        val finalContent = if (reasoning.isNotEmpty()) {
            buildString {
                appendLine("🧠 Рассуждение модели:")
                appendLine("━━━━━━━━━━━━━━━━")
                appendLine(reasoning)
                appendLine("━━━━━━━━━━━━━━━━")
                appendLine()
                append(cleanedContent)
            }
        } else {
            cleanedContent
        }

        return AIResponse(
            id = response.id ?: "hf-${System.currentTimeMillis()}",
            content = finalContent,
            role = MessageRole.ASSISTANT,
            model = response.model,
            usage = TokenUsage(
                inputTokens = response.usage.promptTokens,
                outputTokens = response.usage.completionTokens,
                totalTokens = response.usage.totalTokens
            ),
            finishReason = mapFinishReason(choice.finishReason),
            metadata = if (reasoning.isNotEmpty()) {
                mapOf("reasoning" to reasoning)
            } else {
                emptyMap()
            }
        )
    }

    private fun mapFinishReason(reason: String?): FinishReason {
        return when (reason) {
            "stop" -> FinishReason.STOP
            "length" -> FinishReason.MAX_TOKENS
            "content_filter" -> FinishReason.CONTENT_FILTER
            else -> FinishReason.STOP
        }
    }

    /**
     * Извлекает текст reasoning из тегов <think>...</think>
     */
    private fun extractReasoning(content: String): String {
        val thinkRegex = Regex("<think>(.*?)</think>", RegexOption.DOT_MATCHES_ALL)
        val matches = thinkRegex.findAll(content)

        return matches.joinToString("\n\n") { match ->
            match.groupValues[1].trim()
        }
    }

    /**
     * Удаляет теги <think>...</think> из контента
     */
    private fun removeReasoningTags(content: String): String {
        return content
            .replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
            .trim()
    }
}

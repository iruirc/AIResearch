package com.researchai.data.provider.claude

import com.researchai.domain.models.*
import com.researchai.models.ResponseFormat
import com.researchai.services.ClaudeMessageFormatter

/**
 * Маппер для конвертации между domain моделями и Claude API моделями
 */
class ClaudeMapper {

    fun toClaudeRequest(
        request: AIRequest,
        config: ProviderConfig.ClaudeConfig,
        formatter: ClaudeMessageFormatter
    ): ClaudeApiRequest {
        // Формируем сообщения
        val messages = request.messages.map { message ->
            val content = when (val msgContent = message.content) {
                is MessageContent.Text -> msgContent.text
                is MessageContent.MultiModal -> msgContent.text ?: ""
            }

            ClaudeApiMessage(
                role = when (message.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                    MessageRole.SYSTEM -> "user" // Claude doesn't have system role in messages
                },
                content = content
            )
        }.toMutableList()

        // Применяем форматирование для последнего пользовательского сообщения
        if (messages.isNotEmpty() && messages.last().role == "user") {
            val lastMessage = messages.last()
            val enhancedContent = formatter.enhanceMessage(
                lastMessage.content,
                request.parameters.responseFormat
            )
            messages[messages.lastIndex] = lastMessage.copy(content = enhancedContent)
        }

        return ClaudeApiRequest(
            model = request.model,
            messages = messages,
            maxTokens = request.parameters.maxTokens,
            temperature = request.parameters.temperature,
            topP = request.parameters.topP,
            topK = request.parameters.topK,
            stopSequences = request.parameters.stopSequences.takeIf { it.isNotEmpty() },
            system = request.systemPrompt
        )
    }

    fun fromClaudeResponse(
        response: ClaudeApiResponse,
        format: ResponseFormat,
        formatter: ClaudeMessageFormatter
    ): AIResponse {
        var content = response.content.firstOrNull()?.text ?: ""

        // Применяем пост-обработку форматирования
        content = formatter.processResponseByFormat(content, format)

        return AIResponse(
            id = response.id,
            content = content,
            role = MessageRole.ASSISTANT,
            model = response.model,
            usage = TokenUsage(
                inputTokens = response.usage.inputTokens,
                outputTokens = response.usage.outputTokens
            ),
            finishReason = mapStopReason(response.stopReason),
            metadata = mapOf(
                "type" to response.type,
                "stop_sequence" to (response.stopSequence ?: "")
            )
        )
    }

    private fun mapStopReason(reason: String?): FinishReason {
        return when (reason) {
            "end_turn" -> FinishReason.STOP
            "max_tokens" -> FinishReason.MAX_TOKENS
            "stop_sequence" -> FinishReason.STOP
            else -> FinishReason.STOP
        }
    }
}

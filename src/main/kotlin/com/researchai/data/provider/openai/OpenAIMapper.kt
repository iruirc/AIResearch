package com.researchai.data.provider.openai

import com.researchai.domain.models.*

/**
 * Маппер для конвертации между domain моделями и OpenAI API моделями
 */
class OpenAIMapper {

    fun toOpenAIRequest(
        request: AIRequest,
        config: ProviderConfig.OpenAIConfig
    ): OpenAIApiRequest {
        val messages = mutableListOf<OpenAIApiMessage>()

        // System prompt как отдельное сообщение
        request.systemPrompt?.let {
            messages.add(OpenAIApiMessage(role = "system", content = it))
        }

        // Добавляем остальные сообщения
        messages.addAll(request.messages.map { message ->
            val content = when (val msgContent = message.content) {
                is MessageContent.Text -> msgContent.text
                is MessageContent.MultiModal -> msgContent.text ?: ""
            }

            OpenAIApiMessage(
                role = when (message.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                    MessageRole.SYSTEM -> "system"
                },
                content = content
            )
        })

        // GPT-5 модели требуют max_completion_tokens вместо max_tokens
        val isGPT5 = request.model.startsWith("gpt-5")

        return OpenAIApiRequest(
            model = request.model,
            messages = messages,
            temperature = request.parameters.temperature,
            maxTokens = if (!isGPT5) request.parameters.maxTokens else null,
            maxCompletionTokens = if (isGPT5) request.parameters.maxTokens else null,
            topP = request.parameters.topP,
            frequencyPenalty = request.parameters.frequencyPenalty,
            presencePenalty = request.parameters.presencePenalty,
            stop = request.parameters.stopSequences.takeIf { it.isNotEmpty() }
        )
    }

    fun fromOpenAIResponse(response: OpenAIApiResponse): AIResponse {
        val choice = response.choices.firstOrNull()
            ?: throw AIError.ParseException("No choices in OpenAI response")

        return AIResponse(
            id = response.id,
            content = choice.message.content ?: "",
            role = MessageRole.ASSISTANT,
            model = response.model,
            usage = TokenUsage(
                inputTokens = response.usage.promptTokens,
                outputTokens = response.usage.completionTokens,
                totalTokens = response.usage.totalTokens
            ),
            finishReason = mapFinishReason(choice.finishReason),
            metadata = mapOf(
                "created" to response.created.toString(),
                "system_fingerprint" to (response.systemFingerprint ?: "")
            )
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
}

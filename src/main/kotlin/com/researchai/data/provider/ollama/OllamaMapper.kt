package com.researchai.data.provider.ollama

import com.researchai.domain.models.*
import com.researchai.models.ResponseFormat
import java.util.UUID

/**
 * Маппер для конвертации между domain моделями и Ollama API моделями
 */
class OllamaMapper {

    /**
     * Конвертирует AIRequest в OllamaChatRequest
     */
    fun toOllamaRequest(
        request: AIRequest,
        config: ProviderConfig.OllamaConfig
    ): OllamaChatRequest {
        val messages = buildList {
            // Системный промпт как первое сообщение
            if (request.systemPrompt != null) {
                add(OllamaMessage("system", request.systemPrompt))
            }

            // Остальные сообщения из истории
            addAll(request.messages.map {
                val contentText = when (val content = it.content) {
                    is MessageContent.Text -> content.text
                    is MessageContent.MultiModal -> content.text ?: ""
                    is MessageContent.Structured -> {
                        // Для structured контента извлекаем текстовые блоки
                        content.blocks.filterIsInstance<ContentBlock.Text>()
                            .joinToString("\n") { block -> block.text }
                    }
                }

                OllamaMessage(
                    role = it.role.name.lowercase(),
                    content = contentText
                )
            })
        }

        return OllamaChatRequest(
            model = request.model ?: "llama3.2",
            messages = messages,
            stream = false,
            temperature = request.parameters.temperature,
            format = if (request.parameters.responseFormat == ResponseFormat.JSON) "json" else null,
            keep_alive = config.keepAlive
        )
    }

    /**
     * Конвертирует OllamaChatResponse в AIResponse
     */
    fun fromOllamaResponse(response: OllamaChatResponse): AIResponse {
        return AIResponse(
            id = UUID.randomUUID().toString(),
            content = response.message.content,
            model = response.model,
            usage = TokenUsage(
                inputTokens = response.prompt_eval_count ?: 0,
                outputTokens = response.eval_count ?: 0,
                totalTokens = (response.prompt_eval_count ?: 0) + (response.eval_count ?: 0)
            ),
            finishReason = if (response.done) FinishReason.STOP else FinishReason.ERROR,
            metadata = mapOf(
                "created_at" to response.created_at,
                "total_duration_ns" to (response.total_duration ?: 0L).toString(),
                "load_duration_ns" to (response.load_duration ?: 0L).toString(),
                "eval_duration_ns" to (response.eval_duration ?: 0L).toString()
            )
        )
    }
}

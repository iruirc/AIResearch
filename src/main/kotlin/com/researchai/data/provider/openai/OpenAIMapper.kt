package com.researchai.data.provider.openai

import com.researchai.domain.models.*
import com.researchai.domain.mcp.ClaudeTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Маппер для конвертации между domain моделями и OpenAI API моделями
 */
class OpenAIMapper {

    private val json = Json { ignoreUnknownKeys = true }

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
        request.messages.forEach { message ->
            when (val msgContent = message.content) {
                is MessageContent.Text, is MessageContent.MultiModal -> {
                    val content = when (msgContent) {
                        is MessageContent.Text -> msgContent.text
                        is MessageContent.MultiModal -> msgContent.text ?: ""
                        else -> ""
                    }

                    messages.add(
                        OpenAIApiMessage(
                            role = when (message.role) {
                                MessageRole.USER -> "user"
                                MessageRole.ASSISTANT -> "assistant"
                                MessageRole.SYSTEM -> "system"
                            },
                            content = content
                        )
                    )
                }
                is MessageContent.Structured -> {
                    // Structured content: обрабатываем tool_use и tool_result blocks
                    msgContent.blocks.forEach { block ->
                        when (block) {
                            is ContentBlock.ToolUseBlock -> {
                                // Assistant message with tool_calls
                                messages.add(
                                    OpenAIApiMessage(
                                        role = "assistant",
                                        content = null,
                                        toolCalls = listOf(
                                            OpenAIToolCall(
                                                id = block.id,
                                                type = "function",
                                                function = OpenAIFunctionCall(
                                                    name = block.name,
                                                    arguments = block.input.toString()
                                                )
                                            )
                                        )
                                    )
                                )
                            }
                            is ContentBlock.ToolResultBlock -> {
                                // Tool message with result
                                messages.add(
                                    OpenAIApiMessage(
                                        role = "tool",
                                        content = block.content,
                                        toolCallId = block.toolUseId
                                    )
                                )
                            }
                            is ContentBlock.Text -> {
                                // Regular text message (fallback)
                                messages.add(
                                    OpenAIApiMessage(
                                        role = when (message.role) {
                                            MessageRole.USER -> "user"
                                            MessageRole.ASSISTANT -> "assistant"
                                            MessageRole.SYSTEM -> "system"
                                        },
                                        content = block.text
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // GPT-5 модели требуют max_completion_tokens вместо max_tokens
        val isGPT5 = request.model.startsWith("gpt-5")

        // Конвертация MCP tools в OpenAI format
        val openAITools = if (request.tools.isNotEmpty()) {
            request.tools.map { convertToOpenAITool(it) }
        } else null

        return OpenAIApiRequest(
            model = request.model,
            messages = messages,
            temperature = request.parameters.temperature,
            maxTokens = if (!isGPT5) request.parameters.maxTokens else null,
            maxCompletionTokens = if (isGPT5) request.parameters.maxTokens else null,
            topP = request.parameters.topP,
            frequencyPenalty = request.parameters.frequencyPenalty,
            presencePenalty = request.parameters.presencePenalty,
            stop = request.parameters.stopSequences.takeIf { it.isNotEmpty() },
            tools = openAITools,
            toolChoice = if (openAITools != null) "auto" else null
        )
    }

    /**
     * Конвертация ClaudeTool в OpenAI Tool format
     */
    private fun convertToOpenAITool(tool: ClaudeTool): OpenAITool {
        return OpenAITool(
            type = "function",
            function = OpenAIFunction(
                name = tool.name,
                description = tool.description,
                parameters = tool.input_schema
            )
        )
    }

    fun fromOpenAIResponse(response: OpenAIApiResponse): AIResponse {
        val choice = response.choices.firstOrNull()
            ?: throw AIError.ParseException("No choices in OpenAI response")

        // Извлечение tool_calls если они есть
        val toolUses = extractToolCalls(choice.message)
        val finishReason = if (toolUses.isNotEmpty() || choice.finishReason == "tool_calls") {
            FinishReason.TOOL_USE
        } else {
            mapFinishReason(choice.finishReason)
        }

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
            finishReason = finishReason,
            toolUses = toolUses,
            metadata = mapOf(
                "created" to response.created.toString(),
                "system_fingerprint" to (response.systemFingerprint ?: "")
            )
        )
    }

    /**
     * Извлечение tool_calls из OpenAI message и конвертация в ToolUse
     */
    private fun extractToolCalls(message: OpenAIApiMessage): List<ToolUse> {
        val toolCalls = message.toolCalls ?: return emptyList()

        return toolCalls.mapNotNull { toolCall ->
            try {
                // Парсим JSON string в JsonElement
                val inputJson = json.parseToJsonElement(toolCall.function.arguments)
                ToolUse(
                    id = toolCall.id,
                    name = toolCall.function.name,
                    input = inputJson
                )
            } catch (e: Exception) {
                // Если парсинг не удался, пропускаем этот tool call
                null
            }
        }
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

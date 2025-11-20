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
            val claudeContent = when (val msgContent = message.content) {
                is MessageContent.Text -> ClaudeApiMessageContent.Text(msgContent.text)
                is MessageContent.MultiModal -> ClaudeApiMessageContent.Text(msgContent.text ?: "")
                is MessageContent.Structured -> {
                    // Конвертируем domain ContentBlocks в Claude API ContentBlocks
                    val claudeBlocks = msgContent.blocks.map { block ->
                        when (block) {
                            is ContentBlock.Text -> ClaudeContentBlock.Text(text = block.text)
                            is ContentBlock.ToolUseBlock -> ClaudeContentBlock.ToolUse(
                                id = block.id,
                                name = block.name,
                                input = block.input
                            )
                            is ContentBlock.ToolResultBlock -> ClaudeContentBlock.ToolResult(
                                toolUseId = block.toolUseId,
                                content = block.content
                            )
                        }
                    }
                    ClaudeApiMessageContent.Structured(claudeBlocks)
                }
            }

            ClaudeApiMessage(
                role = when (message.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                    MessageRole.SYSTEM -> "user" // Claude doesn't have system role in messages
                },
                content = claudeContent
            )
        }.toMutableList()

        // Применяем форматирование для последнего пользовательского сообщения
        if (messages.isNotEmpty() && messages.last().role == "user") {
            val lastMessage = messages.last()
            val lastContent = when (val content = lastMessage.content) {
                is ClaudeApiMessageContent.Text -> content.value
                is ClaudeApiMessageContent.Structured -> ""
            }
            val enhancedContent = formatter.enhanceMessage(
                lastContent,
                request.parameters.responseFormat
            )
            messages[messages.lastIndex] = lastMessage.copy(
                content = ClaudeApiMessageContent.Text(enhancedContent)
            )
        }

        // Конвертируем MCP tools в Claude API формат
        val claudeTools = if (request.tools.isNotEmpty()) {
            request.tools.map { tool ->
                ClaudeApiTool(
                    name = tool.name,
                    description = tool.description,
                    inputSchema = tool.input_schema
                )
            }
        } else null

        return ClaudeApiRequest(
            model = request.model,
            messages = messages,
            maxTokens = request.parameters.maxTokens,
            temperature = request.parameters.temperature,
            topP = request.parameters.topP,
            topK = request.parameters.topK,
            stopSequences = request.parameters.stopSequences.takeIf { it.isNotEmpty() },
            system = request.systemPrompt,
            tools = claudeTools
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

        // Extract tool uses
        val toolUses = extractToolUses(response)

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
            ),
            toolUses = toolUses
        )
    }

    private fun mapStopReason(reason: String?): FinishReason {
        return when (reason) {
            "end_turn" -> FinishReason.STOP
            "max_tokens" -> FinishReason.MAX_TOKENS
            "stop_sequence" -> FinishReason.STOP
            "tool_use" -> FinishReason.TOOL_USE
            else -> FinishReason.STOP
        }
    }

    /**
     * Extract tool uses from Claude API response
     */
    fun extractToolUses(response: ClaudeApiResponse): List<ToolUse> {
        return response.content
            .filter { it.type == "tool_use" }
            .mapNotNull { content ->
                if (content.id != null && content.name != null && content.input != null) {
                    ToolUse(
                        id = content.id,
                        name = content.name,
                        input = content.input
                    )
                } else null
            }
    }
}

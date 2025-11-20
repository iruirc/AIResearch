package com.researchai.data.provider.claude

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Модели для Claude API
 */

@Serializable
data class ClaudeApiRequest(
    val model: String,
    val messages: List<ClaudeApiMessage>,
    @SerialName("max_tokens")
    val maxTokens: Int,
    val temperature: Double = 1.0,
    @SerialName("top_p")
    val topP: Double = 1.0,
    @SerialName("top_k")
    val topK: Int? = null,
    @SerialName("stop_sequences")
    val stopSequences: List<String>? = null,
    val system: String? = null,
    val tools: List<ClaudeApiTool>? = null
)

@Serializable
data class ClaudeApiMessage(
    val role: String,
    val content: ClaudeApiMessageContent
)

@Serializable(with = ClaudeApiMessageContentSerializer::class)
sealed class ClaudeApiMessageContent {
    @Serializable
    data class Text(val value: String) : ClaudeApiMessageContent()

    @Serializable
    data class Structured(val blocks: List<ClaudeContentBlock>) : ClaudeApiMessageContent()
}

@Serializable
sealed class ClaudeContentBlock {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : ClaudeContentBlock()

    @Serializable
    @SerialName("tool_use")
    data class ToolUse(
        val id: String,
        val name: String,
        val input: JsonElement
    ) : ClaudeContentBlock()

    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        @SerialName("tool_use_id")
        val toolUseId: String,
        val content: String
    ) : ClaudeContentBlock()
}

@Serializable
data class ClaudeApiTool(
    val name: String,
    val description: String,
    @SerialName("input_schema")
    val inputSchema: JsonElement
)

@Serializable
data class ClaudeApiResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ClaudeApiContent>,
    val model: String,
    @SerialName("stop_reason")
    val stopReason: String?,
    @SerialName("stop_sequence")
    val stopSequence: String? = null,
    val usage: ClaudeApiUsage
)

@Serializable
data class ClaudeApiContent(
    val type: String,
    val text: String? = null,
    val id: String? = null,
    val name: String? = null,
    val input: JsonElement? = null
)

@Serializable
data class ClaudeApiUsage(
    @SerialName("input_tokens")
    val inputTokens: Int,
    @SerialName("output_tokens")
    val outputTokens: Int
)

@Serializable
data class ClaudeApiError(
    val type: String,
    val error: ClaudeApiErrorDetails
)

@Serializable
data class ClaudeApiErrorDetails(
    val type: String,
    val message: String
)

/**
 * Custom serializer for ClaudeApiMessageContent to handle both string and structured content
 */
class ClaudeApiMessageContentSerializer : kotlinx.serialization.KSerializer<ClaudeApiMessageContent> {
    override val descriptor = kotlinx.serialization.descriptors.buildClassSerialDescriptor("ClaudeApiMessageContent")

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: ClaudeApiMessageContent) {
        when (value) {
            is ClaudeApiMessageContent.Text -> encoder.encodeString(value.value)
            is ClaudeApiMessageContent.Structured -> {
                encoder.encodeSerializableValue(
                    kotlinx.serialization.builtins.ListSerializer(ClaudeContentBlock.serializer()),
                    value.blocks
                )
            }
        }
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): ClaudeApiMessageContent {
        // For deserialization, we need to peek at the JSON to determine if it's string or array
        val jsonDecoder = decoder as? kotlinx.serialization.json.JsonDecoder
            ?: throw kotlinx.serialization.SerializationException("This serializer can only be used with Json format")

        val element = jsonDecoder.decodeJsonElement()
        return when {
            element is kotlinx.serialization.json.JsonPrimitive && element.isString -> {
                ClaudeApiMessageContent.Text(element.content)
            }
            element is kotlinx.serialization.json.JsonArray -> {
                val blocks = jsonDecoder.json.decodeFromJsonElement(
                    kotlinx.serialization.builtins.ListSerializer(ClaudeContentBlock.serializer()),
                    element
                )
                ClaudeApiMessageContent.Structured(blocks)
            }
            else -> throw kotlinx.serialization.SerializationException("Unexpected JSON element type")
        }
    }
}

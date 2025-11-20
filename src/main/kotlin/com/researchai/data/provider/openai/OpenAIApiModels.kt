package com.researchai.data.provider.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Модели для OpenAI API
 */

@Serializable
data class OpenAIApiRequest(
    val model: String,
    val messages: List<OpenAIApiMessage>,
    val temperature: Double = 1.0,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    @SerialName("max_completion_tokens")
    val maxCompletionTokens: Int? = null,
    @SerialName("top_p")
    val topP: Double = 1.0,
    @SerialName("frequency_penalty")
    val frequencyPenalty: Double? = null,
    @SerialName("presence_penalty")
    val presencePenalty: Double? = null,
    val stop: List<String>? = null,
    val tools: List<OpenAITool>? = null,
    @SerialName("tool_choice")
    val toolChoice: String? = null
)

@Serializable
data class OpenAIApiMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<OpenAIToolCall>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null
)

@Serializable
data class OpenAIApiResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<OpenAIChoice>,
    val usage: OpenAIUsage,
    @SerialName("system_fingerprint")
    val systemFingerprint: String? = null
)

@Serializable
data class OpenAIChoice(
    val index: Int,
    val message: OpenAIApiMessage,
    @SerialName("finish_reason")
    val finishReason: String?
)

@Serializable
data class OpenAIUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)

@Serializable
data class OpenAIModelsResponse(
    val `object`: String,
    val data: List<OpenAIModelData>
)

@Serializable
data class OpenAIModelData(
    val id: String,
    val `object`: String,
    val created: Long,
    @SerialName("owned_by")
    val ownedBy: String
)

@Serializable
data class OpenAIApiError(
    val error: OpenAIApiErrorDetails
)

@Serializable
data class OpenAIApiErrorDetails(
    val message: String,
    val type: String,
    val param: String? = null,
    val code: String? = null
)

/**
 * OpenAI Tool Definition (function calling)
 */
@Serializable
data class OpenAITool(
    val type: String = "function",
    val function: OpenAIFunction
)

@Serializable
data class OpenAIFunction(
    val name: String,
    val description: String,
    val parameters: kotlinx.serialization.json.JsonElement
)

/**
 * OpenAI Tool Call (в ответе от модели)
 */
@Serializable
data class OpenAIToolCall(
    val id: String,
    val type: String,
    val function: OpenAIFunctionCall
)

@Serializable
data class OpenAIFunctionCall(
    val name: String,
    val arguments: String  // JSON string
)

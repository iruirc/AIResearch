package com.researchai.models

import kotlinx.serialization.Serializable

/**
 * Краткая информация о сессии для списка
 */
@Serializable
data class SessionListItem(
    val id: String,
    val title: String? = null,
    val messageCount: Int,
    val createdAt: Long,
    val lastAccessedAt: Long,
    val agentId: String? = null
)

/**
 * Список всех сессий
 */
@Serializable
data class SessionListResponse(
    val sessions: List<SessionListItem>
)

/**
 * Детальная информация о сессии с историей сообщений
 */
@Serializable
data class SessionDetailResponse(
    val id: String,
    val messages: List<MessageItem>,
    val createdAt: Long,
    val lastAccessedAt: Long
)

/**
 * Сообщение для отображения в истории
 */
@Serializable
data class MessageItem(
    val role: String,
    val content: String,
    val timestamp: Long? = null,
    val metadata: MessageMetadataDTO? = null
)

/**
 * DTO для метаданных сообщения
 */
@Serializable
data class MessageMetadataDTO(
    val model: String,
    val tokensUsed: Int,
    val responseTime: Double,

    // Токены от API провайдера (реальные)
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0,

    // Токены, подсчитанные локально (оценочные)
    val estimatedInputTokens: Int = 0,
    val estimatedOutputTokens: Int = 0,
    val estimatedTotalTokens: Int = 0
)

/**
 * Общий ответ для операций без тела
 */
@Serializable
data class StatusResponse(
    val success: Boolean,
    val message: String? = null
)

/**
 * Ответ на копирование сессии
 */
@Serializable
data class CopySessionResponse(
    val success: Boolean,
    val message: String? = null,
    val newSessionId: String
)

/**
 * Информация об агенте для отображения
 */
@Serializable
data class AgentListItem(
    val id: String,
    val name: String,
    val description: String
)

/**
 * Список всех доступных агентов
 */
@Serializable
data class AgentListResponse(
    val agents: List<AgentListItem>
)

/**
 * Запрос на создание сессии с агентом
 */
@Serializable
data class CreateAgentSessionRequest(
    val agentId: String
)

/**
 * Ответ на создание сессии с агентом
 */
@Serializable
data class CreateAgentSessionResponse(
    val sessionId: String,
    val agentName: String,
    val initialMessage: String
)

/**
 * Информация о провайдере
 */
@Serializable
data class ProviderDTO(
    val id: String,
    val name: String,
    val defaultModel: String
)

/**
 * Список всех доступных провайдеров
 */
@Serializable
data class ProvidersListResponse(
    val providers: List<ProviderDTO>
)

/**
 * Информация о возможностях модели
 */
@Serializable
data class ModelCapabilitiesDTO(
    val modelId: String,
    val maxTokens: Int,
    val contextWindow: Int,
    val supportsVision: Boolean,
    val supportsStreaming: Boolean
)

/**
 * Запрос на обновление названия сессии
 */
@Serializable
data class UpdateTitleRequest(
    val title: String
)

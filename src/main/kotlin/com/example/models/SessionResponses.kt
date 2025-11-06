package com.example.models

import kotlinx.serialization.Serializable

/**
 * Краткая информация о сессии для списка
 */
@Serializable
data class SessionListItem(
    val id: String,
    val messageCount: Int,
    val createdAt: Long,
    val lastAccessedAt: Long
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
    val content: String
)

/**
 * Общий ответ для операций без тела
 */
@Serializable
data class StatusResponse(
    val success: Boolean,
    val message: String? = null
)

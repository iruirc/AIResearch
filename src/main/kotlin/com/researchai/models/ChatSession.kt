package com.researchai.models

import java.util.*

/**
 * Представляет сессию чата с историей сообщений.
 * Каждая сессия хранит полную историю диалога между пользователем и Claude.
 * Сессия может быть связана с агентом, который определяет ее поведение.
 */
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    private val _messages: MutableList<ClaudeMessage> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    var lastAccessedAt: Long = System.currentTimeMillis(),
    val agentId: String? = null // ID агента, если сессия связана с агентом
) {
    /**
     * Возвращает копию списка сообщений
     */
    val messages: List<ClaudeMessage>
        get() = _messages.toList()

    /**
     * Добавляет сообщение в историю сессии
     */
    fun addMessage(role: MessageRole, content: String) {
        _messages.add(ClaudeMessage(role = role, content = content))
        lastAccessedAt = System.currentTimeMillis()
    }

    /**
     * Очищает историю сообщений
     */
    fun clear() {
        _messages.clear()
        lastAccessedAt = System.currentTimeMillis()
    }
}

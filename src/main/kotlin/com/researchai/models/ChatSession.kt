package com.researchai.models

import com.researchai.domain.models.Message
import com.researchai.domain.models.MessageContent
import com.researchai.domain.models.MessageMetadata
import java.util.*

/**
 * Представляет сессию чата с историей сообщений.
 * Каждая сессия хранит полную историю диалога между пользователем и Claude.
 * Сессия может быть связана с агентом, который определяет ее поведение.
 */
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    private val _messages: MutableList<Message> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    var lastAccessedAt: Long = System.currentTimeMillis(),
    val agentId: String? = null // ID агента, если сессия связана с агентом
) {
    /**
     * Возвращает копию списка сообщений
     */
    val messages: List<Message>
        get() = _messages.toList()

    /**
     * Добавляет сообщение в историю сессии (legacy метод для обратной совместимости)
     */
    @Deprecated("Use addMessage(Message) instead", ReplaceWith("addMessage(Message(role, MessageContent.Text(content)))"))
    fun addMessage(role: MessageRole, content: String) {
        _messages.add(Message(
            role = com.researchai.domain.models.MessageRole.valueOf(role.name),
            content = MessageContent.Text(content)
        ))
        lastAccessedAt = System.currentTimeMillis()
    }

    /**
     * Добавляет сообщение в историю сессии (новый метод с domain Message)
     */
    fun addMessage(message: Message) {
        _messages.add(message)
        lastAccessedAt = System.currentTimeMillis()
    }

    /**
     * Обновляет последнее сообщение в истории (например, для добавления метаданных)
     */
    fun updateLastMessage(updater: (Message) -> Message): Boolean {
        if (_messages.isEmpty()) return false
        val lastIndex = _messages.lastIndex
        _messages[lastIndex] = updater(_messages[lastIndex])
        lastAccessedAt = System.currentTimeMillis()
        return true
    }

    /**
     * Очищает историю сообщений
     */
    fun clear() {
        _messages.clear()
        lastAccessedAt = System.currentTimeMillis()
    }
}

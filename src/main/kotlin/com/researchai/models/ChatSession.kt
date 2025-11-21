package com.researchai.models

import com.researchai.domain.models.CompressionConfig
import com.researchai.domain.models.Message
import com.researchai.domain.models.MessageContent
import com.researchai.domain.models.MessageMetadata
import java.util.*

/**
 * Представляет сессию чата с историей сообщений.
 * Каждая сессия хранит полную историю диалога между пользователем и AI.
 * Сессия может быть связана с ассистентом, задачей планировщика или пайплайном.
 * ВАЖНО: Сессия может иметь только ОДНО из: assistantId, scheduledTaskId, pipelineId (mutual exclusivity).
 */
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    var title: String? = null, // Название чата (генерируется из первого сообщения)
    private val _messages: MutableList<Message> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    var lastAccessedAt: Long = System.currentTimeMillis(),
    var assistantId: String? = null, // ID ассистента, если сессия связана с ассистентом
    val scheduledTaskId: String? = null, // ID задачи планировщика, если сессия связана с задачей
    val pipelineId: String? = null, // ID пайплайна, если сессия связана с пайплайном

    // Поля для сжатия диалогов
    private val _archivedMessages: MutableList<Message> = mutableListOf(), // Архив сжатых сообщений
    var compressionConfig: CompressionConfig = CompressionConfig(), // Настройки сжатия
    var compressionCount: Int = 0 // Количество выполненных сжатий
) {
    /**
     * Возвращает копию списка сообщений
     */
    val messages: List<Message>
        get() = _messages.toList()

    /**
     * Возвращает копию архивированных сообщений
     */
    val archivedMessages: List<Message>
        get() = _archivedMessages.toList()

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

    /**
     * Архивирует указанные сообщения
     */
    fun archiveMessages(messages: List<Message>) {
        _archivedMessages.addAll(messages)
    }

    /**
     * Заменяет текущие сообщения на новые (используется при сжатии)
     */
    fun replaceMessages(newMessages: List<Message>) {
        _messages.clear()
        _messages.addAll(newMessages)
        lastAccessedAt = System.currentTimeMillis()
    }

    /**
     * Получить общее количество токенов во всех сообщениях
     */
    fun getTotalTokens(): Int {
        return _messages.sumOf { message ->
            message.metadata?.totalTokens ?: message.metadata?.estimatedTotalTokens ?: 0
        }
    }
}

package com.example.services

import com.example.models.Agent

/**
 * Менеджер для управления AI агентами.
 * Хранит список доступных агентов и предоставляет методы для работы с ними.
 */
class AgentManager {
    private val agents = mutableMapOf<String, Agent>()

    init {
        // Инициализируем встроенных агентов
        registerDefaultAgents()
    }

    /**
     * Регистрирует агентов по умолчанию
     */
    private fun registerDefaultAgents() {
        // Ассистент Приветствия
        val greetingAgent = Agent(
            id = "greeting-assistant",
            name = "Ассистент Приветствия",
            systemPrompt = "Ты - ассистент приветствия. Ты приветствуешь пользователя и предлагаешь ему начать диалог.",
            description = "Приветствует пользователей и помогает начать диалог"
        )
        registerAgent(greetingAgent)
    }

    /**
     * Регистрирует нового агента
     */
    fun registerAgent(agent: Agent) {
        agents[agent.id] = agent
    }

    /**
     * Получает агента по ID
     */
    fun getAgent(agentId: String): Agent? {
        return agents[agentId]
    }

    /**
     * Получает список всех доступных агентов
     */
    fun getAllAgents(): List<Agent> {
        return agents.values.toList()
    }

    /**
     * Проверяет существование агента
     */
    fun hasAgent(agentId: String): Boolean {
        return agents.containsKey(agentId)
    }
}

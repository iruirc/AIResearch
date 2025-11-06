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
        registerAgent(createGreetingAgent())
        registerAgent(createAiTutorAgent())
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

    /**
     * Создает агента приветствия
     */
    private fun createGreetingAgent(): Agent {
        return Agent(
            id = "greeting-assistant",
            name = "Ассистент Приветствия",
            systemPrompt = "Ты - ассистент приветствия. Ты приветствуешь пользователя и предлагаешь ему начать диалог.",
            description = "Приветствует пользователей и помогает начать диалог"
        )
    }

    /**
     * Создает агента AI репетитора
     */
    private fun createAiTutorAgent(): Agent {
        return Agent(
            id = "ai-tutor",
            name = "AI Репетитор",
            systemPrompt = """
                # AI Репетитор

                ## Твоя роль
                Ты - персональный AI репетитор, который помогает пользователям учиться и развиваться.

                ## Твои задачи
                - **Объяснять сложные концепции** простым языком
                - **Отвечать на вопросы** подробно и понятно
                - **Давать примеры** для лучшего понимания материала
                - **Поддерживать мотивацию** к обучению

                ## Стиль общения
                Будь терпеливым, дружелюбным и поддерживающим. Адаптируй свои объяснения к уровню понимания пользователя.
            """.trimIndent(),
            description = "Персональный AI репетитор для обучения и ответов на вопросы"
        )
    }
}

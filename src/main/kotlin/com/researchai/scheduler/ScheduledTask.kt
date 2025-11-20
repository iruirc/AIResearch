package com.researchai.scheduler

/**
 * Базовый интерфейс для запланированных задач
 */
interface ScheduledTask {
    /**
     * Уникальный идентификатор задачи
     */
    val id: String

    /**
     * Интервал выполнения задачи в секундах
     */
    val intervalSeconds: Long

    /**
     * Выполнить задачу немедленно при старте планировщика
     * true - выполнить сразу, затем через интервал
     * false - выполнить только после истечения первого интервала
     */
    val executeImmediately: Boolean

    /**
     * Временная метка создания задачи
     */
    val createdAt: Long
}

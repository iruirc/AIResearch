package com.researchai.models

import kotlinx.serialization.Serializable

@Serializable
data class CreateScheduledTaskRequest(
    val title: String? = null,
    val taskRequest: String,
    val intervalSeconds: Long,
    val executeImmediately: Boolean,
    val providerId: String? = null,
    val model: String? = null
)

@Serializable
data class CreateScheduledTaskResponse(
    val taskId: String,
    val sessionId: String,
    val message: String
)

@Serializable
data class ScheduledTaskInfo(
    val id: String,
    val title: String?,
    val taskRequest: String,
    val intervalSeconds: Long,
    val executeImmediately: Boolean,
    val sessionId: String?,
    val createdAt: Long,
    val isRunning: Boolean,
    val secondsUntilNext: Long,
    val providerId: String?,
    val model: String?
)

@Serializable
data class ScheduledTasksListResponse(
    val tasks: List<ScheduledTaskInfo>
)

@Serializable
data class ScheduledTaskDetailResponse(
    val id: String,
    val title: String?,
    val taskRequest: String,
    val intervalSeconds: Long,
    val executeImmediately: Boolean,
    val sessionId: String?,
    val createdAt: Long,
    val isRunning: Boolean,
    val secondsUntilNext: Long,
    val providerId: String?,
    val model: String?
)

@Serializable
data class SchedulerOperationResponse(
    val success: Boolean,
    val message: String
)

package com.researchai.models

import kotlinx.serialization.Serializable

@Serializable
data class ChatResponse(
    val response: String,
    val sessionId: String
)

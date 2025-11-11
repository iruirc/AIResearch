package com.researchai.models

import kotlinx.serialization.Serializable

@Serializable
enum class ResponseFormat {
    PLAIN_TEXT,
    JSON,
    XML
}

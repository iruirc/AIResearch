package com.researchai.domain.models

import kotlinx.serialization.Serializable

@Serializable
enum class ChunkingStrategy {
    FIXED_SIZE,
    SEMANTIC,
    RECURSIVE
}

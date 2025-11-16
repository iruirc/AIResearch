package com.researchai.auth.domain.models

import kotlinx.serialization.Serializable

/**
 * OAuth провайдеры для аутентификации
 */
@Serializable
enum class OAuthProvider {
    GOOGLE,
    GITHUB,
    APPLE
}

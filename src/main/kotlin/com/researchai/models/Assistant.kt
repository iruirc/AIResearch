package com.researchai.models

import kotlinx.serialization.Serializable

/**
 * Представляет AI ассистента с определенным поведением.
 * Ассистент имеет имя и системный prompt, который определяет его поведение.
 *
 * @property isSystem Флаг системного ассистента (не может быть удален/изменен)
 */
@Serializable
data class Assistant(
    val id: String,
    val name: String,
    val systemPrompt: String,
    val description: String = "",
    val isSystem: Boolean = false
)

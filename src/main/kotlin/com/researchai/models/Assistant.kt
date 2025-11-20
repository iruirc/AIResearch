package com.researchai.models

/**
 * Представляет AI ассистента с определенным поведением.
 * Ассистент имеет имя и системный prompt, который определяет его поведение.
 */
data class Assistant(
    val id: String,
    val name: String,
    val systemPrompt: String,
    val description: String = ""
)

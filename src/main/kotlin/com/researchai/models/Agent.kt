package com.researchai.models

/**
 * Представляет AI агента с определенным поведением.
 * Агент имеет имя и системный prompt, который определяет его поведение.
 */
data class Agent(
    val id: String,
    val name: String,
    val systemPrompt: String,
    val description: String = ""
)

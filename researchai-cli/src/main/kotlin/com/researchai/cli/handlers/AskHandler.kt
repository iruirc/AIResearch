package com.researchai.cli.handlers

import com.researchai.cli.api.ChatResponse
import com.researchai.cli.api.ResearchAiClient
import com.researchai.cli.config.ProjectConfig

/**
 * Handler for RAG-based question answering.
 * Used by both top-level AskCommand and /ask slash command in chat.
 */
class AskHandler(
    private val client: ResearchAiClient,
    private val projectConfig: ProjectConfig?,
    private val output: (String) -> Unit
) {
    /**
     * Ask a question using RAG knowledge base.
     *
     * @param question The question to ask
     * @param model Model to use (optional)
     * @param sessionId Session ID for context (optional)
     * @return ChatResponse if successful, null otherwise
     */
    suspend fun ask(
        question: String,
        model: String? = null,
        sessionId: String? = null
    ): ChatResponse? {
        if (question.isBlank()) {
            output("Please provide a question.")
            return null
        }

        if (projectConfig == null) {
            output("Project not initialized. Run 'rai init' or '/init' first.")
            return null
        }

        // Search in RAG
        output("Searching knowledge base...")
        val searchResults = try {
            client.searchRag(
                query = question,
                documentIds = listOf(projectConfig.ragDocumentId),
                topK = 5
            )
        } catch (e: Exception) {
            output("Error searching RAG: ${e.message}")
            emptyList()
        }

        if (searchResults.isEmpty()) {
            output("No relevant context found in knowledge base.")
            output("Asking without context...\n")
        }

        // Build context
        val context = if (searchResults.isNotEmpty()) {
            searchResults.joinToString("\n\n") { result ->
                "---\n${result.text}\n---"
            }
        } else null

        // Send question with context
        val messageWithContext = if (context != null) {
            """
            |Based on the following context from project documentation:
            |
            |$context
            |
            |Please answer: $question
            """.trimMargin()
        } else {
            question
        }

        output("Asking AI...\n")

        return try {
            client.sendMessage(messageWithContext, sessionId, model)
        } catch (e: Exception) {
            output("Error: ${e.message}")
            null
        }
    }
}

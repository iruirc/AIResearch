package com.researchai.cli.handlers

import com.researchai.cli.api.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Handler for tech support queries.
 * Processes queries using RAG and Trello integration.
 */
class SupportHandler(
    private val client: ResearchAiClient,
    private val output: (String, Boolean) -> Unit
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Handle a tech support query.
     *
     * @param query The user's question or issue
     * @param sessionId Session ID for conversation continuity
     * @param customerId Customer ID for context
     * @param boardId Trello board ID override
     * @param includeRag Whether to search RAG knowledge base
     * @param includeTrello Whether to search Trello tickets
     * @param outputFormat Output format: text or json
     * @param model AI model to use
     */
    suspend fun handle(
        query: String,
        sessionId: String? = null,
        customerId: String? = null,
        boardId: String? = null,
        includeRag: Boolean = true,
        includeTrello: Boolean = true,
        outputFormat: String = "text",
        model: String? = null
    ) {
        if (query.isBlank()) {
            output("Please provide a question or issue description.", true)
            return
        }

        output("\nProcessing your request...\n", true)

        val request = TechSupportRequest(
            query = query,
            sessionId = sessionId,
            customerId = customerId,
            trelloBoardId = boardId,
            includeRag = includeRag,
            includeTrello = includeTrello,
            model = model
        )

        val response = try {
            client.techSupport(request)
        } catch (e: Exception) {
            output("Error: ${e.message}", true)
            return
        }

        when (outputFormat.lowercase()) {
            "json" -> outputJson(response)
            else -> outputText(response)
        }
    }

    private fun outputJson(response: TechSupportResponse) {
        output(json.encodeToString(response), true)
    }

    private fun outputText(response: TechSupportResponse) {
        val divider = "=" .repeat(60)
        val subDivider = "-" .repeat(60)

        output(divider, true)
        output("TECH SUPPORT RESPONSE", true)
        output(divider, true)
        output("", true)

        // Query type indicator
        val queryTypeIcon = when (response.queryType) {
            "BUG_REPORT" -> "[BUG]"
            "HOW_TO" -> "[HOW-TO]"
            "STATUS_CHECK" -> "[STATUS]"
            "FEATURE_REQUEST" -> "[FEATURE]"
            else -> "[INFO]"
        }
        output("Query Type: $queryTypeIcon ${response.queryType}", true)
        output("", true)

        // Main answer
        output("Answer:", true)
        output(subDivider, true)
        output(response.answer, true)
        output("", true)

        // Sources used
        if (response.sourcesUsed.ragSourceCount > 0 || response.sourcesUsed.trelloTicketCount > 0) {
            output(subDivider, true)
            output("Sources Used:", true)

            if (response.sourcesUsed.ragSources.isNotEmpty()) {
                output("  Documentation (${response.sourcesUsed.ragSourceCount}):", true)
                response.sourcesUsed.ragSources.forEach { source ->
                    output("    - $source", true)
                }
            }

            if (response.sourcesUsed.trelloSources.isNotEmpty()) {
                output("  Tickets (${response.sourcesUsed.trelloTicketCount}):", true)
                response.sourcesUsed.trelloSources.forEach { source ->
                    output("    - $source", true)
                }
            }
            output("", true)
        }

        // Related tickets
        if (response.relatedTickets.isNotEmpty()) {
            output(subDivider, true)
            output("Related Tickets:", true)
            response.relatedTickets.forEach { ticket ->
                val labels = if (ticket.labels.isNotEmpty()) {
                    " [${ticket.labels.joinToString(", ")}]"
                } else ""
                output("  - ${ticket.cardName}$labels", true)
                output("    Status: ${ticket.listName}", true)
                ticket.url?.let { output("    URL: $it", true) }
            }
            output("", true)
        }

        // Suggested actions
        if (response.suggestedActions.isNotEmpty()) {
            output(subDivider, true)
            output("Suggested Actions:", true)
            response.suggestedActions.forEachIndexed { index, action ->
                when (action.actionType) {
                    "CREATE_TICKET" -> {
                        action.createTicket?.let { create ->
                            output("  ${index + 1}. Create Ticket:", true)
                            output("     Title: ${create.title}", true)
                            output("     Description: ${create.description}", true)
                            if (create.suggestedLabels.isNotEmpty()) {
                                output("     Labels: ${create.suggestedLabels.joinToString(", ")}", true)
                            }
                        }
                    }
                    "VIEW_TICKET" -> {
                        action.viewTicket?.let { view ->
                            output("  ${index + 1}. View Related Ticket:", true)
                            output("     Ticket: ${view.cardName}", true)
                            output("     Reason: ${view.reason}", true)
                        }
                    }
                }
            }
            output("", true)
        }

        // Metadata
        output(subDivider, true)
        output("Session: ${response.sessionId}", true)
        output("Processing time: ${response.processingTimeMs}ms", true)
        output(divider, true)
    }
}

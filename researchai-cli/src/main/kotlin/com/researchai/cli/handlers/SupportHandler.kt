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
            "PROJECT_MANAGEMENT" -> "[PROJECT]"
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
                            output("  ${index + 1}. View Ticket: ${view.cardName}", true)
                            output("     Reason: ${view.reason}", true)
                            view.url?.let { output("     URL: $it", true) }
                        }
                    }
                    "LIST_TASKS" -> {
                        action.listTasks?.let { list ->
                            output("  ${index + 1}. Tasks (${list.filter}): ${list.totalCount} total", true)
                            list.tasks.forEachIndexed { idx, task ->
                                val priorityIcon = when (task.priority?.lowercase()) {
                                    "high" -> "[!]"
                                    "medium" -> "[~]"
                                    "low" -> "[-]"
                                    else -> "[ ]"
                                }
                                output("     $priorityIcon ${idx + 1}. ${task.cardName}", true)
                                output("        Status: ${task.listName}", true)
                                task.url?.let { output("        URL: $it", true) }
                            }
                        }
                    }
                    "PRIORITIZE" -> {
                        action.prioritize?.let { prioritize ->
                            output("  ${index + 1}. AI Recommendations:", true)
                            prioritize.recommendedOrder.forEach { rec ->
                                output("     #${rec.order}. ${rec.cardName}", true)
                                output("        Reason: ${rec.reason}", true)
                                rec.estimatedEffort?.let { output("        Effort: $it", true) }
                            }
                        }
                    }
                    "PROJECT_STATUS" -> {
                        action.projectStatus?.let { status ->
                            output("  ${index + 1}. Project Status:", true)
                            output("     Total Tasks: ${status.totalTasks}", true)
                            if (status.byPriority.isNotEmpty()) {
                                output("     By Priority:", true)
                                status.byPriority.forEach { (priority, count) ->
                                    output("       - $priority: $count", true)
                                }
                            }
                            if (status.byStatus.isNotEmpty()) {
                                output("     By Status:", true)
                                status.byStatus.forEach { (statusName, count) ->
                                    output("       - $statusName: $count", true)
                                }
                            }
                        }
                    }
                    "ESCALATE" -> {
                        action.escalate?.let { escalate ->
                            output("  ${index + 1}. Escalate to Support:", true)
                            output("     Reason: ${escalate.reason}", true)
                            output("     Priority: ${escalate.priority}", true)
                        }
                    }
                    "ADD_TO_FAQ" -> {
                        action.addToFaq?.let { faq ->
                            output("  ${index + 1}. Add to FAQ:", true)
                            output("     Question: ${faq.question.take(50)}...", true)
                        }
                    }
                    "CONTACT_SUPPORT" -> {
                        action.contactSupport?.let { contact ->
                            output("  ${index + 1}. Contact Support:", true)
                            output("     Channel: ${contact.suggestedChannel}", true)
                            output("     Reason: ${contact.reason}", true)
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

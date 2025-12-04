package com.researchai.routes

import com.researchai.domain.models.techsupport.*
import com.researchai.domain.models.workflow.*
import com.researchai.services.ChatSessionManager
import com.researchai.services.TechSupportService
import com.researchai.services.TaskWorkflowService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Tech Support API routes
 */
fun Route.techSupportRoutes(
    techSupportService: TechSupportService,
    chatSessionManager: ChatSessionManager,
    taskWorkflowService: TaskWorkflowService
) {
    route("/api/v2/tech-support") {

        /**
         * POST /api/v2/tech-support/sessions
         * Create a new Tech Support session
         *
         * Response: { sessionId: string }
         */
        post("/sessions") {
            try {
                val sessionId = chatSessionManager.createSession(isTechSupport = true)
                call.respond(mapOf("sessionId" to sessionId))
            } catch (e: Exception) {
                call.application.environment.log.error("Failed to create tech support session", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to create session: ${e.message}")
                )
            }
        }

        /**
         * PUT /api/v2/tech-support/sessions/{sessionId}
         * Mark existing session as Tech Support
         */
        put("/sessions/{sessionId}") {
            try {
                val sessionId = call.parameters["sessionId"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Session ID required"))

                val updated = chatSessionManager.updateSessionTechSupport(sessionId, true)
                if (updated) {
                    call.respond(mapOf("success" to true, "sessionId" to sessionId))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
                }
            } catch (e: Exception) {
                call.application.environment.log.error("Failed to update session", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to update session: ${e.message}")
                )
            }
        }

        /**
         * POST /api/v2/tech-support
         * Process a tech support query
         *
         * Request body: TechSupportRequest
         * Response: TechSupportResponse
         */
        post {
            try {
                val request = call.receive<TechSupportRequest>()

                if (request.query.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Query cannot be empty")
                    )
                    return@post
                }

                val result = techSupportService.processRequest(request)

                result.fold(
                    onSuccess = { response ->
                        call.respond(response)
                    },
                    onFailure = { error ->
                        call.application.environment.log.error("Tech support request failed", error)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to "Failed to process request: ${error.message}")
                        )
                    }
                )
            } catch (e: ContentTransformationException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid request format: ${e.message}")
                )
            } catch (e: Exception) {
                call.application.environment.log.error("Tech support request failed", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Request processing failed: ${e.message}")
                )
            }
        }

        /**
         * POST /api/v2/tech-support/tickets
         * Create a support ticket in Trello
         *
         * Request body: CreateTicketRequest
         * Response: CreateTicketResponse
         */
        post("/tickets") {
            try {
                val request = call.receive<CreateTicketRequest>()

                if (request.title.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        CreateTicketResponse(
                            success = false,
                            cardId = null,
                            cardUrl = null,
                            error = "Title is required"
                        )
                    )
                    return@post
                }

                val result = techSupportService.createTicket(request)

                result.fold(
                    onSuccess = { response ->
                        call.respond(response)
                    },
                    onFailure = { error ->
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            CreateTicketResponse(
                                success = false,
                                cardId = null,
                                cardUrl = null,
                                error = error.message
                            )
                        )
                    }
                )
            } catch (e: ContentTransformationException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    CreateTicketResponse(
                        success = false,
                        cardId = null,
                        cardUrl = null,
                        error = "Invalid request format: ${e.message}"
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    CreateTicketResponse(
                        success = false,
                        cardId = null,
                        cardUrl = null,
                        error = e.message
                    )
                )
            }
        }

        /**
         * POST /api/v2/tech-support/faq
         * Add FAQ entry to RAG knowledge base
         *
         * Request body: AddFaqRequest
         * Response: AddFaqResponse
         */
        post("/faq") {
            try {
                val request = call.receive<AddFaqRequest>()

                if (request.question.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        AddFaqResponse(
                            success = false,
                            error = "Question cannot be empty"
                        )
                    )
                    return@post
                }

                if (request.answer.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        AddFaqResponse(
                            success = false,
                            error = "Answer cannot be empty"
                        )
                    )
                    return@post
                }

                val result = techSupportService.addFaq(request)

                result.fold(
                    onSuccess = { response ->
                        call.respond(HttpStatusCode.Created, response)
                    },
                    onFailure = { error ->
                        call.application.environment.log.error("Failed to add FAQ", error)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            AddFaqResponse(
                                success = false,
                                error = error.message ?: "Failed to add FAQ"
                            )
                        )
                    }
                )
            } catch (e: ContentTransformationException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    AddFaqResponse(
                        success = false,
                        error = "Invalid request format: ${e.message}"
                    )
                )
            } catch (e: Exception) {
                call.application.environment.log.error("FAQ request failed", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AddFaqResponse(
                        success = false,
                        error = e.message ?: "Request processing failed"
                    )
                )
            }
        }

        /**
         * GET /api/v2/tech-support/health
         * Check service health status
         *
         * Response: Health status with RAG and Trello connection info
         */
        get("/health") {
            try {
                val trelloConnected = try {
                    techSupportService.isTrelloConnected()
                } catch (e: Exception) {
                    call.application.environment.log.warn("Failed to check Trello connection", e)
                    false
                }

                val githubConnected = try {
                    techSupportService.isGithubConnected()
                } catch (e: Exception) {
                    call.application.environment.log.warn("Failed to check GitHub connection", e)
                    false
                }

                call.respond(TechSupportHealthResponse(
                    status = "ok",
                    ragEnabled = true,
                    trelloConnected = trelloConnected,
                    githubConnected = githubConnected
                ))
            } catch (e: Exception) {
                call.application.environment.log.error("Health check failed", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    TechSupportHealthResponse(
                        status = "error",
                        ragEnabled = false,
                        trelloConnected = false,
                        githubConnected = false,
                        error = e.message ?: "Unknown error"
                    )
                )
            }
        }

        /**
         * POST /api/v2/tech-support/workflow
         * Execute task workflow (start/complete task)
         *
         * Request body: TaskWorkflowRequest
         * Response: TaskWorkflowResult
         */
        post("/workflow") {
            try {
                val request = call.receive<TaskWorkflowRequest>()

                if (request.query.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        TaskWorkflowResult(
                            success = false,
                            taskId = "unknown",
                            action = TaskAction.START,
                            message = "Query cannot be empty",
                            errors = listOf("Query cannot be empty")
                        )
                    )
                    return@post
                }

                val result = taskWorkflowService.processWorkflow(request)
                call.respond(if (result.success) HttpStatusCode.OK else HttpStatusCode.BadRequest, result)
            } catch (e: ContentTransformationException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    TaskWorkflowResult(
                        success = false,
                        taskId = "unknown",
                        action = TaskAction.START,
                        message = "Invalid request format: ${e.message}",
                        errors = listOf("Invalid request format")
                    )
                )
            } catch (e: Exception) {
                call.application.environment.log.error("Workflow request failed", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    TaskWorkflowResult(
                        success = false,
                        taskId = "unknown",
                        action = TaskAction.START,
                        message = "Request processing failed: ${e.message}",
                        errors = listOf(e.message ?: "Unknown error")
                    )
                )
            }
        }
    }
}

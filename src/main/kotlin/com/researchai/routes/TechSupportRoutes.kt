package com.researchai.routes

import com.researchai.domain.models.techsupport.*
import com.researchai.services.TechSupportService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Tech Support API routes
 */
fun Route.techSupportRoutes(techSupportService: TechSupportService) {
    route("/api/v2/tech-support") {

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
         * GET /api/v2/tech-support/health
         * Check service health status
         *
         * Response: Health status with RAG and Trello connection info
         */
        get("/health") {
            call.respond(mapOf(
                "status" to "ok",
                "service" to "tech-support",
                "ragEnabled" to true,
                "trelloConnected" to techSupportService.isTrelloConnected()
            ))
        }
    }
}

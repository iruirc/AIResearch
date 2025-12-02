package com.researchai.routes

import com.researchai.domain.models.pr.PRReviewRequest
import com.researchai.services.PRReviewService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Routes for PR review functionality
 */
fun Route.prReviewRoutes(prReviewService: PRReviewService) {
    route("/pr-review") {

        /**
         * POST /pr-review
         * Execute PR review
         */
        post {
            try {
                val request = call.receive<PRReviewRequest>()

                // Validate request
                if (request.repositoryOwner.isBlank() || request.repositoryName.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Repository owner and name are required")
                    )
                    return@post
                }

                if (request.pullRequestNumber <= 0) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Valid pull request number is required")
                    )
                    return@post
                }

                // Execute review
                val result = prReviewService.reviewPullRequest(request)

                result.onSuccess { review ->
                    call.respond(HttpStatusCode.OK, review)
                }.onFailure { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (error.message ?: "Review failed"))
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }

        /**
         * GET /pr-review/health
         * Health check endpoint
         */
        get("/health") {
            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "status" to "ok",
                    "service" to "pr-review"
                )
            )
        }
    }
}

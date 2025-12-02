package com.researchai.routes

import com.researchai.domain.models.pr.PRReviewRequest
import com.researchai.domain.models.pr.PRReviewResult
import com.researchai.services.PRReviewService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

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
         * POST /pr-review/comment
         * Post a review result as a comment on the GitHub PR
         */
        post("/comment") {
            try {
                @Serializable
                data class PostCommentRequest(
                    val reviewResult: PRReviewResult,
                    val githubToken: String? = null
                )

                val request = call.receive<PostCommentRequest>()

                // Post comment
                val result = prReviewService.postReviewAsComment(
                    result = request.reviewResult,
                    githubToken = request.githubToken
                )

                result.onSuccess {
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf(
                            "message" to "Review comment posted successfully",
                            "prUrl" to request.reviewResult.pullRequestUrl
                        )
                    )
                }.onFailure { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (error.message ?: "Failed to post comment"))
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

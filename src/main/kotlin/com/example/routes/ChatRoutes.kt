package com.example.routes

import com.example.models.ChatRequest
import com.example.models.ChatResponse
import com.example.services.ClaudeService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.chatRoutes(claudeService: ClaudeService) {
    route("/chat") {
        post {
            try {
                val request = call.receive<ChatRequest>()

                if (request.message.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Message cannot be empty"))
                    return@post
                }

                val claudeResponse = claudeService.sendMessage(request.message)
                call.respond(ChatResponse(response = claudeResponse))

            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to process request: ${e.message}")
                )
            }
        }
    }

    get("/health") {
        call.respond(mapOf("status" to "ok"))
    }
}

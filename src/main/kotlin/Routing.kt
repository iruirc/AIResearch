package com.example

import com.example.routes.chatRoutes
import com.example.services.ClaudeService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(claudeService: ClaudeService) {
    routing {
        get("/") {
            call.respondText("Claude Chat API Server is running!")
        }

        chatRoutes(claudeService)
    }
}

package com.researchai

import com.researchai.config.ClaudeConfig
import com.researchai.di.AppModule
import com.researchai.routes.chatRoutes
import com.researchai.routes.compressionRoutes
import com.researchai.routes.providerRoutes
import com.researchai.services.ClaudeService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(
    claudeService: ClaudeService,
    claudeConfig: ClaudeConfig,
    appModule: AppModule
) {
    routing {
        // Главная страница - перенаправление на index.html
        get("/") {
            call.respondRedirect("/index.html")
        }

        // API роуты для чата (legacy + новая архитектура)
        chatRoutes(claudeService, appModule.chatSessionManager, appModule.agentManager, claudeConfig, appModule)

        // Новые API роуты для работы с провайдерами
        providerRoutes(appModule)

        // API роуты для сжатия диалогов
        compressionRoutes(appModule.chatSessionManager, appModule.compressionService)

        // Статические файлы (HTML, CSS, JS)
        staticResources("/", "static")
    }
}

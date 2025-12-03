package com.researchai

import com.researchai.auth.routes.authRoutes
import com.researchai.config.ClaudeConfig
import com.researchai.di.AppModule
import com.researchai.routes.assistantRoutes
import com.researchai.routes.chatRoutes
import com.researchai.routes.compressionRoutes
import com.researchai.routes.mcpRoutes
import com.researchai.routes.ollamaRoutes
import com.researchai.routes.pipelineRoutes
import com.researchai.routes.prReviewRoutes
import com.researchai.routes.providerRoutes
import com.researchai.routes.techSupportRoutes
import com.researchai.routes.ragRoutes
import com.researchai.routes.ragTestRoutes
import com.researchai.routes.schedulerRoutes
import com.researchai.routes.preferencesRoutes
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

        // Публичный endpoint для проверки статуса аутентификации
        get("/auth/status") {
            call.respond(mapOf("enabled" to (appModule.authService != null)))
        }

        // Authentication routes (только если authService и googleAuthProvider не null)
        if (appModule.googleAuthProvider != null && appModule.authService != null) {
            authRoutes(appModule.googleAuthProvider!!, appModule.authService!!)
        }

        // API роуты для чата (legacy + новая архитектура)
        chatRoutes(claudeService, appModule.chatSessionManager, appModule.assistantManager, claudeConfig, appModule)

        // Новые API роуты для работы с провайдерами
        providerRoutes(appModule)

        // API роуты для Ollama подключений
        ollamaRoutes(appModule.ollamaConnectionManager)

        // API роуты для сжатия диалогов
        compressionRoutes(appModule.chatSessionManager, appModule.compressionService)

        // API роуты для MCP серверов
        mcpRoutes(appModule.mcpServerManager)

        // API роуты для планировщика задач
        schedulerRoutes(appModule.schedulerManager)

        // API роуты для assistant pipelines
        pipelineRoutes(appModule.assistantPipelineUseCase, appModule.pipelineStorage)

        // API роуты для ассистентов
        assistantRoutes(appModule.assistantManager)

        // API роуты для пользовательских настроек
        preferencesRoutes(appModule.preferencesManager)

        // API роуты для RAG (Retrieval-Augmented Generation)
        ragRoutes(appModule.ragManager, appModule.ragPreferencesStorage)

        // API роуты для RAG тестов
        ragTestRoutes(appModule.ragTestStorage, appModule.ragTestExecutionService)

        // API роуты для PR review
        prReviewRoutes(appModule.prReviewService)

        // API роуты для техподдержки
        techSupportRoutes(appModule.techSupportService, appModule.chatSessionManager)

        // Статические файлы (HTML, CSS, JS)
        staticResources("/", "static") {
            default("index.html")
        }
    }
}

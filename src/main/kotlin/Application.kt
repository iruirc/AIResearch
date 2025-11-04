package com.example

import com.example.config.getClaudeConfig
import com.example.services.ClaudeService
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Инициализация ClaudeService
    val claudeConfig = getClaudeConfig()
    val claudeService = ClaudeService(claudeConfig)

    // Закрытие клиента при остановке приложения
    monitor.subscribe(ApplicationStopped) {
        claudeService.close()
    }

    // Установка плагинов
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        })
    }

    install(CORS) {
        anyHost()
        allowHeader("Content-Type")
    }

    // Конфигурация роутинга
    configureRouting(claudeService)
}

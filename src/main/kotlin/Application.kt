package com.example

import com.example.config.DotenvLoader
import com.example.config.getClaudeConfig
import com.example.services.ClaudeService
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

fun main(args: Array<String>) {
    // Загружаем переменные окружения из .env файла (если существует)
    DotenvLoader.load()

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
    install(CallLogging) {
        level = Level.INFO
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val uri = call.request.uri
            "$httpMethod $uri - $status"
        }
    }

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

package com.example.services

import com.example.config.ClaudeConfig
import com.example.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class ClaudeService(private val config: ClaudeConfig) {

    private val logger = LoggerFactory.getLogger(ClaudeService::class.java)
    private val formatter = ClaudeMessageFormatter()

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 300_000 // 300 секунд (5 минут)
            connectTimeoutMillis = 10_000  // 10 секунд на подключение
            socketTimeoutMillis = 300_000  // 300 секунд на чтение/запись
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }
    }

    suspend fun sendMessage(
        userMessage: String,
        format: ResponseFormat = ResponseFormat.PLAIN_TEXT,
        messageHistory: List<ClaudeMessage> = emptyList(),
        systemPrompt: String? = null,
        model: String? = null
    ): String {
        return try {
            logger.info("Sending message to Claude API: $userMessage")
            logger.info("Response format: $format")
            logger.info("Message history size: ${messageHistory.size}")

            val enhancedMessage = formatter.enhanceMessage(userMessage, format)

            // Строим список сообщений: история + новое сообщение пользователя
            val messages = messageHistory.toMutableList().apply {
                add(ClaudeMessage(
                    role = MessageRole.USER,
                    content = enhancedMessage
                ))
            }
            logger.info("Claude Request's messages: $messages")

            // Используем модель из параметра или модель по умолчанию из конфигурации
            val selectedModel = model ?: config.model

            val claudeRequest = ClaudeRequest(
                model = selectedModel,
                maxTokens = config.maxTokens,
                messages = messages,
                temperature = config.temperature,
                system = systemPrompt
            )

            logger.info("Claude Request: model=$selectedModel, maxTokens=${config.maxTokens}")

            val httpResponse: HttpResponse = client.post(config.apiUrl) {
                header("x-api-key", config.apiKey)
                header("anthropic-version", config.apiVersion)
                contentType(ContentType.Application.Json)
                setBody(claudeRequest)
            }

            logger.info("Claude API response status: ${httpResponse.status}")

            if (!httpResponse.status.isSuccess()) {
                val errorBody = httpResponse.bodyAsText()
                logger.error("Claude API error response: $errorBody")

                return try {
                    val errorResponse: ClaudeError = Json.decodeFromString(errorBody)
                    "Claude API Error: ${errorResponse.error.message}"
                } catch (e: Exception) {
                    logger.error("Failed to parse error response: ${e.message}")
                    "Claude API Error (${httpResponse.status}): $errorBody"
                }
            }

            val response: ClaudeResponse = httpResponse.body()
            var responseText = response.content.firstOrNull()?.text ?: "No response from Claude"
            logger.info("response = $responseText")

            responseText = formatter.processResponseByFormat(responseText, format)

            logger.info("Successfully received response from Claude API")
            logger.info("fix response = $responseText")
            responseText

        } catch (e: Exception) {
            logger.error("Exception in ClaudeService: ${e.message}", e)
            "Error: ${e.message}"
        }
    }

    fun close() {
        client.close()
    }
}

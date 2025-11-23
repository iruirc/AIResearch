package com.researchai.routes

import com.researchai.models.ResponseFormat
import com.researchai.models.UserPreferences
import com.researchai.services.PreferencesManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * API routes for user preferences management
 */
fun Route.preferencesRoutes(preferencesManager: PreferencesManager) {
    val logger = LoggerFactory.getLogger("PreferencesRoutes")

    route("/preferences") {
        /**
         * GET /preferences
         * Get current user preferences
         */
        get {
            try {
                val preferences = preferencesManager.getPreferences()
                call.respond(
                    PreferencesResponse(
                        providerId = preferences.providerId,
                        model = preferences.model,
                        temperature = preferences.temperature,
                        maxTokens = preferences.maxTokens,
                        format = preferences.format.name,
                        updatedAt = preferences.updatedAt
                    )
                )
            } catch (e: Exception) {
                logger.error("Failed to get preferences", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Failed to get preferences"))
                )
            }
        }

        /**
         * POST /preferences
         * Save user preferences
         */
        post {
            try {
                val request = call.receive<SavePreferencesRequest>()

                // Validate format
                val format = try {
                    ResponseFormat.valueOf(request.format)
                } catch (e: Exception) {
                    logger.warn("Invalid format: ${request.format}, using PLAIN_TEXT")
                    ResponseFormat.PLAIN_TEXT
                }

                val preferences = UserPreferences(
                    providerId = request.providerId,
                    model = request.model,
                    temperature = request.temperature,
                    maxTokens = request.maxTokens,
                    format = format
                )

                preferencesManager.savePreferences(preferences).fold(
                    onSuccess = {
                        call.respond(
                            HttpStatusCode.OK,
                            SavePreferencesResponse(
                                message = "Preferences saved successfully",
                                preferences = PreferencesResponse(
                                    providerId = preferences.providerId,
                                    model = preferences.model,
                                    temperature = preferences.temperature,
                                    maxTokens = preferences.maxTokens,
                                    format = preferences.format.name,
                                    updatedAt = preferences.updatedAt
                                )
                            )
                        )
                    },
                    onFailure = { error ->
                        logger.error("Failed to save preferences", error)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to (error.message ?: "Failed to save preferences"))
                        )
                    }
                )
            } catch (e: Exception) {
                logger.error("Exception in save preferences endpoint", e)
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Invalid request"))
                )
            }
        }

        /**
         * DELETE /preferences
         * Reset preferences to defaults
         */
        delete {
            try {
                preferencesManager.resetToDefaults().fold(
                    onSuccess = {
                        val defaults = preferencesManager.getDefaults()
                        call.respond(
                            HttpStatusCode.OK,
                            ResetPreferencesResponse(
                                message = "Preferences reset to defaults",
                                preferences = PreferencesResponse(
                                    providerId = defaults.providerId,
                                    model = defaults.model,
                                    temperature = defaults.temperature,
                                    maxTokens = defaults.maxTokens,
                                    format = defaults.format.name,
                                    updatedAt = defaults.updatedAt
                                )
                            )
                        )
                    },
                    onFailure = { error ->
                        logger.error("Failed to reset preferences", error)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to (error.message ?: "Failed to reset preferences"))
                        )
                    }
                )
            } catch (e: Exception) {
                logger.error("Exception in reset preferences endpoint", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Failed to reset preferences"))
                )
            }
        }

        /**
         * GET /preferences/defaults
         * Get default preferences without loading from storage
         */
        get("/defaults") {
            try {
                val defaults = preferencesManager.getDefaults()
                call.respond(
                    PreferencesResponse(
                        providerId = defaults.providerId,
                        model = defaults.model,
                        temperature = defaults.temperature,
                        maxTokens = defaults.maxTokens,
                        format = defaults.format.name,
                        updatedAt = defaults.updatedAt
                    )
                )
            } catch (e: Exception) {
                logger.error("Failed to get default preferences", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Failed to get default preferences"))
                )
            }
        }
    }
}

// Request/Response models

@Serializable
data class SavePreferencesRequest(
    val providerId: String,
    val model: String,
    val temperature: Double,
    val maxTokens: Int,
    val format: String
)

@Serializable
data class PreferencesResponse(
    val providerId: String,
    val model: String,
    val temperature: Double,
    val maxTokens: Int,
    val format: String,
    val updatedAt: Long
)

@Serializable
data class SavePreferencesResponse(
    val message: String,
    val preferences: PreferencesResponse
)

@Serializable
data class ResetPreferencesResponse(
    val message: String,
    val preferences: PreferencesResponse
)

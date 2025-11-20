package com.researchai.routes

import com.researchai.domain.models.*
import com.researchai.domain.usecase.AssistantPipelineUseCase
import com.researchai.persistence.AssistantPipelineStorage
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

/**
 * REST API endpoints для Assistant Pipeline
 */
fun Route.pipelineRoutes(
    pipelineUseCase: AssistantPipelineUseCase,
    pipelineStorage: AssistantPipelineStorage
) {
    val logger = LoggerFactory.getLogger("PipelineRoutes")

    route("/api/v2/pipeline") {

        // Execute pipeline
        post("/execute") {
            try {
                val request = call.receive<ExecutePipelineRequest>()
                logger.info("Execute pipeline: ${request.pipelineId ?: "ad-hoc"}")

                val result = pipelineUseCase(request).getOrElse { error ->
                    logger.error("Pipeline execution failed", error)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (error.message ?: "Pipeline execution failed"))
                    )
                    return@post
                }

                call.respond(HttpStatusCode.OK, result)
            } catch (e: Exception) {
                logger.error("Failed to execute pipeline", e)
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Invalid request"))
                )
            }
        }

        // Execute saved pipeline by ID
        post("/execute/{id}") {
            try {
                val pipelineId = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Pipeline ID required"))
                    return@post
                }

                @kotlinx.serialization.Serializable
                data class ExecuteSavedPipelineRequest(
                    val initialMessage: String,
                    val model: String? = null,
                    val parameters: RequestParameters? = null
                )

                val body = call.receive<ExecuteSavedPipelineRequest>()

                val request = ExecutePipelineRequest(
                    initialMessage = body.initialMessage,
                    pipelineId = pipelineId,
                    model = body.model,
                    parameters = body.parameters ?: RequestParameters()
                )

                val result = pipelineUseCase(request).getOrElse { error ->
                    logger.error("Pipeline execution failed", error)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (error.message ?: "Pipeline execution failed"))
                    )
                    return@post
                }

                call.respond(HttpStatusCode.OK, result)
            } catch (e: Exception) {
                logger.error("Failed to execute pipeline", e)
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Invalid request"))
                )
            }
        }

        // Create or update pipeline configuration
        post("/config") {
            try {
                @kotlinx.serialization.Serializable
                data class CreatePipelineRequest(
                    val id: String? = null,
                    val name: String,
                    val description: String = "",
                    val assistantIds: List<String>,
                    val providerId: ProviderType = ProviderType.CLAUDE,
                    val model: String? = null,
                    val defaultParameters: RequestParameters = RequestParameters()
                )

                val request = call.receive<CreatePipelineRequest>()

                val pipeline = AssistantPipeline(
                    id = request.id ?: java.util.UUID.randomUUID().toString(),
                    name = request.name,
                    description = request.description,
                    assistantIds = request.assistantIds,
                    providerId = request.providerId,
                    model = request.model,
                    defaultParameters = request.defaultParameters
                )

                pipelineStorage.savePipeline(pipeline).getOrElse { error ->
                    logger.error("Failed to save pipeline", error)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Failed to save pipeline")
                    )
                    return@post
                }

                logger.info("Saved pipeline: ${pipeline.id} - ${pipeline.name}")
                call.respond(HttpStatusCode.OK, pipeline)
            } catch (e: Exception) {
                logger.error("Failed to create pipeline", e)
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Invalid request"))
                )
            }
        }

        // Get all pipeline configurations
        get("/configs") {
            try {
                val pipelines = pipelineStorage.loadAllPipelines().getOrElse { error ->
                    logger.error("Failed to load pipelines", error)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Failed to load pipelines")
                    )
                    return@get
                }

                val items = pipelines.map { pipeline ->
                    PipelineListItem(
                        id = pipeline.id,
                        name = pipeline.name,
                        description = pipeline.description,
                        assistantCount = pipeline.assistantIds.size,
                        providerId = pipeline.providerId,
                        createdAt = pipeline.createdAt,
                        updatedAt = pipeline.updatedAt
                    )
                }

                call.respond(HttpStatusCode.OK, mapOf("pipelines" to items))
            } catch (e: Exception) {
                logger.error("Failed to get pipelines", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to load pipelines")
                )
            }
        }

        // Get specific pipeline configuration
        get("/config/{id}") {
            try {
                val pipelineId = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Pipeline ID required"))
                    return@get
                }

                val pipeline = pipelineStorage.loadPipeline(pipelineId).getOrElse { error ->
                    logger.error("Failed to load pipeline", error)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Failed to load pipeline")
                    )
                    return@get
                }

                if (pipeline == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Pipeline not found"))
                    return@get
                }

                call.respond(HttpStatusCode.OK, pipeline)
            } catch (e: Exception) {
                logger.error("Failed to get pipeline", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to load pipeline")
                )
            }
        }

        // Delete pipeline configuration
        delete("/config/{id}") {
            try {
                val pipelineId = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Pipeline ID required"))
                    return@delete
                }

                pipelineStorage.deletePipeline(pipelineId).getOrElse { error ->
                    logger.error("Failed to delete pipeline", error)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Failed to delete pipeline")
                    )
                    return@delete
                }

                logger.info("Deleted pipeline: $pipelineId")
                call.respond(HttpStatusCode.OK, mapOf("message" to "Pipeline deleted"))
            } catch (e: Exception) {
                logger.error("Failed to delete pipeline", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to delete pipeline")
                )
            }
        }

        // Get execution history
        get("/executions") {
            try {
                val pipelineId = call.request.queryParameters["pipelineId"]
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50

                val executions = if (pipelineId != null) {
                    pipelineStorage.loadExecutionsByPipeline(pipelineId, limit)
                } else {
                    pipelineStorage.loadRecentExecutions(limit)
                }.getOrElse { error ->
                    logger.error("Failed to load executions", error)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Failed to load executions")
                    )
                    return@get
                }

                call.respond(HttpStatusCode.OK, mapOf("executions" to executions))
            } catch (e: Exception) {
                logger.error("Failed to get executions", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to load executions")
                )
            }
        }

        // Get specific execution details
        get("/execution/{id}") {
            try {
                val executionId = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Execution ID required"))
                    return@get
                }

                val execution = pipelineStorage.loadExecution(executionId).getOrElse { error ->
                    logger.error("Failed to load execution", error)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Failed to load execution")
                    )
                    return@get
                }

                if (execution == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Execution not found"))
                    return@get
                }

                call.respond(HttpStatusCode.OK, execution)
            } catch (e: Exception) {
                logger.error("Failed to get execution", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to load execution")
                )
            }
        }
    }
}

package com.researchai.routes

import com.researchai.domain.models.RAGTest
import com.researchai.domain.models.RAGTestQuery
import com.researchai.domain.models.TestExecutionEvent
import com.researchai.persistence.RAGTestStorage
import com.researchai.services.RAGTestExecutionService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class AddTestRequest(
    val name: String,
    val queries: List<RAGTestQuery>,
    val evaluationMetrics: Map<String, String>? = null
)

@Serializable
data class UpdateTestRequest(
    val name: String? = null,
    val queries: List<RAGTestQuery>? = null,
    val evaluationMetrics: Map<String, String>? = null
)

@Serializable
data class TestErrorResponse(
    val error: String,
    val details: ValidationDetails? = null
)

@Serializable
data class ValidationDetails(
    val invalidQueries: List<InvalidQueryInfo>? = null
)

@Serializable
data class InvalidQueryInfo(
    val index: Int,
    val missingFields: List<String>
)

/**
 * Exception thrown when a test with the same name already exists.
 */
class DuplicateTestNameException(name: String) : Exception("Test with name '$name' already exists")

/**
 * Validate that all required fields are present in queries.
 * Required fields: id, query, explanation
 */
private fun validateQueries(queries: List<RAGTestQuery>): List<InvalidQueryInfo> {
    val invalidQueries = mutableListOf<InvalidQueryInfo>()

    queries.forEachIndexed { index, query ->
        val missingFields = mutableListOf<String>()

        if (query.id.isBlank()) missingFields.add("id")
        if (query.query.isBlank()) missingFields.add("query")
        if (query.explanation.isBlank()) missingFields.add("explanation")

        if (missingFields.isNotEmpty()) {
            invalidQueries.add(InvalidQueryInfo(index, missingFields))
        }
    }

    return invalidQueries
}

/**
 * Generate a unique test ID based on current timestamp.
 * Format: "yyyy-MM-dd_HH:mm:ss"
 * If ID already exists, appends "_1", "_2", etc.
 */
private suspend fun generateUniqueTestId(storage: RAGTestStorage): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss")
    val baseId = LocalDateTime.now().format(formatter)

    // Check if base ID is available
    if (!storage.exists(baseId)) {
        return baseId
    }

    // Find unique suffix
    var suffix = 1
    while (storage.exists("${baseId}_$suffix")) {
        suffix++
    }
    return "${baseId}_$suffix"
}

// JSON serializer for SSE events
private val sseJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

// Track active execution IDs for cancellation
private val activeExecutionIds = ConcurrentHashMap<String, Job>()

/**
 * Routes for RAG test management.
 */
fun Route.ragTestRoutes(
    storage: RAGTestStorage,
    executionService: RAGTestExecutionService? = null
) {
    route("/rag/tests") {
        // POST /rag/tests - Add new test
        post {
            try {
                val request = call.receive<AddTestRequest>()

                if (request.name.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, TestErrorResponse("Test name cannot be empty"))
                    return@post
                }

                if (request.queries.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, TestErrorResponse("Test must contain at least one query"))
                    return@post
                }

                // Validate required fields in queries
                val invalidQueries = validateQueries(request.queries)
                if (invalidQueries.isNotEmpty()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        TestErrorResponse(
                            "Some queries have missing required fields (id, query, explanation)",
                            ValidationDetails(invalidQueries)
                        )
                    )
                    return@post
                }

                // Check for duplicate name
                if (storage.existsByName(request.name)) {
                    call.respond(HttpStatusCode.Conflict, TestErrorResponse("Test with name '${request.name}' already exists"))
                    return@post
                }

                val now = Clock.System.now()
                val testId = generateUniqueTestId(storage)
                val test = RAGTest(
                    id = testId,
                    name = request.name,
                    queries = request.queries,
                    evaluationMetrics = request.evaluationMetrics,
                    createdAt = now,
                    updatedAt = now
                )

                storage.save(test)
                call.respond(HttpStatusCode.Created, test)
            } catch (e: DuplicateTestNameException) {
                call.respond(HttpStatusCode.Conflict, TestErrorResponse(e.message ?: "Test with this name already exists"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, TestErrorResponse(e.message ?: "Unknown error"))
            }
        }

        // GET /rag/tests - Get all tests
        get {
            try {
                val tests = storage.loadAll()
                call.respond(HttpStatusCode.OK, tests)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, TestErrorResponse(e.message ?: "Unknown error"))
            }
        }

        // GET /rag/tests/{id} - Get test by ID
        get("/{id}") {
            try {
                val testId = call.parameters["id"]
                if (testId == null) {
                    call.respond(HttpStatusCode.BadRequest, TestErrorResponse("Test ID is required"))
                    return@get
                }

                val test = storage.load(testId)
                if (test == null) {
                    call.respond(HttpStatusCode.NotFound, TestErrorResponse("Test not found"))
                } else {
                    call.respond(HttpStatusCode.OK, test)
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, TestErrorResponse(e.message ?: "Unknown error"))
            }
        }

        // PUT /rag/tests/{id} - Update test
        put("/{id}") {
            try {
                val testId = call.parameters["id"]
                if (testId == null) {
                    call.respond(HttpStatusCode.BadRequest, TestErrorResponse("Test ID is required"))
                    return@put
                }

                val existingTest = storage.load(testId)
                if (existingTest == null) {
                    call.respond(HttpStatusCode.NotFound, TestErrorResponse("Test not found"))
                    return@put
                }

                val request = call.receive<UpdateTestRequest>()

                // Check for duplicate name (if name is being changed)
                if (request.name != null && request.name != existingTest.name) {
                    if (storage.existsByName(request.name)) {
                        call.respond(HttpStatusCode.Conflict, TestErrorResponse("Test with name '${request.name}' already exists"))
                        return@put
                    }
                }

                // Validate queries if provided
                if (request.queries != null) {
                    if (request.queries.isEmpty()) {
                        call.respond(HttpStatusCode.BadRequest, TestErrorResponse("Test must contain at least one query"))
                        return@put
                    }

                    val invalidQueries = validateQueries(request.queries)
                    if (invalidQueries.isNotEmpty()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            TestErrorResponse(
                                "Some queries have missing required fields (id, query, explanation)",
                                ValidationDetails(invalidQueries)
                            )
                        )
                        return@put
                    }
                }

                val updatedTest = existingTest.copy(
                    name = request.name ?: existingTest.name,
                    queries = request.queries ?: existingTest.queries,
                    evaluationMetrics = request.evaluationMetrics ?: existingTest.evaluationMetrics,
                    updatedAt = Clock.System.now()
                )

                storage.save(updatedTest)
                call.respond(HttpStatusCode.OK, updatedTest)
            } catch (e: DuplicateTestNameException) {
                call.respond(HttpStatusCode.Conflict, TestErrorResponse(e.message ?: "Test with this name already exists"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, TestErrorResponse(e.message ?: "Unknown error"))
            }
        }

        // DELETE /rag/tests/{id} - Delete test
        delete("/{id}") {
            try {
                val testId = call.parameters["id"]
                if (testId == null) {
                    call.respond(HttpStatusCode.BadRequest, TestErrorResponse("Test ID is required"))
                    return@delete
                }

                val exists = storage.exists(testId)
                if (!exists) {
                    call.respond(HttpStatusCode.NotFound, TestErrorResponse("Test not found"))
                    return@delete
                }

                storage.delete(testId)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, TestErrorResponse(e.message ?: "Unknown error"))
            }
        }

        // GET /rag/tests/{id}/execute - Execute test with SSE streaming
        get("/{id}/execute") {
            if (executionService == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, TestErrorResponse("Execution service not available"))
                return@get
            }

            val testId = call.parameters["id"]
            if (testId == null) {
                call.respond(HttpStatusCode.BadRequest, TestErrorResponse("Test ID is required"))
                return@get
            }

            // Check if test exists
            val test = storage.load(testId)
            if (test == null) {
                call.respond(HttpStatusCode.NotFound, TestErrorResponse("Test not found"))
                return@get
            }

            // Generate execution ID for tracking
            val executionId = UUID.randomUUID().toString()

            // Set SSE headers
            call.response.headers.append(HttpHeaders.ContentType, "text/event-stream")
            call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
            call.response.headers.append(HttpHeaders.Connection, "keep-alive")
            call.response.headers.append("X-Execution-Id", executionId)

            // Stream SSE events
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                val job = Job()
                activeExecutionIds[executionId] = job

                try {
                    withContext(job) {
                        executionService.executeTest(testId)
                            .onEach { event ->
                                val eventData = when (event) {
                                    is com.researchai.domain.models.ExecutionStartedEvent -> {
                                        // Add executionId to started event for cancellation support
                                        val eventWithExecutionId = buildJsonObject {
                                            put("type", event.type)
                                            put("testId", event.testId)
                                            put("testName", event.testName)
                                            put("sessionId", event.sessionId)
                                            put("totalQueries", event.totalQueries)
                                            put("executionId", executionId)
                                        }
                                        eventWithExecutionId.toString()
                                    }
                                    is com.researchai.domain.models.QueryProcessingEvent ->
                                        sseJson.encodeToString(event)
                                    is com.researchai.domain.models.QueryCompletedEvent ->
                                        sseJson.encodeToString(event)
                                    is com.researchai.domain.models.QueryErrorEvent ->
                                        sseJson.encodeToString(event)
                                    is com.researchai.domain.models.ExecutionFinishedEvent ->
                                        sseJson.encodeToString(event)
                                    is com.researchai.domain.models.ExecutionCancelledEvent ->
                                        sseJson.encodeToString(event)
                                    else -> sseJson.encodeToString(event)
                                }

                                // SSE format: data: {json}\n\n
                                write("data: $eventData\n\n")
                                flush()
                            }
                            .catch { e ->
                                if (e is CancellationException) {
                                    // Send cancelled event
                                    val cancelEvent = buildJsonObject {
                                        put("type", "cancelled")
                                        put("message", "Execution was cancelled")
                                    }
                                    write("data: $cancelEvent\n\n")
                                    flush()
                                } else {
                                    // Send error event
                                    val errorEvent = buildJsonObject {
                                        put("type", "error")
                                        put("message", e.message ?: "Unknown error")
                                    }
                                    write("data: $errorEvent\n\n")
                                    flush()
                                }
                            }
                            .collect()
                    }
                } catch (e: CancellationException) {
                    // Expected on cancellation
                    val cancelEvent = buildJsonObject {
                        put("type", "cancelled")
                        put("message", "Execution was cancelled")
                    }
                    write("data: $cancelEvent\n\n")
                    flush()
                } finally {
                    activeExecutionIds.remove(executionId)
                }
            }
        }

        // POST /rag/tests/executions/{executionId}/cancel - Cancel test execution
        post("/executions/{executionId}/cancel") {
            val executionId = call.parameters["executionId"]
            if (executionId == null) {
                call.respond(HttpStatusCode.BadRequest, TestErrorResponse("Execution ID is required"))
                return@post
            }

            val job = activeExecutionIds[executionId]
            if (job == null) {
                call.respond(HttpStatusCode.NotFound, TestErrorResponse("Execution not found or already completed"))
                return@post
            }

            if (job.isActive) {
                job.cancel()
                val response = buildJsonObject {
                    put("status", "cancelling")
                    put("executionId", executionId)
                }
                call.respondText(response.toString(), ContentType.Application.Json)
            } else {
                val response = buildJsonObject {
                    put("status", "already_completed")
                    put("executionId", executionId)
                }
                call.respondText(response.toString(), ContentType.Application.Json)
            }
        }
    }
}

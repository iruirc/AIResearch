package com.researchai.routes

import com.researchai.domain.models.RAGTest
import com.researchai.domain.models.RAGTestQuery
import com.researchai.persistence.RAGTestStorage
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import java.util.UUID

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
 * Routes for RAG test management.
 */
fun Route.ragTestRoutes(storage: RAGTestStorage) {
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
                val test = RAGTest(
                    id = UUID.randomUUID().toString(),
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
    }
}

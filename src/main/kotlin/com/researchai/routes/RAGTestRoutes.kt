package com.researchai.routes

import com.researchai.domain.models.RAGTest
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
    val content: String
)

@Serializable
data class UpdateTestRequest(
    val name: String? = null,
    val content: String? = null
)

@Serializable
data class TestErrorResponse(
    val error: String
)

/**
 * Exception thrown when a test with the same name already exists.
 */
class DuplicateTestNameException(name: String) : Exception("Test with name '$name' already exists")

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

                if (request.content.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, TestErrorResponse("Test content cannot be empty"))
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
                    content = request.content,
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

                val updatedTest = existingTest.copy(
                    name = request.name ?: existingTest.name,
                    content = request.content ?: existingTest.content,
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

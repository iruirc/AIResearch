package com.researchai.routes

import com.researchai.domain.models.ChunkingStrategy
import com.researchai.services.DuplicateDocumentNameException
import com.researchai.services.RAGManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class AddDocumentRequest(
    val name: String,
    val content: String,
    val chunkingStrategy: ChunkingStrategy = ChunkingStrategy.FIXED_SIZE,
    val enabled: Boolean = true
)

@Serializable
data class UpdateDocumentRequest(
    val name: String? = null,
    val enabled: Boolean? = null,
    val chunkingStrategy: ChunkingStrategy? = null
)

@Serializable
data class SearchRequest(
    val query: String,
    val topK: Int = 5,
    val minScore: Float = 0.7f
)

@Serializable
data class ErrorResponse(
    val error: String
)

fun Route.ragRoutes(ragManager: RAGManager) {
    route("/rag") {
        // POST /rag/documents - Add new document
        post("/documents") {
            try {
                val request = call.receive<AddDocumentRequest>()

                if (request.name.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Document name cannot be empty"))
                    return@post
                }

                if (request.content.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Document content cannot be empty"))
                    return@post
                }

                val document = ragManager.addDocument(
                    name = request.name,
                    content = request.content,
                    chunkingStrategy = request.chunkingStrategy,
                    enabled = request.enabled
                )

                call.respond(HttpStatusCode.Created, document)
            } catch (e: DuplicateDocumentNameException) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse(e.message ?: "Document with this name already exists"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Unknown error"))
            }
        }

        // GET /rag/documents - Get all documents
        get("/documents") {
            try {
                val documents = ragManager.getAllDocuments()
                call.respond(HttpStatusCode.OK, documents)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Unknown error"))
            }
        }

        // GET /rag/documents/{id} - Get document by ID
        get("/documents/{id}") {
            try {
                val documentId = call.parameters["id"]
                if (documentId == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Document ID is required"))
                    return@get
                }

                val document = ragManager.getDocument(documentId)
                if (document == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Document not found"))
                } else {
                    call.respond(HttpStatusCode.OK, document)
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Unknown error"))
            }
        }

        // PUT /rag/documents/{id} - Update document
        put("/documents/{id}") {
            try {
                val documentId = call.parameters["id"]
                if (documentId == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Document ID is required"))
                    return@put
                }

                val request = call.receive<UpdateDocumentRequest>()

                val document = ragManager.updateDocument(
                    documentId = documentId,
                    name = request.name,
                    enabled = request.enabled,
                    chunkingStrategy = request.chunkingStrategy
                )

                if (document == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Document not found"))
                } else {
                    call.respond(HttpStatusCode.OK, document)
                }
            } catch (e: DuplicateDocumentNameException) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse(e.message ?: "Document with this name already exists"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Unknown error"))
            }
        }

        // DELETE /rag/documents/{id} - Delete document
        delete("/documents/{id}") {
            try {
                val documentId = call.parameters["id"]
                if (documentId == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Document ID is required"))
                    return@delete
                }

                val deleted = ragManager.deleteDocument(documentId)
                if (deleted) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Document not found"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Unknown error"))
            }
        }

        // POST /rag/search - Search relevant context
        post("/search") {
            try {
                val request = call.receive<SearchRequest>()

                if (request.query.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Query cannot be empty"))
                    return@post
                }

                val results = ragManager.searchRelevantContext(
                    query = request.query,
                    topK = request.topK,
                    minScore = request.minScore
                )

                call.respond(HttpStatusCode.OK, results)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Unknown error"))
            }
        }
    }
}

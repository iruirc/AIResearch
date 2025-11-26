package com.researchai.routes

import com.researchai.domain.models.ChunkingStrategy
import com.researchai.domain.models.RerankerConfig
import com.researchai.domain.models.RerankerStrategy
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

/**
 * Request for two-stage search with reranking
 */
@Serializable
data class SearchWithRerankingRequest(
    val query: String,
    val topK: Int = 5,
    val minScore: Float = 0.7f,
    val rerankerStrategy: RerankerStrategy = RerankerStrategy.SCORE_THRESHOLD,
    val secondaryThreshold: Float = 0.75f,
    val stdDevMultiplier: Float = 1.0f,
    val minResultsToKeep: Int = 1,
    val crossEncoderModel: String = "llama3.2:latest",
    val crossEncoderMinScore: Float = 6.0f
)

/**
 * Request for comparing search strategies
 */
@Serializable
data class CompareSearchRequest(
    val query: String,
    val topK: Int = 5,
    val minScore: Float = 0.7f,
    val rerankerStrategy: RerankerStrategy = RerankerStrategy.SCORE_THRESHOLD,
    val secondaryThreshold: Float = 0.75f,
    val stdDevMultiplier: Float = 1.0f,
    val crossEncoderModel: String = "llama3.2:latest",
    val crossEncoderMinScore: Float = 6.0f
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

        // POST /rag/search - Search relevant context (first stage only)
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

        // POST /rag/search/rerank - Two-stage search with reranking
        post("/search/rerank") {
            try {
                val request = call.receive<SearchWithRerankingRequest>()

                if (request.query.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Query cannot be empty"))
                    return@post
                }

                val rerankerConfig = RerankerConfig(
                    strategy = request.rerankerStrategy,
                    secondaryThreshold = request.secondaryThreshold,
                    stdDevMultiplier = request.stdDevMultiplier,
                    minResultsToKeep = request.minResultsToKeep,
                    crossEncoderModel = request.crossEncoderModel,
                    crossEncoderMinScore = request.crossEncoderMinScore
                )

                val result = ragManager.searchWithReranking(
                    query = request.query,
                    topK = request.topK,
                    minScore = request.minScore,
                    rerankerConfig = rerankerConfig
                )

                call.respond(HttpStatusCode.OK, result)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Unknown error"))
            }
        }

        // POST /rag/search/compare - Compare results with and without reranking
        post("/search/compare") {
            try {
                val request = call.receive<CompareSearchRequest>()

                if (request.query.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Query cannot be empty"))
                    return@post
                }

                val rerankerConfig = RerankerConfig(
                    strategy = request.rerankerStrategy,
                    secondaryThreshold = request.secondaryThreshold,
                    stdDevMultiplier = request.stdDevMultiplier,
                    crossEncoderModel = request.crossEncoderModel,
                    crossEncoderMinScore = request.crossEncoderMinScore
                )

                val comparison = ragManager.compareSearchStrategies(
                    query = request.query,
                    topK = request.topK,
                    minScore = request.minScore,
                    rerankerConfig = rerankerConfig
                )

                call.respond(HttpStatusCode.OK, comparison)
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse(e.message ?: "Reranker not configured"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Unknown error"))
            }
        }

        // GET /rag/reranker/strategies - Get available reranking strategies
        get("/reranker/strategies") {
            val strategies = RerankerStrategy.entries.map { strategy ->
                mapOf(
                    "name" to strategy.name,
                    "description" to when (strategy) {
                        RerankerStrategy.NONE -> "No reranking - use first-stage results as-is"
                        RerankerStrategy.SCORE_THRESHOLD -> "Score threshold filter - removes results below secondary threshold"
                        RerankerStrategy.STATISTICAL -> "Statistical filter - removes outliers based on score distribution"
                        RerankerStrategy.CROSS_ENCODER -> "Cross-encoder reranker - uses LLM to rerank results (slower but more accurate)"
                    }
                )
            }
            call.respond(HttpStatusCode.OK, strategies)
        }
    }
}

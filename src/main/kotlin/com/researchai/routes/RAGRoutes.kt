package com.researchai.routes

import com.researchai.domain.models.ChunkingStrategy
import com.researchai.domain.models.RerankerConfig
import com.researchai.domain.models.RerankerStrategy
import com.researchai.models.RAGSearchPreferences
import com.researchai.persistence.RAGPreferencesStorage
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
    val enabled: Boolean = true,
    val originalFileName: String? = null
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

@Serializable
data class AddDocumentBatchRequest(
    val documents: List<DocumentInput>,
    val chunkingStrategy: ChunkingStrategy = ChunkingStrategy.FIXED_SIZE,
    val enabled: Boolean = true
)

@Serializable
data class DocumentInput(
    val name: String,
    val content: String,
    val originalFileName: String? = null
)

@Serializable
data class AddDocumentBatchResponse(
    val created: List<com.researchai.domain.models.RAGDocument>,
    val errors: List<BatchError> = emptyList()
)

@Serializable
data class BatchError(
    val name: String,
    val error: String
)

fun Route.ragRoutes(ragManager: RAGManager, preferencesStorage: RAGPreferencesStorage) {
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
                    enabled = request.enabled,
                    originalFileName = request.originalFileName
                )

                call.respond(HttpStatusCode.Created, document)
            } catch (e: DuplicateDocumentNameException) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse(e.message ?: "Document with this name already exists"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Unknown error"))
            }
        }

        // POST /rag/documents/batch - Add multiple documents
        post("/documents/batch") {
            try {
                val request = call.receive<AddDocumentBatchRequest>()

                if (request.documents.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Documents list cannot be empty"))
                    return@post
                }

                val created = mutableListOf<com.researchai.domain.models.RAGDocument>()
                val errors = mutableListOf<BatchError>()

                for (input in request.documents) {
                    if (input.name.isBlank()) {
                        errors.add(BatchError(input.name.ifBlank { "(empty)" }, "Document name cannot be empty"))
                        continue
                    }
                    if (input.content.isBlank()) {
                        errors.add(BatchError(input.name, "Document content cannot be empty"))
                        continue
                    }

                    try {
                        val document = ragManager.addDocument(
                            name = input.name,
                            content = input.content,
                            chunkingStrategy = request.chunkingStrategy,
                            enabled = request.enabled,
                            originalFileName = input.originalFileName
                        )
                        created.add(document)
                    } catch (e: DuplicateDocumentNameException) {
                        errors.add(BatchError(input.name, "Document with this name already exists"))
                    } catch (e: Exception) {
                        errors.add(BatchError(input.name, e.message ?: "Unknown error"))
                    }
                }

                call.respond(HttpStatusCode.Created, AddDocumentBatchResponse(created, errors))
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

        // ============================================
        // RAG Search Preferences Endpoints
        // ============================================

        // GET /rag/preferences - Get current RAG search preferences
        get("/preferences") {
            try {
                val preferences = preferencesStorage.load()
                call.respond(HttpStatusCode.OK, preferences)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Failed to load preferences"))
            }
        }

        // POST /rag/preferences - Save RAG search preferences
        post("/preferences") {
            try {
                val preferences = call.receive<RAGSearchPreferences>()

                // Validate parameters
                if (preferences.scoreThreshold < 0f || preferences.scoreThreshold > 1f) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("scoreThreshold must be between 0.0 and 1.0"))
                    return@post
                }

                if (preferences.searchMinScore < 0f || preferences.searchMinScore > 1f) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("searchMinScore must be between 0.0 and 1.0"))
                    return@post
                }

                if (preferences.crossEncoderMinScore < 0f || preferences.crossEncoderMinScore > 10f) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("crossEncoderMinScore must be between 0.0 and 10.0"))
                    return@post
                }

                if (preferences.searchTopK < 1 || preferences.searchTopK > 100) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("searchTopK must be between 1 and 100"))
                    return@post
                }

                if (preferences.minResultsToKeep < 0) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("minResultsToKeep must be non-negative"))
                    return@post
                }

                // Save with updated timestamp
                val updatedPreferences = preferences.copy(updatedAt = System.currentTimeMillis())
                preferencesStorage.save(updatedPreferences)

                call.respond(HttpStatusCode.OK, updatedPreferences)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Failed to save preferences"))
            }
        }

        // DELETE /rag/preferences - Reset to default preferences
        delete("/preferences") {
            try {
                val defaults = preferencesStorage.reset()
                call.respond(HttpStatusCode.OK, defaults)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Failed to reset preferences"))
            }
        }

        // GET /rag/preferences/defaults - Get default preferences without modifying storage
        get("/preferences/defaults") {
            call.respond(HttpStatusCode.OK, RAGSearchPreferences.default())
        }

        // ============================================
        // RAG Context Preview Endpoints
        // ============================================

        // POST /rag/preview - Preview RAG context with comparison (uses saved preferences)
        post("/preview") {
            try {
                val request = call.receive<PreviewRequest>()
                println("🔍 RAG Preview: query='${request.query.take(50)}...'")

                if (request.query.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Query cannot be empty"))
                    return@post
                }

                // Debug: Check how many documents are in the index
                val allDocs = ragManager.getAllDocuments()
                val enabledDocs = allDocs.filter { it.enabled }
                println("🔍 RAG Preview: ${allDocs.size} total docs, ${enabledDocs.size} enabled")
                enabledDocs.forEach { doc ->
                    println("  - ${doc.name}: ${doc.chunks.size} chunks")
                }

                // Load preferences
                val prefs = preferencesStorage.load()
                println("🔍 RAG Preview: prefs topK=${prefs.searchTopK}, minScore=${prefs.searchMinScore}")

                // Build reranker config from preferences
                val rerankerConfig = RerankerConfig(
                    strategy = prefs.strategy,
                    secondaryThreshold = prefs.scoreThreshold,
                    stdDevMultiplier = prefs.stdDevMultiplier,
                    minResultsToKeep = prefs.minResultsToKeep,
                    crossEncoderModel = prefs.crossEncoderModel ?: "llama3.2:latest",
                    crossEncoderMinScore = prefs.crossEncoderMinScore
                )

                // Get results without reranking (first stage only)
                val withoutReranking = ragManager.searchRelevantContext(
                    query = request.query,
                    topK = prefs.searchTopK,
                    minScore = prefs.searchMinScore
                )
                println("🔍 RAG Preview: search returned ${withoutReranking.size} results")

                // Get results with reranking (if enabled)
                val withReranking = if (prefs.enableReranking) {
                    ragManager.searchWithReranking(
                        query = request.query,
                        topK = prefs.searchTopK,
                        minScore = prefs.searchMinScore,
                        rerankerConfig = rerankerConfig
                    )
                } else null

                // Build preview response
                val previewResponse = PreviewResponse(
                    query = request.query,
                    withoutReranking = withoutReranking,
                    withReranking = withReranking?.results,
                    filteredResults = withReranking?.let { result ->
                        result.originalResults.filter { original ->
                            result.results.none { it.documentId == original.documentId && it.chunkIndex == original.chunkIndex }
                        }
                    } ?: emptyList(),
                    rerankingEnabled = prefs.enableReranking,
                    rerankingStrategy = if (prefs.enableReranking) prefs.strategy.name else null,
                    statistics = withReranking?.statistics
                )

                call.respond(HttpStatusCode.OK, previewResponse)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Failed to preview context"))
            }
        }
    }
}

/**
 * Request for previewing RAG context
 */
@Serializable
data class PreviewRequest(
    val query: String
)

/**
 * Response with preview comparison
 */
@Serializable
data class PreviewResponse(
    val query: String,
    val withoutReranking: List<com.researchai.domain.models.SearchResult>,
    val withReranking: List<com.researchai.domain.models.SearchResult>?,
    val filteredResults: List<com.researchai.domain.models.SearchResult>,
    val rerankingEnabled: Boolean,
    val rerankingStrategy: String?,
    val statistics: com.researchai.domain.models.RerankerStatistics?
)

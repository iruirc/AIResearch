package com.researchai.cli.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class ResearchAiClient(private val baseUrl: String) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 300_000 // 5 minutes for long AI responses
            connectTimeoutMillis = 10_000
        }
    }

    suspend fun sendMessage(message: String, sessionId: String?, model: String? = null): ChatResponse {
        val response = client.post("$baseUrl/chat") {
            contentType(ContentType.Application.Json)
            setBody(ChatRequest(message = message, sessionId = sessionId, model = model))
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            val errorMessage = try {
                json.decodeFromString<ErrorResponse>(errorBody).error
            } catch (e: Exception) {
                errorBody
            }
            throw RuntimeException(errorMessage)
        }

        return response.body()
    }

    suspend fun clearSession(sessionId: String) {
        client.post("$baseUrl/sessions/$sessionId/clear")
    }

    suspend fun checkHealth(): Boolean {
        return try {
            client.get("$baseUrl/health")
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==================== PR Review API ====================

    /**
     * Review a pull request
     */
    suspend fun reviewPR(request: PRReviewRequest): PRReviewResult {
        val response = client.post("$baseUrl/pr-review") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            val errorMessage = try {
                json.decodeFromString<ErrorResponse>(errorBody).error
            } catch (e: Exception) {
                errorBody
            }
            throw RuntimeException("PR review failed: $errorMessage")
        }

        return response.body()
    }

    fun close() = client.close()

    // ==================== RAG API ====================

    /**
     * Create a RAG document with multiple source files
     */
    suspend fun createRagDocument(
        name: String,
        content: String,
        sourceFiles: List<Pair<String, String>>? = null
    ): RAGDocument {
        val request = AddDocumentRequest(
            name = name,
            content = content,
            sourceFiles = sourceFiles?.map { SourceFileInput(it.first, it.second) }
        )

        val response = client.post("$baseUrl/rag/documents") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw RuntimeException("Failed to create RAG document: $errorBody")
        }

        return response.body()
    }

    /**
     * Create a RAG document with SSE progress streaming
     */
    suspend fun createRagDocumentWithProgress(
        name: String,
        content: String,
        sourceFiles: List<Pair<String, String>>? = null,
        onProgress: suspend (ProgressData) -> Unit,
        onComplete: suspend (CompleteData) -> Unit,
        onError: suspend (String) -> Unit
    ) {
        val request = AddDocumentRequest(
            name = name,
            content = content,
            sourceFiles = sourceFiles?.map { SourceFileInput(it.first, it.second) }
        )

        client.preparePost("$baseUrl/rag/documents/stream") {
            contentType(ContentType.Application.Json)
            setBody(request)
            timeout {
                requestTimeoutMillis = 600_000 // 10 minutes for large documents
            }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                onError("Server error: ${response.status}")
                return@execute
            }

            val channel: ByteReadChannel = response.bodyAsChannel()
            val buffer = StringBuilder()

            while (!channel.isClosedForRead) {
                val line = try {
                    channel.readUTF8Line()
                } catch (e: Exception) {
                    null
                }

                if (line == null) break

                if (line.startsWith("event:")) {
                    buffer.clear()
                    buffer.append(line)
                } else if (line.startsWith("data:")) {
                    val eventLine = buffer.toString()
                    val eventType = eventLine.removePrefix("event:").trim()
                    val data = line.removePrefix("data:").trim()

                    when (eventType) {
                        "progress" -> {
                            try {
                                val progress = json.decodeFromString<ProgressData>(data)
                                onProgress(progress)
                            } catch (e: Exception) {
                                // Skip malformed progress events
                            }
                        }
                        "complete" -> {
                            try {
                                val complete = json.decodeFromString<CompleteData>(data)
                                onComplete(complete)
                            } catch (e: Exception) {
                                onError("Failed to parse complete event: ${e.message}")
                            }
                        }
                        "error" -> {
                            try {
                                val error = json.decodeFromString<ErrorData>(data)
                                onError(error.error)
                            } catch (e: Exception) {
                                onError(data)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Search RAG for relevant context
     */
    suspend fun searchRag(
        query: String,
        documentIds: List<String>? = null,
        topK: Int = 5,
        minScore: Float = 0.5f
    ): List<SearchResult> {
        val request = SearchRequest(
            query = query,
            topK = topK,
            minScore = minScore,
            documentIds = documentIds
        )

        val response = client.post("$baseUrl/rag/search") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw RuntimeException("Failed to search RAG: $errorBody")
        }

        // Server returns array directly, not wrapped in object
        return response.body<List<SearchResult>>()
    }

    /**
     * Delete a RAG document
     */
    suspend fun deleteRagDocument(documentId: String): Boolean {
        val response = client.delete("$baseUrl/rag/documents/$documentId")
        return response.status.isSuccess()
    }

    // ==================== MCP API ====================

    /**
     * Get list of MCP tools for a specific server
     */
    suspend fun getMcpTools(serverId: String): List<MCPTool> {
        val response = client.get("$baseUrl/mcp/tools") {
            parameter("serverId", serverId)
        }

        if (!response.status.isSuccess()) {
            throw RuntimeException("Failed to get MCP tools: ${response.bodyAsText()}")
        }

        return response.body<MCPToolsResponse>().tools
    }

    /**
     * Call an MCP tool
     */
    suspend fun callMcpTool(
        serverId: String,
        toolName: String,
        arguments: JsonElement
    ): MCPToolCallResult {
        val request = MCPToolCallRequest(
            serverId = serverId,
            toolName = toolName,
            arguments = arguments
        )

        val response = client.post("$baseUrl/mcp/tools/call") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (!response.status.isSuccess()) {
            throw RuntimeException("Failed to call MCP tool: ${response.bodyAsText()}")
        }

        return response.body()
    }
}

@Serializable
data class ChatRequest(
    val message: String,
    val sessionId: String? = null,
    val model: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null
)

@Serializable
data class ChatResponse(
    val response: String,
    val sessionId: String,
    val tokensUsed: Int? = null
)

@Serializable
data class ErrorResponse(
    val error: String
)

// ==================== RAG API Models ====================

@Serializable
data class SourceFileInput(
    val fileName: String,
    val content: String
)

@Serializable
data class AddDocumentRequest(
    val name: String,
    val content: String,
    val chunkingStrategy: String = "FIXED_SIZE",
    val enabled: Boolean = true,
    val sourceFiles: List<SourceFileInput>? = null
)

@Serializable
data class RAGDocument(
    val id: String,
    val name: String,
    val enabled: Boolean = true
)

@Serializable
data class SearchRequest(
    val query: String,
    val topK: Int = 5,
    val minScore: Float = 0.5f,
    val documentIds: List<String>? = null
)

@Serializable
data class SearchResult(
    val documentId: String,
    val documentName: String,
    val chunkIndex: Int = 0,
    val text: String,
    val score: Float
)

@Serializable
data class SearchResponse(
    val results: List<SearchResult>,
    val totalFound: Int = 0
)

// ==================== MCP API Models ====================

@Serializable
data class MCPTool(
    val name: String,
    val description: String,
    val inputSchema: JsonElement,
    val serverId: String
)

@Serializable
data class MCPToolsResponse(
    val tools: List<MCPTool>
)

@Serializable
data class MCPToolCallRequest(
    val toolName: String,
    val arguments: JsonElement,
    val serverId: String
)

@Serializable
data class MCPToolCallResult(
    val success: Boolean,
    val content: List<MCPContent>,
    val error: String? = null
)

@Serializable
data class MCPContent(
    val type: String,
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null,
    val uri: String? = null
)

// ==================== SSE Progress Models ====================

@Serializable
data class ProgressData(
    val phase: String,
    val current: Int? = null,
    val total: Int? = null,
    val percent: Int? = null,
    val message: String? = null
)

@Serializable
data class CompleteData(
    val documentId: String,
    val name: String,
    val chunksCount: Int
)

@Serializable
data class ErrorData(
    val error: String
)

// ==================== PR Review API Models ====================

@Serializable
data class PRReviewRequest(
    val repositoryOwner: String,
    val repositoryName: String,
    val pullRequestNumber: Int,
    val reviewMode: String = "STANDARD",
    val focusAreas: List<String> = emptyList(),
    val useRAG: Boolean = false,
    val ragMinScore: Float = 0.7f,
    val ragMaxChunks: Int = 10
)

@Serializable
data class PRReviewResult(
    val requestId: String,
    val pullRequestUrl: String,
    val summary: ReviewSummary,
    val fileReviews: List<FileReview>,
    val overallScore: Int,
    val metadata: ReviewMetadata
)

@Serializable
data class ReviewSummary(
    val overview: String,
    val criticalIssues: List<Issue>,
    val importantIssues: List<Issue>,
    val suggestions: List<Issue>,
    val positives: List<String>
)

@Serializable
data class FileReview(
    val filePath: String,
    val changeType: String,
    val lineComments: List<LineComment>,
    val fileSummary: String?
)

@Serializable
data class LineComment(
    val lineNumber: Int,
    val side: String,
    val severity: String,
    val category: String,
    val message: String,
    val suggestedFix: String?
)

@Serializable
data class Issue(
    val severity: String,
    val category: String,
    val title: String,
    val description: String,
    val affectedFiles: List<String>,
    val suggestedAction: String?
)

@Serializable
data class ReviewMetadata(
    val reviewDurationMs: Long,
    val filesReviewed: Int,
    val linesAnalyzed: Int,
    val ragContextUsed: Boolean = false,
    val ragChunksRetrieved: Int = 0,
    val tokensUsed: Int,
    val model: String,
    val provider: String
)

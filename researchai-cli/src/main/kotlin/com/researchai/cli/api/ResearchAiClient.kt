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

package com.researchai.services

import com.researchai.data.rag.FixedSizeTextChunker
import com.researchai.data.rag.RecursiveTextChunker
import com.researchai.data.rag.SemanticTextChunker
import com.researchai.domain.models.*
import com.researchai.domain.rag.EmbeddingService
import com.researchai.domain.rag.TextChunker
import com.researchai.domain.rag.VectorSearchService
import com.researchai.persistence.RAGDocumentStorage
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Exception thrown when attempting to create a document with a name that already exists
 */
class DuplicateDocumentNameException(name: String) : Exception("Document with name '$name' already exists")

class RAGManager(
    private val embeddingService: EmbeddingService,
    private val vectorSearch: VectorSearchService,
    private val storage: RAGDocumentStorage,
    private val config: RAGConfig,
    private val rerankerService: RerankerService? = null
) {
    private val logger = LoggerFactory.getLogger(RAGManager::class.java)
    suspend fun initialize() {
        val documents = storage.loadAll()
        documents.forEach { document ->
            vectorSearch.indexDocument(document)
        }
    }

    suspend fun addDocument(
        name: String,
        content: String,
        chunkingStrategy: ChunkingStrategy = ChunkingStrategy.FIXED_SIZE,
        enabled: Boolean = config.enabledByDefault
    ): RAGDocument {
        // Check if document with this name already exists
        if (storage.existsByName(name)) {
            throw DuplicateDocumentNameException(name)
        }

        val documentId = UUID.randomUUID().toString()

        val chunker = getChunker(chunkingStrategy)
        val textChunks = chunker.chunk(content)

        val embeddings = embeddingService.generateEmbeddings(textChunks)

        val chunks = textChunks.mapIndexed { index, text ->
            DocumentChunk(
                text = text,
                embedding = embeddings[index],
                chunkIndex = index,
                metadata = mapOf(
                    "chunkSize" to text.length.toString(),
                    "strategy" to chunkingStrategy.name
                )
            )
        }

        val now = Clock.System.now()
        val document = RAGDocument(
            id = documentId,
            name = name,
            content = content,
            chunks = chunks,
            chunkingStrategy = chunkingStrategy,
            enabled = enabled,
            createdAt = now,
            updatedAt = now
        )

        storage.save(document)
        vectorSearch.indexDocument(document)

        return document
    }

    suspend fun updateDocument(
        documentId: String,
        name: String? = null,
        enabled: Boolean? = null,
        chunkingStrategy: ChunkingStrategy? = null
    ): RAGDocument? {
        val existingDocument = storage.load(documentId) ?: return null

        // Check if new name conflicts with existing document (other than this one)
        if (name != null && name != existingDocument.name) {
            val existingByName = storage.findByName(name)
            if (existingByName != null && existingByName.id != documentId) {
                throw DuplicateDocumentNameException(name)
            }
        }

        val shouldReprocess = chunkingStrategy != null && chunkingStrategy != existingDocument.chunkingStrategy

        val updatedDocument = if (shouldReprocess) {
            val chunker = getChunker(chunkingStrategy!!)
            val textChunks = chunker.chunk(existingDocument.content)
            val embeddings = embeddingService.generateEmbeddings(textChunks)

            val chunks = textChunks.mapIndexed { index, text ->
                DocumentChunk(
                    text = text,
                    embedding = embeddings[index],
                    chunkIndex = index,
                    metadata = mapOf(
                        "chunkSize" to text.length.toString(),
                        "strategy" to chunkingStrategy.name
                    )
                )
            }

            existingDocument.copy(
                name = name ?: existingDocument.name,
                enabled = enabled ?: existingDocument.enabled,
                chunks = chunks,
                chunkingStrategy = chunkingStrategy,
                updatedAt = Clock.System.now()
            )
        } else {
            existingDocument.copy(
                name = name ?: existingDocument.name,
                enabled = enabled ?: existingDocument.enabled,
                updatedAt = Clock.System.now()
            )
        }

        storage.save(updatedDocument)
        vectorSearch.indexDocument(updatedDocument)

        return updatedDocument
    }

    suspend fun deleteDocument(documentId: String): Boolean {
        if (!storage.exists(documentId)) {
            return false
        }

        storage.delete(documentId)
        vectorSearch.removeDocument(documentId)

        return true
    }

    suspend fun getDocument(documentId: String): RAGDocument? {
        return storage.load(documentId)
    }

    suspend fun getAllDocuments(): List<RAGDocument> {
        return storage.loadAll()
    }

    /**
     * First stage: Vector search only (without reranking)
     */
    suspend fun searchRelevantContext(
        query: String,
        topK: Int = config.searchTopK,
        minScore: Float = config.searchMinScore
    ): List<SearchResult> {
        val queryEmbedding = embeddingService.generateEmbedding(query)
        return vectorSearch.search(queryEmbedding, topK, minScore)
    }

    /**
     * Two-stage search: Vector search + Reranking
     *
     * Stage 1: Vector search with cosine similarity (retrieval)
     * Stage 2: Reranking/filtering based on configured strategy
     */
    suspend fun searchWithReranking(
        query: String,
        topK: Int = config.searchTopK,
        minScore: Float = config.searchMinScore,
        rerankerConfig: RerankerConfig = config.rerankerConfig
    ): RerankerResult {
        logger.info("Two-stage search: query='${query.take(50)}...', topK=$topK, minScore=$minScore, strategy=${rerankerConfig.strategy}")

        // Stage 1: Vector search
        val firstStageResults = searchRelevantContext(query, topK, minScore)
        logger.debug("Stage 1 (vector search): ${firstStageResults.size} results")

        // Stage 2: Reranking
        if (rerankerService == null) {
            logger.warn("RerankerService not configured, returning first-stage results")
            return RerankerResult(
                results = firstStageResults,
                originalResults = firstStageResults,
                strategy = RerankerStrategy.NONE,
                statistics = RerankerStatistics(
                    inputCount = firstStageResults.size,
                    outputCount = firstStageResults.size,
                    filteredCount = 0,
                    avgScoreBefore = firstStageResults.map { it.score }.average().toFloat(),
                    avgScoreAfter = firstStageResults.map { it.score }.average().toFloat(),
                    processingTimeMs = 0
                )
            )
        }

        val rerankerResult = rerankerService.rerank(query, firstStageResults, rerankerConfig)
        logger.info("Stage 2 (${rerankerConfig.strategy}): ${firstStageResults.size} -> ${rerankerResult.results.size} results")

        return rerankerResult
    }

    /**
     * Compare search results with and without reranking
     */
    suspend fun compareSearchStrategies(
        query: String,
        topK: Int = config.searchTopK,
        minScore: Float = config.searchMinScore,
        rerankerConfig: RerankerConfig = config.rerankerConfig
    ): ComparisonResult {
        val firstStageResults = searchRelevantContext(query, topK, minScore)

        if (rerankerService == null) {
            throw IllegalStateException("RerankerService not configured")
        }

        return rerankerService.compareWithAndWithoutReranking(query, firstStageResults, rerankerConfig)
    }

    suspend fun getContextForChat(
        query: String,
        topK: Int = config.searchTopK,
        minScore: Float = config.searchMinScore,
        useReranking: Boolean = false,
        rerankerConfig: RerankerConfig = config.rerankerConfig
    ): String {
        val results = if (useReranking && rerankerService != null) {
            searchWithReranking(query, topK, minScore, rerankerConfig).results
        } else {
            searchRelevantContext(query, topK, minScore)
        }

        if (results.isEmpty()) {
            return ""
        }

        return buildString {
            appendLine("Relevant context from knowledge base:")
            appendLine()
            results.forEachIndexed { index, result ->
                appendLine("${index + 1}. From document '${result.documentName}' (relevance: ${String.format("%.2f", result.score)}):")
                appendLine(result.text)
                appendLine()
            }
        }
    }

    /**
     * Result of getContextForChat with debug information
     */
    data class ContextWithDebugInfo(
        val context: String,
        val debugInfo: RAGDebugInfo
    )

    /**
     * Get context for chat with optional debug information
     */
    suspend fun getContextForChatWithDebug(
        query: String,
        topK: Int = config.searchTopK,
        minScore: Float = config.searchMinScore,
        useReranking: Boolean = false,
        rerankerConfig: RerankerConfig = config.rerankerConfig
    ): ContextWithDebugInfo {
        val startTime = System.currentTimeMillis()

        val (usedResults, filteredResults) = if (useReranking && rerankerService != null) {
            val rerankerResult = searchWithReranking(query, topK, minScore, rerankerConfig)
            // Calculate filtered results (those in original but not in final)
            val filtered = rerankerResult.originalResults.filter { original ->
                rerankerResult.results.none { it.documentId == original.documentId && it.chunkIndex == original.chunkIndex }
            }
            Pair(rerankerResult.results, filtered)
        } else {
            Pair(searchRelevantContext(query, topK, minScore), emptyList())
        }

        val processingTime = System.currentTimeMillis() - startTime

        val context = if (usedResults.isEmpty()) {
            ""
        } else {
            buildString {
                appendLine("Relevant context from knowledge base:")
                appendLine()
                usedResults.forEachIndexed { index, result ->
                    appendLine("${index + 1}. From document '${result.documentName}' (relevance: ${String.format("%.2f", result.score)}):")
                    appendLine(result.text)
                    appendLine()
                }
            }
        }

        // Estimate tokens (rough: ~4 chars per token)
        val estimatedTokens = context.length / 4

        val debugInfo = RAGDebugInfo(
            query = query,
            usedResults = usedResults,
            filteredResults = filteredResults,
            rerankingEnabled = useReranking,
            rerankingStrategy = if (useReranking) rerankerConfig.strategy.name else null,
            processingTimeMs = processingTime,
            estimatedTokens = estimatedTokens
        )

        return ContextWithDebugInfo(context, debugInfo)
    }

    private fun getChunker(strategy: ChunkingStrategy): TextChunker {
        return when (strategy) {
            ChunkingStrategy.FIXED_SIZE -> FixedSizeTextChunker(
                chunkSize = config.defaultChunkSize,
                overlap = config.defaultOverlap
            )
            ChunkingStrategy.RECURSIVE -> RecursiveTextChunker(
                maxChunkSize = config.defaultChunkSize
            )
            ChunkingStrategy.SEMANTIC -> SemanticTextChunker(
                embeddingService = embeddingService
            )
        }
    }
}

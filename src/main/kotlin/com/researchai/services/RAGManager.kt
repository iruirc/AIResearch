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
import java.util.*

/**
 * Exception thrown when attempting to create a document with a name that already exists
 */
class DuplicateDocumentNameException(name: String) : Exception("Document with name '$name' already exists")

class RAGManager(
    private val embeddingService: EmbeddingService,
    private val vectorSearch: VectorSearchService,
    private val storage: RAGDocumentStorage,
    private val config: RAGConfig
) {
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

    suspend fun searchRelevantContext(
        query: String,
        topK: Int = config.searchTopK,
        minScore: Float = config.searchMinScore
    ): List<SearchResult> {
        val queryEmbedding = embeddingService.generateEmbedding(query)
        return vectorSearch.search(queryEmbedding, topK, minScore)
    }

    suspend fun getContextForChat(
        query: String,
        topK: Int = config.searchTopK,
        minScore: Float = config.searchMinScore
    ): String {
        val results = searchRelevantContext(query, topK, minScore)

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

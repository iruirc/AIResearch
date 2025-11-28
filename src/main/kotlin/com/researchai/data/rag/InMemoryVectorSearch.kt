package com.researchai.data.rag

import com.researchai.domain.models.RAGDocument
import com.researchai.domain.models.SearchResult
import com.researchai.domain.rag.VectorSearchService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.sqrt

class InMemoryVectorSearch : VectorSearchService {

    private val documents = mutableMapOf<String, RAGDocument>()
    private val mutex = Mutex()

    override suspend fun indexDocument(document: RAGDocument) {
        mutex.withLock {
            documents[document.id] = document
        }
    }

    override suspend fun removeDocument(documentId: String) {
        mutex.withLock {
            documents.remove(documentId)
        }
    }

    override suspend fun search(
        queryEmbedding: List<Float>,
        topK: Int,
        minScore: Float
    ): List<SearchResult> {
        return mutex.withLock {
            val results = mutableListOf<SearchResult>()

            documents.values
                .filter { it.enabled }
                .forEach { document ->
                    document.chunks.forEachIndexed { index, chunk ->
                        val similarity = cosineSimilarity(queryEmbedding, chunk.embedding)
                        if (similarity >= minScore) {
                            results.add(
                                SearchResult(
                                    documentId = document.id,
                                    documentName = document.name,
                                    chunkIndex = index,
                                    text = chunk.text,
                                    score = similarity,
                                    sourceFileName = extractFileName(document.sourceFilePath)
                                )
                            )
                        }
                    }
                }

            results.sortedByDescending { it.score }.take(topK)
        }
    }

    override suspend fun getAllDocuments(): List<RAGDocument> {
        return mutex.withLock {
            documents.values.toList()
        }
    }

    override suspend fun isEnabled(documentId: String): Boolean {
        return mutex.withLock {
            documents[documentId]?.enabled ?: false
        }
    }

    /**
     * Extract file name from source file path.
     * Removes document ID prefix if present (e.g., "{uuid}_{filename}" -> "{filename}")
     */
    private fun extractFileName(sourceFilePath: String?): String? {
        if (sourceFilePath == null) return null

        val fileName = sourceFilePath.substringAfterLast("/").substringAfterLast("\\")

        // Check if file name has UUID prefix (format: {uuid}_{originalName} or {uuid}.txt)
        val uuidPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}_")
        val match = uuidPattern.find(fileName)

        return if (match != null) {
            // Remove UUID prefix, return original file name
            fileName.substring(match.range.last + 1)
        } else if (fileName.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.txt$"))) {
            // File is {uuid}.txt (text input) - no original file name
            null
        } else {
            fileName
        }
    }

    private fun cosineSimilarity(vec1: List<Float>, vec2: List<Float>): Float {
        require(vec1.size == vec2.size) { "Vectors must have the same dimension" }

        var dotProduct = 0.0f
        var norm1 = 0.0f
        var norm2 = 0.0f

        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }

        val denominator = sqrt(norm1) * sqrt(norm2)
        return if (denominator > 0) dotProduct / denominator else 0.0f
    }
}

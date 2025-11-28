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
                            // Get source file from chunk metadata (set during document creation)
                            // Priority: 1) explicit sourceFile metadata, 2) use stored position,
                            // 3) detect from text, 4) fallback to document source file
                            val chunkSourceFile = chunk.metadata["sourceFile"]
                                ?: findSourceFileUsingStoredPosition(chunk, document)
                                ?: detectSourceFileFromText(chunk.text)
                                ?: extractFileName(document.sourceFilePath)

                            results.add(
                                SearchResult(
                                    documentId = document.id,
                                    documentName = document.name,
                                    chunkIndex = index,
                                    text = chunk.text,
                                    score = similarity,
                                    sourceFileName = chunkSourceFile,
                                    sourceFilePaths = document.sourceFilePaths
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
     * Find source file using stored chunk position from metadata.
     * More reliable than text search for documents with repeated content.
     */
    private fun findSourceFileUsingStoredPosition(
        chunk: com.researchai.domain.models.DocumentChunk,
        document: RAGDocument
    ): String? {
        val storedPosition = chunk.metadata["chunkStartPosition"]?.toIntOrNull() ?: return null

        // Find all source file markers in the document content
        val markerPattern = Regex("""===\s*([^=]+\.\w+)\s*===""")
        val markers = markerPattern.findAll(document.content)
            .map { Pair(it.range.first, it.groupValues[1].trim()) }
            .toList()

        if (markers.isEmpty()) return null

        // Find the last marker before the stored position
        var sourceFile: String? = null
        for ((markerPos, fileName) in markers) {
            if (markerPos <= storedPosition) {
                sourceFile = fileName
            } else {
                break
            }
        }

        return sourceFile
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

    /**
     * Try to detect source file name from chunk text.
     * Looks for pattern "=== filename.ext ===" in the text.
     * Fallback for existing documents without sourceFile metadata.
     */
    private fun detectSourceFileFromText(text: String): String? {
        val markerPattern = Regex("""===\s*([^=]+\.\w+)\s*===""")
        val match = markerPattern.find(text)
        return match?.groupValues?.get(1)?.trim()
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

package com.researchai.domain.rag

import com.researchai.domain.models.RAGDocument
import com.researchai.domain.models.SearchResult

interface VectorSearchService {
    suspend fun indexDocument(document: RAGDocument)
    suspend fun removeDocument(documentId: String)
    suspend fun search(queryEmbedding: List<Float>, topK: Int, minScore: Float): List<SearchResult>
    suspend fun getAllDocuments(): List<RAGDocument>
    suspend fun isEnabled(documentId: String): Boolean
}

package com.researchai.persistence

import com.researchai.domain.models.RAGDocument

interface RAGDocumentStorage {
    suspend fun save(document: RAGDocument)
    suspend fun load(documentId: String): RAGDocument?
    suspend fun loadAll(): List<RAGDocument>
    suspend fun delete(documentId: String)
    suspend fun exists(documentId: String): Boolean
    suspend fun existsByName(name: String): Boolean
    suspend fun findByName(name: String): RAGDocument?
}

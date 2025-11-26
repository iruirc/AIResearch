package com.researchai.persistence

import com.researchai.domain.models.RAGTest

/**
 * Interface for RAG test persistence operations.
 */
interface RAGTestStorage {
    suspend fun save(test: RAGTest)
    suspend fun load(testId: String): RAGTest?
    suspend fun loadAll(): List<RAGTest>
    suspend fun delete(testId: String)
    suspend fun exists(testId: String): Boolean
    suspend fun existsByName(name: String): Boolean
    suspend fun findByName(name: String): RAGTest?
}

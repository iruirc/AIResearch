package com.researchai.persistence.sql

import com.researchai.domain.models.RAGTest
import com.researchai.domain.models.RAGTestQuery
import com.researchai.persistence.RAGTestStorage
import com.researchai.persistence.sql.DatabaseFactory.dbQuery
import com.researchai.persistence.sql.tables.RAGTestsTable
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.slf4j.LoggerFactory

/**
 * PostgreSQL implementation of RAGTestStorage
 * Uses Exposed ORM with JSONB for queries storage
 */
class PostgresRAGTestStorage : RAGTestStorage {
    private val logger = LoggerFactory.getLogger(PostgresRAGTestStorage::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun save(test: RAGTest) {
        dbQuery {
            try {
                val queriesJson = test.queries.map { json.encodeToJsonElement(it) }
                val metricsJson = test.evaluationMetrics?.let { json.encodeToJsonElement(it) }

                RAGTestsTable.upsert {
                    it[id] = test.id
                    it[name] = test.name
                    it[queries] = queriesJson
                    it[evaluationMetrics] = metricsJson
                    it[createdAt] = test.createdAt
                    it[updatedAt] = test.updatedAt
                }
                logger.debug("Saved RAG test: ${test.id}")
            } catch (e: Exception) {
                logger.error("Failed to save RAG test: ${test.id}", e)
                throw e
            }
        }
    }

    override suspend fun load(testId: String): RAGTest? = dbQuery {
        try {
            val row = RAGTestsTable.selectAll()
                .where { RAGTestsTable.id eq testId }
                .singleOrNull()

            row?.let { mapRowToRAGTest(it) }
        } catch (e: Exception) {
            logger.error("Failed to load RAG test: $testId", e)
            throw e
        }
    }

    override suspend fun loadAll(): List<RAGTest> = dbQuery {
        try {
            RAGTestsTable.selectAll()
                .orderBy(RAGTestsTable.createdAt, SortOrder.DESC)
                .map { mapRowToRAGTest(it) }
        } catch (e: Exception) {
            logger.error("Failed to load all RAG tests", e)
            throw e
        }
    }

    override suspend fun delete(testId: String) {
        dbQuery {
            try {
                RAGTestsTable.deleteWhere { id eq testId }
                logger.info("Deleted RAG test: $testId")
            } catch (e: Exception) {
                logger.error("Failed to delete RAG test: $testId", e)
                throw e
            }
        }
    }

    override suspend fun exists(testId: String): Boolean = dbQuery {
        RAGTestsTable.selectAll()
            .where { RAGTestsTable.id eq testId }
            .count() > 0
    }

    override suspend fun existsByName(name: String): Boolean = dbQuery {
        RAGTestsTable.selectAll()
            .where { RAGTestsTable.name eq name }
            .count() > 0
    }

    override suspend fun findByName(name: String): RAGTest? = dbQuery {
        try {
            val row = RAGTestsTable.selectAll()
                .where { RAGTestsTable.name eq name }
                .singleOrNull()

            row?.let { mapRowToRAGTest(it) }
        } catch (e: Exception) {
            logger.error("Failed to find RAG test by name: $name", e)
            throw e
        }
    }

    private fun mapRowToRAGTest(row: ResultRow): RAGTest {
        val queriesJson = row[RAGTestsTable.queries]
        val queries = queriesJson.map { json.decodeFromJsonElement<RAGTestQuery>(it) }

        val metricsJson = row[RAGTestsTable.evaluationMetrics]
        val metrics = metricsJson?.let { json.decodeFromJsonElement<Map<String, String>>(it) }

        return RAGTest(
            id = row[RAGTestsTable.id],
            name = row[RAGTestsTable.name],
            queries = queries,
            evaluationMetrics = metrics,
            createdAt = row[RAGTestsTable.createdAt],
            updatedAt = row[RAGTestsTable.updatedAt]
        )
    }
}

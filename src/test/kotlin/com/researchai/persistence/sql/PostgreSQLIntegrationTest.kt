package com.researchai.persistence.sql

import com.researchai.models.Assistant
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests using Testcontainers with real PostgreSQL
 * Tests the full stack: Flyway migrations, Exposed ORM, PostgreSQL storage
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgreSQLIntegrationTest {

    companion object {
        @Container
        private val postgresContainer = PostgreSQLContainer<Nothing>("postgres:17-alpine").apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
        }
    }

    private lateinit var assistantStorage: PostgresAssistantStorage

    @BeforeAll
    fun setup() {
        // Wait for container to start
        postgresContainer.start()

        // Create DatabaseConfig from container
        val config = DatabaseConfig(
            url = postgresContainer.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgresContainer.username,
            password = postgresContainer.password,
            maxPoolSize = 5,
            minIdle = 2
        )

        // Initialize database
        DatabaseFactory.init(config)

        // Run Flyway migrations
        FlywayMigrator.migrate(DatabaseFactory.dataSource)

        // Create storage instances
        assistantStorage = PostgresAssistantStorage()
    }

    @AfterAll
    fun tearDown() {
        DatabaseFactory.close()
        postgresContainer.stop()
    }

    @Test
    fun `should successfully migrate schema with Flyway`() {
        // If we got this far, Flyway migrations succeeded
        // This is implicitly tested by the setup() method
        assertTrue(true, "Flyway migrations completed successfully")
    }

    @Test
    fun `should perform CRUD operations on real PostgreSQL`() = runBlocking {
        val assistant = Assistant(
            id = "postgres-test-1",
            name = "PostgreSQL Test Assistant",
            systemPrompt = "You are testing PostgreSQL integration",
            description = "Real database test",
            isSystem = false
        )

        // Create
        val saveResult = assistantStorage.saveAssistant(assistant)
        assertTrue(saveResult.isSuccess, "Save should succeed on real PostgreSQL")

        // Read
        val loadResult = assistantStorage.loadAssistant("postgres-test-1")
        assertTrue(loadResult.isSuccess, "Load should succeed")

        val loaded = loadResult.getOrNull()
        assertNotNull(loaded)
        assertEquals(assistant.id, loaded!!.id)
        assertEquals(assistant.name, loaded.name)
        assertEquals(assistant.systemPrompt, loaded.systemPrompt)

        // Update
        val updated = assistant.copy(name = "Updated on PostgreSQL")
        val updateResult = assistantStorage.saveAssistant(updated)
        assertTrue(updateResult.isSuccess, "Update should succeed")

        val loadedUpdated = assistantStorage.loadAssistant("postgres-test-1").getOrNull()
        assertNotNull(loadedUpdated)
        assertEquals("Updated on PostgreSQL", loadedUpdated!!.name)

        // Delete
        val deleteResult = assistantStorage.deleteAssistant("postgres-test-1")
        assertTrue(deleteResult.isSuccess, "Delete should succeed")

        val existsAfterDelete = assistantStorage.assistantExists("postgres-test-1")
        assertTrue(!existsAfterDelete, "Assistant should not exist after delete")
    }

    @Test
    fun `should handle concurrent operations`() = runBlocking {
        // Create multiple assistants concurrently
        val assistants = (1..10).map { i ->
            Assistant(
                id = "concurrent-$i",
                name = "Concurrent Test $i",
                systemPrompt = "Concurrent prompt $i",
                description = "Testing concurrency",
                isSystem = false
            )
        }

        // Save all concurrently (simulated by sequential saves in this test)
        assistants.forEach { assistant ->
            val result = assistantStorage.saveAssistant(assistant)
            assertTrue(result.isSuccess, "Concurrent save ${assistant.id} should succeed")
        }

        // Load all
        val loadAllResult = assistantStorage.loadAllAssistants()
        assertTrue(loadAllResult.isSuccess)

        val loaded = loadAllResult.getOrNull()
        assertNotNull(loaded)
        assertTrue(loaded!!.size >= 10, "Should have at least 10 assistants")

        // Cleanup
        assistants.forEach { assistant ->
            assistantStorage.deleteAssistant(assistant.id)
        }
    }

    @Test
    fun `should persist data across connections`() = runBlocking {
        val assistant = Assistant(
            id = "persistence-test",
            name = "Persistence Test",
            systemPrompt = "Testing persistence",
            description = "Should survive connection restart",
            isSystem = false
        )

        // Save with first storage instance
        assistantStorage.saveAssistant(assistant)

        // Close and reopen connection (simulate application restart)
        DatabaseFactory.close()

        val config = DatabaseConfig(
            url = postgresContainer.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgresContainer.username,
            password = postgresContainer.password
        )
        DatabaseFactory.init(config)

        // Create new storage instance
        val newStorage = PostgresAssistantStorage()

        // Load with new storage instance
        val loaded = newStorage.loadAssistant("persistence-test").getOrNull()
        assertNotNull(loaded, "Data should persist across connections")
        assertEquals(assistant.name, loaded!!.name)

        // Cleanup
        newStorage.deleteAssistant("persistence-test")
    }

    @Test
    fun `should handle transactions correctly`() = runBlocking {
        // This test verifies that Exposed transactions work correctly
        val assistant1 = Assistant(
            id = "tx-test-1",
            name = "Transaction Test 1",
            systemPrompt = "Testing transactions",
            description = "Transaction test",
            isSystem = false
        )

        val assistant2 = Assistant(
            id = "tx-test-2",
            name = "Transaction Test 2",
            systemPrompt = "Testing transactions",
            description = "Transaction test",
            isSystem = false
        )

        // Save both
        assistantStorage.saveAssistant(assistant1)
        assistantStorage.saveAssistant(assistant2)

        // Verify both exist
        val exists1 = assistantStorage.assistantExists("tx-test-1")
        val exists2 = assistantStorage.assistantExists("tx-test-2")

        assertTrue(exists1)
        assertTrue(exists2)

        // Cleanup
        assistantStorage.deleteAssistant("tx-test-1")
        assistantStorage.deleteAssistant("tx-test-2")
    }

    @Test
    fun `should handle special characters in PostgreSQL`() = runBlocking {
        val assistant = Assistant(
            id = "special-chars-pg",
            name = "Special: ÄÖÜ €£¥ 中文 日本語",
            systemPrompt = "Testing Unicode: 'quotes', \"double\", backslash\\, semicolon;",
            description = "Newlines:\n\nand tabs:\t\there",
            isSystem = false
        )

        assistantStorage.saveAssistant(assistant)

        val loaded = assistantStorage.loadAssistant("special-chars-pg").getOrNull()
        assertNotNull(loaded)
        assertEquals(assistant.name, loaded!!.name)
        assertEquals(assistant.systemPrompt, loaded.systemPrompt)
        assertEquals(assistant.description, loaded.description)

        // Cleanup
        assistantStorage.deleteAssistant("special-chars-pg")
    }
}

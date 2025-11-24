package com.researchai.persistence.sql

import com.researchai.models.Assistant
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for PostgresAssistantStorage using H2 in-memory database
 */
class PostgresAssistantStorageTest {

    private lateinit var storage: PostgresAssistantStorage
    private lateinit var dataSource: com.zaxxer.hikari.HikariDataSource

    @BeforeEach
    fun setup() {
        // Create H2 database
        dataSource = TestDatabaseFactory.createH2DataSource("assistant_test_${System.currentTimeMillis()}")
        val config = TestDatabaseFactory.createH2Config("assistant_test_${System.currentTimeMillis()}")

        // Initialize database
        DatabaseFactory.init(config)
        TestDatabaseFactory.initH2Schema(dataSource)

        // Create storage instance
        storage = PostgresAssistantStorage()
    }

    @AfterEach
    fun tearDown() {
        // Cleanup
        TestDatabaseFactory.dropH2Schema()
        DatabaseFactory.close()
        dataSource.close()
    }

    @Test
    fun `should save and load assistant`() = runBlocking {
        val assistant = Assistant(
            id = "test-1",
            name = "Test Assistant",
            systemPrompt = "You are a test assistant",
            description = "Test description",
            isSystem = false
        )

        // Save
        val saveResult = storage.saveAssistant(assistant)
        assertTrue(saveResult.isSuccess, "Save should succeed")

        // Load
        val loadResult = storage.loadAssistant("test-1")
        assertTrue(loadResult.isSuccess, "Load should succeed")

        val loaded = loadResult.getOrNull()
        assertNotNull(loaded, "Loaded assistant should not be null")
        assertEquals(assistant.id, loaded!!.id)
        assertEquals(assistant.name, loaded.name)
        assertEquals(assistant.systemPrompt, loaded.systemPrompt)
        assertEquals(assistant.description, loaded.description)
        assertEquals(assistant.isSystem, loaded.isSystem)
    }

    @Test
    fun `should update existing assistant`() = runBlocking {
        val original = Assistant(
            id = "test-2",
            name = "Original Name",
            systemPrompt = "Original prompt",
            description = "Original description",
            isSystem = false
        )

        // Save original
        storage.saveAssistant(original)

        // Update
        val updated = original.copy(
            name = "Updated Name",
            systemPrompt = "Updated prompt",
            description = "Updated description"
        )
        val updateResult = storage.saveAssistant(updated)
        assertTrue(updateResult.isSuccess, "Update should succeed")

        // Load and verify
        val loaded = storage.loadAssistant("test-2").getOrNull()
        assertNotNull(loaded)
        assertEquals("Updated Name", loaded!!.name)
        assertEquals("Updated prompt", loaded.systemPrompt)
        assertEquals("Updated description", loaded.description)
    }

    @Test
    fun `should load all assistants`() = runBlocking {
        // Create multiple assistants
        val assistants = listOf(
            Assistant("test-3", "Assistant 1", "Prompt 1", "Desc 1", false),
            Assistant("test-4", "Assistant 2", "Prompt 2", "Desc 2", true),
            Assistant("test-5", "Assistant 3", "Prompt 3", "Desc 3", false)
        )

        // Save all
        assistants.forEach { storage.saveAssistant(it) }

        // Load all
        val loadResult = storage.loadAllAssistants()
        assertTrue(loadResult.isSuccess, "LoadAll should succeed")

        val loaded = loadResult.getOrNull()
        assertNotNull(loaded)
        assertEquals(3, loaded!!.size, "Should load 3 assistants")

        // Verify all assistants are present
        val loadedIds = loaded.map { it.id }.toSet()
        assertTrue(loadedIds.contains("test-3"))
        assertTrue(loadedIds.contains("test-4"))
        assertTrue(loadedIds.contains("test-5"))
    }

    @Test
    fun `should delete assistant`() = runBlocking {
        val assistant = Assistant(
            id = "test-6",
            name = "To Delete",
            systemPrompt = "Delete me",
            description = "Will be deleted",
            isSystem = false
        )

        // Save
        storage.saveAssistant(assistant)

        // Verify it exists
        val existsBefore = storage.assistantExists("test-6")
        assertTrue(existsBefore, "Assistant should exist before delete")

        // Delete
        val deleteResult = storage.deleteAssistant("test-6")
        assertTrue(deleteResult.isSuccess, "Delete should succeed")

        // Verify it's gone
        val existsAfter = storage.assistantExists("test-6")
        assertTrue(!existsAfter, "Assistant should not exist after delete")

        // Try to load - should return null
        val loadResult = storage.loadAssistant("test-6")
        val loaded = loadResult.getOrNull()
        assertNull(loaded, "Deleted assistant should not be loadable")
    }

    @Test
    fun `should check if assistant exists`() = runBlocking {
        val assistant = Assistant(
            id = "test-7",
            name = "Exists Check",
            systemPrompt = "Check existence",
            description = "Testing exists",
            isSystem = false
        )

        // Before save
        val existsBefore = storage.assistantExists("test-7")
        assertTrue(!existsBefore, "Assistant should not exist before save")

        // Save
        storage.saveAssistant(assistant)

        // After save
        val existsAfter = storage.assistantExists("test-7")
        assertTrue(existsAfter, "Assistant should exist after save")
    }

    @Test
    fun `should return empty list when no assistants exist`() = runBlocking {
        val loadResult = storage.loadAllAssistants()
        assertTrue(loadResult.isSuccess)

        val loaded = loadResult.getOrNull()
        assertNotNull(loaded)
        assertEquals(0, loaded!!.size, "Should return empty list when no assistants exist")
    }

    @Test
    fun `should handle system assistants`() = runBlocking {
        val systemAssistant = Assistant(
            id = "system-1",
            name = "System Assistant",
            systemPrompt = "I am a system assistant",
            description = "Built-in assistant",
            isSystem = true
        )

        // Save system assistant
        storage.saveAssistant(systemAssistant)

        // Load and verify isSystem flag
        val loaded = storage.loadAssistant("system-1").getOrNull()
        assertNotNull(loaded)
        assertTrue(loaded!!.isSystem, "isSystem flag should be preserved")
    }

    @Test
    fun `should handle assistants with empty description`() = runBlocking {
        val assistant = Assistant(
            id = "test-8",
            name = "No Description",
            systemPrompt = "Prompt without description",
            description = "",
            isSystem = false
        )

        storage.saveAssistant(assistant)

        val loaded = storage.loadAssistant("test-8").getOrNull()
        assertNotNull(loaded)
        assertEquals("", loaded!!.description, "Empty description should be preserved")
    }

    @Test
    fun `should handle assistants with special characters`() = runBlocking {
        val assistant = Assistant(
            id = "test-9",
            name = "Special Characters: @#$%",
            systemPrompt = "Prompt with 'quotes' and \"double quotes\"",
            description = "Description with\nnewlines\tand\ttabs",
            isSystem = false
        )

        storage.saveAssistant(assistant)

        val loaded = storage.loadAssistant("test-9").getOrNull()
        assertNotNull(loaded)
        assertEquals(assistant.name, loaded!!.name)
        assertEquals(assistant.systemPrompt, loaded.systemPrompt)
        assertEquals(assistant.description, loaded.description)
    }
}

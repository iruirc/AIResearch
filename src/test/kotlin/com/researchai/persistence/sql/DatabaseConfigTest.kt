package com.researchai.persistence.sql

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Unit tests for DatabaseConfig
 */
class DatabaseConfigTest {

    @Test
    fun `should create config with all parameters`() {
        val config = DatabaseConfig(
            url = "jdbc:postgresql://localhost:5432/testdb",
            driver = "org.postgresql.Driver",
            user = "testuser",
            password = "testpass",
            maxPoolSize = 20,
            minIdle = 10,
            connectionTimeout = 5000,
            idleTimeout = 600000
        )

        assertEquals("jdbc:postgresql://localhost:5432/testdb", config.url)
        assertEquals("org.postgresql.Driver", config.driver)
        assertEquals("testuser", config.user)
        assertEquals("testpass", config.password)
        assertEquals(20, config.maxPoolSize)
        assertEquals(10, config.minIdle)
        assertEquals(5000, config.connectionTimeout)
        assertEquals(600000, config.idleTimeout)
    }

    @Test
    fun `should use default values`() {
        val config = DatabaseConfig(
            url = "jdbc:postgresql://localhost:5432/testdb",
            user = "testuser",
            password = "testpass"
        )

        assertEquals("org.postgresql.Driver", config.driver)
        assertEquals(10, config.maxPoolSize)
        assertEquals(5, config.minIdle)
        assertEquals(10000, config.connectionTimeout)
        assertEquals(300000, config.idleTimeout)
    }

    @Test
    fun `fromEnv should create config with environment defaults`() {
        // Clear any existing env vars for clean test
        val originalUrl = System.getenv("DATABASE_URL")
        val originalUser = System.getenv("DATABASE_USER")
        val originalPassword = System.getenv("DATABASE_PASSWORD")
        val originalPoolSize = System.getenv("DATABASE_POOL_SIZE")

        try {
            // Note: We can't actually set env vars in tests, so this tests the fallback behavior
            val config = DatabaseConfig.fromEnv()

            assertNotNull(config.url)
            assertNotNull(config.user)
            assertNotNull(config.password)
            assertEquals(10, config.maxPoolSize) // Default value when DATABASE_POOL_SIZE is not set
        } finally {
            // Environment variables can't be restored in Kotlin/JVM
            // This is just to document that we tried to be clean
        }
    }

    @Test
    fun `fromEnv should use default PostgreSQL URL when not set`() {
        val config = DatabaseConfig.fromEnv()

        // When DATABASE_URL is not set, it should use the default
        if (System.getenv("DATABASE_URL") == null) {
            assertEquals("jdbc:postgresql://localhost:5432/researchai", config.url)
        }
    }

    @Test
    fun `fromEnv should use default user when not set`() {
        val config = DatabaseConfig.fromEnv()

        // When DATABASE_USER is not set, it should use the default
        if (System.getenv("DATABASE_USER") == null) {
            assertEquals("researchai", config.user)
        }
    }

    @Test
    fun `fromEnv should use default password when not set`() {
        val config = DatabaseConfig.fromEnv()

        // When DATABASE_PASSWORD is not set, it should use the default
        if (System.getenv("DATABASE_PASSWORD") == null) {
            assertEquals("researchai_dev_password", config.password)
        }
    }

    @Test
    fun `should support H2 configuration`() {
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:test;MODE=PostgreSQL",
            driver = "org.h2.Driver",
            user = "sa",
            password = "",
            maxPoolSize = 3,
            minIdle = 1
        )

        assertEquals("jdbc:h2:mem:test;MODE=PostgreSQL", config.url)
        assertEquals("org.h2.Driver", config.driver)
        assertEquals("sa", config.user)
        assertEquals("", config.password)
    }
}

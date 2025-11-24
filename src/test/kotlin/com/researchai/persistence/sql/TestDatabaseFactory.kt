package com.researchai.persistence.sql

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import com.researchai.persistence.sql.tables.*

/**
 * Test utility for creating in-memory H2 databases for unit tests
 * Uses H2 in PostgreSQL compatibility mode
 */
object TestDatabaseFactory {

    /**
     * Create a new in-memory H2 database for testing
     * @param name Unique name for the database instance
     * @return HikariDataSource configured for H2
     */
    fun createH2DataSource(name: String = "test"): HikariDataSource {
        val config = HikariConfig().apply {
            driverClassName = "org.h2.Driver"
            jdbcUrl = "jdbc:h2:mem:$name;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
            username = "sa"
            password = ""
            maximumPoolSize = 3
            minimumIdle = 1
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            validate()
        }
        return HikariDataSource(config)
    }

    /**
     * Initialize H2 database with schema
     * Creates all tables in memory
     */
    fun initH2Schema(dataSource: HikariDataSource) {
        Database.connect(dataSource)

        transaction {
            // Create all tables
            SchemaUtils.create(
                AssistantsTable,
                AssistantPipelinesTable,
                ScheduledTasksTable,
                ChatSessionsTable,
                PipelineExecutionsTable,
                UsersTable,
                UserPreferencesTable
            )
        }
    }

    /**
     * Drop all tables from H2 database
     */
    fun dropH2Schema() {
        transaction {
            SchemaUtils.drop(
                UserPreferencesTable,
                UsersTable,
                PipelineExecutionsTable,
                ChatSessionsTable,
                ScheduledTasksTable,
                AssistantPipelinesTable,
                AssistantsTable
            )
        }
    }

    /**
     * Create a test DatabaseConfig for H2
     */
    fun createH2Config(name: String = "test"): DatabaseConfig {
        return DatabaseConfig(
            url = "jdbc:h2:mem:$name;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
            driver = "org.h2.Driver",
            user = "sa",
            password = "",
            maxPoolSize = 3,
            minIdle = 1
        )
    }
}

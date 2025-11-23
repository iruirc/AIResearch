package com.researchai.persistence.sql

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory

/**
 * Factory for creating and managing database connections
 * Uses HikariCP for connection pooling
 */
object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)
    private var dataSource: HikariDataSource? = null

    /**
     * Initialize database connection pool and run migrations
     */
    fun init(config: DatabaseConfig = DatabaseConfig.fromEnv()) {
        if (dataSource != null) {
            logger.warn("Database already initialized")
            return
        }

        logger.info("Initializing database connection pool")

        val hikariConfig = HikariConfig().apply {
            driverClassName = config.driver
            jdbcUrl = config.url
            username = config.user
            password = config.password
            maximumPoolSize = config.maxPoolSize
            minimumIdle = config.minIdle
            idleTimeout = config.idleTimeout
            connectionTimeout = config.connectionTimeout
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"

            // Connection test query
            connectionTestQuery = "SELECT 1"

            // Leak detection (for debugging)
            leakDetectionThreshold = 60000

            validate()
        }

        dataSource = HikariDataSource(hikariConfig)
        Database.connect(dataSource!!)

        logger.info("Database connection pool initialized successfully")

        // Run Flyway migrations
        FlywayMigrator.migrate(dataSource!!)
    }

    /**
     * Close database connection pool
     */
    fun close() {
        dataSource?.close()
        dataSource = null
        logger.info("Database connection pool closed")
    }

    /**
     * Execute database query in a suspended transaction
     */
    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}

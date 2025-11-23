package com.researchai.persistence.sql

import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Flyway migration manager for database schema versioning
 */
object FlywayMigrator {
    private val logger = LoggerFactory.getLogger(FlywayMigrator::class.java)

    /**
     * Run database migrations
     */
    fun migrate(dataSource: DataSource) {
        logger.info("Running Flyway migrations")

        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .validateOnMigrate(true)
            .load()

        val result = flyway.migrate()

        logger.info(
            "Flyway migration completed: {} migrations executed, current version: {}",
            result.migrationsExecuted,
            result.targetSchemaVersion
        )
    }

    /**
     * Clean database (use only for development!)
     */
    fun clean(dataSource: DataSource) {
        logger.warn("Cleaning database (use only for development!)")
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
        flyway.clean()
    }
}

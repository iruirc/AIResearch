package com.researchai.persistence.sql

/**
 * Configuration for PostgreSQL database connection
 */
data class DatabaseConfig(
    val url: String,
    val driver: String = "org.postgresql.Driver",
    val user: String,
    val password: String,
    val maxPoolSize: Int = 10,
    val minIdle: Int = 5,
    val connectionTimeout: Long = 10000,
    val idleTimeout: Long = 300000
) {
    companion object {
        /**
         * Load configuration from environment variables
         */
        fun fromEnv(): DatabaseConfig {
            return DatabaseConfig(
                url = System.getenv("DATABASE_URL")
                    ?: "jdbc:postgresql://localhost:5432/researchai",
                user = System.getenv("DATABASE_USER") ?: "researchai",
                password = System.getenv("DATABASE_PASSWORD") ?: "researchai_dev_password",
                maxPoolSize = System.getenv("DATABASE_POOL_SIZE")?.toIntOrNull() ?: 10
            )
        }
    }
}

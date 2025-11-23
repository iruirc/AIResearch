package com.researchai.persistence.sql

import com.researchai.persistence.sql.DatabaseFactory.dbQuery
import com.researchai.persistence.sql.tables.UsersTable
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.slf4j.LoggerFactory

/**
 * User model for OAuth authentication
 */
data class User(
    val id: String,
    val email: String,
    val name: String,
    val provider: String, // OAuth provider (GOOGLE, GITHUB, MICROSOFT)
    val providerId: String, // ID from OAuth provider
    val avatar: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis()
)

/**
 * PostgreSQL implementation for User storage
 * Handles OAuth user authentication data
 */
class PostgresUserStorage {
    private val logger = LoggerFactory.getLogger(PostgresUserStorage::class.java)

    /**
     * Saves or updates a user
     */
    suspend fun saveUser(user: User): Result<Unit> = dbQuery {
        try {
            UsersTable.upsert {
                it[id] = user.id
                it[email] = user.email
                it[name] = user.name
                it[provider] = user.provider
                it[providerId] = user.providerId
                it[avatar] = user.avatar
                it[createdAt] = Instant.fromEpochMilliseconds(user.createdAt)
                it[lastLoginAt] = Instant.fromEpochMilliseconds(user.lastLoginAt)
            }

            logger.debug("Saved user: ${user.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to save user ${user.id}", e)
            Result.failure(e)
        }
    }

    /**
     * Loads a user by ID
     */
    suspend fun loadUser(userId: String): Result<User?> = dbQuery {
        try {
            val row = UsersTable.selectAll()
                .where { UsersTable.id eq userId }
                .singleOrNull()

            val user = row?.let {
                User(
                    id = it[UsersTable.id],
                    email = it[UsersTable.email],
                    name = it[UsersTable.name],
                    provider = it[UsersTable.provider],
                    providerId = it[UsersTable.providerId],
                    avatar = it[UsersTable.avatar],
                    createdAt = it[UsersTable.createdAt].toEpochMilliseconds(),
                    lastLoginAt = it[UsersTable.lastLoginAt].toEpochMilliseconds()
                )
            }

            logger.debug("Loaded user: $userId, found: ${user != null}")
            Result.success(user)
        } catch (e: Exception) {
            logger.error("Failed to load user $userId", e)
            Result.failure(e)
        }
    }

    /**
     * Finds a user by email
     */
    suspend fun findByEmail(email: String): Result<User?> = dbQuery {
        try {
            val row = UsersTable.selectAll()
                .where { UsersTable.email eq email }
                .singleOrNull()

            val user = row?.let {
                User(
                    id = it[UsersTable.id],
                    email = it[UsersTable.email],
                    name = it[UsersTable.name],
                    provider = it[UsersTable.provider],
                    providerId = it[UsersTable.providerId],
                    avatar = it[UsersTable.avatar],
                    createdAt = it[UsersTable.createdAt].toEpochMilliseconds(),
                    lastLoginAt = it[UsersTable.lastLoginAt].toEpochMilliseconds()
                )
            }

            logger.debug("Found user by email: $email, found: ${user != null}")
            Result.success(user)
        } catch (e: Exception) {
            logger.error("Failed to find user by email $email", e)
            Result.failure(e)
        }
    }

    /**
     * Finds a user by OAuth provider and provider ID
     */
    suspend fun findByProvider(provider: String, providerId: String): Result<User?> = dbQuery {
        try {
            val row = UsersTable.selectAll()
                .where { (UsersTable.provider eq provider) and (UsersTable.providerId eq providerId) }
                .singleOrNull()

            val user = row?.let {
                User(
                    id = it[UsersTable.id],
                    email = it[UsersTable.email],
                    name = it[UsersTable.name],
                    provider = it[UsersTable.provider],
                    providerId = it[UsersTable.providerId],
                    avatar = it[UsersTable.avatar],
                    createdAt = it[UsersTable.createdAt].toEpochMilliseconds(),
                    lastLoginAt = it[UsersTable.lastLoginAt].toEpochMilliseconds()
                )
            }

            logger.debug("Found user by provider: $provider/$providerId, found: ${user != null}")
            Result.success(user)
        } catch (e: Exception) {
            logger.error("Failed to find user by provider $provider/$providerId", e)
            Result.failure(e)
        }
    }

    /**
     * Updates user's last login timestamp
     */
    suspend fun updateLastLogin(userId: String): Result<Unit> = dbQuery {
        try {
            UsersTable.update({ UsersTable.id eq userId }) {
                it[lastLoginAt] = kotlinx.datetime.Clock.System.now()
            }

            logger.debug("Updated last login for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to update last login for user $userId", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes a user
     */
    suspend fun deleteUser(userId: String): Result<Unit> = dbQuery {
        try {
            UsersTable.deleteWhere { id eq userId }
            logger.info("Deleted user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to delete user $userId", e)
            Result.failure(e)
        }
    }
}

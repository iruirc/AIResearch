package com.researchai.persistence.sql.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * Exposed table definition for Users
 * Maps to the users table in PostgreSQL
 */
object UsersTable : Table("users") {
    val id = text("id")
    val email = text("email").uniqueIndex()
    val name = text("name")
    val provider = customEnumeration(
        name = "provider",
        sql = "oauth_provider",
        fromDb = { value -> value as String },
        toDb = { it.toString() }
    )
    val providerId = text("provider_id")
    val avatar = text("avatar").nullable()
    val createdAt = timestamp("created_at").default(Instant.now())
    val lastLoginAt = timestamp("last_login_at").default(Instant.now())

    override val primaryKey = PrimaryKey(id)
}

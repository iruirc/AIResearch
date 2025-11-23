package com.researchai.persistence.sql.tables

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Exposed table definition for Users
 * Maps to the users table in PostgreSQL
 */
object UsersTable : Table("users") {
    val id = text("id")
    val email = text("email").uniqueIndex()
    val name = text("name")
    val provider = text("provider")
    val providerId = text("provider_id")
    val avatar = text("avatar").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val lastLoginAt = timestamp("last_login_at").default(Clock.System.now())

    override val primaryKey = PrimaryKey(id)
}

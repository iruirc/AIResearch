# PostgreSQL Architecture - ResearchAI

**Version:** 1.0
**Date:** 2025-11-24
**Status:** Production Ready

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture Design](#architecture-design)
3. [Database Schema](#database-schema)
4. [Configuration](#configuration)
5. [Migration Guide](#migration-guide)
6. [Troubleshooting](#troubleshooting)
7. [Performance Considerations](#performance-considerations)
8. [Monitoring & Operations](#monitoring--operations)
9. [Backup & Recovery](#backup--recovery)
10. [Security](#security)

---

## Overview

ResearchAI uses **PostgreSQL 17** as the primary persistence layer, providing robust relational data storage with JSONB support for complex nested structures. The system implements a **Hybrid Storage Strategy** with graceful fallback to JSON file-based storage.

### Key Features

- **PostgreSQL 17 Alpine** - Latest stable version with minimal footprint
- **Exposed ORM** - Type-safe database access with Kotlin DSL
- **Flyway Migrations** - Version-controlled schema evolution
- **HikariCP** - High-performance JDBC connection pooling
- **JSONB Columns** - Flexible storage for nested objects (messages, parameters, steps)
- **Feature Flag** - `ENABLE_POSTGRES` for easy enable/disable
- **Graceful Fallback** - Automatic fallback to JSON storage if PostgreSQL unavailable
- **Comprehensive Testing** - 29 tests with H2 (fast) + Testcontainers (production-like)

### Architecture Goals

1. **Reliability** - Graceful degradation, no data loss
2. **Performance** - Connection pooling, indexed queries, JSONB optimization
3. **Maintainability** - Type-safe queries, versioned migrations
4. **Testability** - Fast H2 unit tests, real PostgreSQL integration tests
5. **Scalability** - Connection pooling, efficient indexes

---

## Architecture Design

### Layered Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Presentation Layer                      │
│          (Routes, Controllers, REST Endpoints)               │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│                       Service Layer                          │
│  (ChatSessionManager, AssistantManager, SchedulerManager)    │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│                    Storage Interface Layer                   │
│  (PersistenceStorage, AssistantStorage, TaskStorage, etc.)   │
└─────┬───────────────────────────────────────────┬───────────┘
      │                                           │
┌─────▼──────────────────┐          ┌────────────▼────────────┐
│  PostgreSQL Storage    │          │    JSON File Storage    │
│  (SQL + JSONB)         │          │    (Fallback)           │
└─────┬──────────────────┘          └─────────────────────────┘
      │
┌─────▼──────────────────────────────────────────────────────┐
│                     Data Access Layer                       │
│         (Exposed ORM, HikariCP, Flyway Migrations)          │
└─────┬──────────────────────────────────────────────────────┘
      │
┌─────▼──────────────────────────────────────────────────────┐
│                   PostgreSQL Database                       │
│              (postgres:17-alpine in Docker)                 │
└─────────────────────────────────────────────────────────────┘
```

### Component Breakdown

#### 1. Storage Interface Layer

**Purpose:** Abstract interface for all storage operations

**Key Interfaces:**
- `PersistenceStorage` - ChatSession CRUD operations
- `AssistantStorage` - Assistant management
- `ScheduledTaskStorage` - Task persistence
- `AssistantPipelineStorage` - Pipeline configuration storage
- `PipelineExecutionStorage` - Execution tracking

**Design Pattern:** Repository Pattern

#### 2. StorageFactory

**Location:** `src/main/kotlin/com/researchai/persistence/sql/StorageFactory.kt`

**Purpose:** Factory pattern for conditional storage creation

```kotlin
object StorageFactory {
    fun createPersistenceStorage(enablePostgres: Boolean): PersistenceStorage
    fun createAssistantStorage(enablePostgres: Boolean): AssistantStorage
}
```

**Behavior:**
- If `enablePostgres = true` → Try PostgreSQL, fallback to JSON on error
- If `enablePostgres = false` → Use JSON storage directly
- Logs all initialization attempts and fallbacks

#### 3. DatabaseFactory

**Location:** `src/main/kotlin/com/researchai/persistence/sql/DatabaseFactory.kt`

**Purpose:** Singleton for database connection lifecycle management

**Responsibilities:**
- Initialize HikariCP connection pool
- Connect Exposed ORM to database
- Run Flyway migrations automatically
- Provide `dbQuery()` wrapper for suspended transactions
- Graceful shutdown on application close

**Configuration:**
```kotlin
HikariConfig {
    maximumPoolSize = 10
    minimumIdle = 5
    connectionTimeout = 10000ms
    idleTimeout = 300000ms (5 min)
    transactionIsolation = TRANSACTION_READ_COMMITTED
    connectionTestQuery = "SELECT 1"
    leakDetectionThreshold = 60000ms
}
```

#### 4. FlywayMigrator

**Location:** `src/main/kotlin/com/researchai/persistence/sql/FlywayMigrator.kt`

**Purpose:** Database schema version control

**Features:**
- Automatic migration execution on startup
- Baseline on migrate (supports existing databases)
- Validation on migrate
- Clean method for development (dangerous in production)

**Migration Location:** `src/main/resources/db/migration/`

**Naming Convention:** `V{version}__{description}.sql`

Example: `V1__create_initial_schema.sql`, `V2__add_indexes.sql`

#### 5. Exposed Table Definitions

**Location:** `src/main/kotlin/com/researchai/persistence/sql/tables/`

**Purpose:** Type-safe SQL table mappings

**Tables:**
- `AssistantsTable` - AI assistant definitions
- `AssistantPipelinesTable` - Multi-assistant pipelines
- `ScheduledTasksTable` - Recurring task definitions
- `ChatSessionsTable` - Conversation sessions with messages
- `PipelineExecutionsTable` - Pipeline execution tracking
- `UsersTable` - User accounts (OAuth)
- `UserPreferencesTable` - User AI provider preferences

**Technology:** Exposed ORM with Kotlin DSL

#### 6. PostgreSQL Storage Implementations

**Location:** `src/main/kotlin/com/researchai/persistence/sql/`

**Classes:**
- `PostgresAssistantStorage` - Assistant CRUD
- `PostgresTaskStorage` - Scheduled task persistence
- `PostgresPipelineStorage` - Pipeline configuration storage
- `PostgresPersistenceStorage` - ChatSession persistence (most complex)
- `PostgresExecutionStorage` - Pipeline execution tracking
- `PostgresUserStorage` - User account management
- `PostgresPreferencesStorage` - User preference storage

**Common Patterns:**
- All use `DatabaseFactory.dbQuery()` for suspended transactions
- All return `Result<T>` for error handling
- All use SLF4J logging
- All implement graceful error handling

---

## Database Schema

### Schema Version: V2

**Current Migrations:**
- **V1__create_initial_schema.sql** - Core schema with 7 tables, 5 ENUMs
- **V2__add_indexes.sql** - Performance indexes (16 total)

### Tables Overview

#### 1. assistants

**Purpose:** Store AI assistant definitions (system + custom)

```sql
CREATE TABLE assistants (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    system_prompt TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

**Indexes:**
- `idx_assistants_is_system` - Filter system vs custom assistants
- `idx_assistants_name` - Search by name

**Key Features:**
- `is_system` flag protects built-in assistants from deletion
- Stores custom system prompts for personalized AI behavior

#### 2. assistant_pipelines

**Purpose:** Multi-assistant conversation pipelines

```sql
CREATE TABLE assistant_pipelines (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    assistant_ids JSONB NOT NULL,
    provider_id provider_type NOT NULL,
    model TEXT,
    default_parameters JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

**Indexes:**
- `idx_pipelines_name` - Search by pipeline name
- `idx_pipelines_provider` - Filter by AI provider

**JSONB Fields:**
- `assistant_ids` - List<String> of assistant IDs
- `default_parameters` - Map<String, Any> for temperature, maxTokens, etc.

#### 3. scheduled_tasks

**Purpose:** Automated recurring chat tasks

```sql
CREATE TABLE scheduled_tasks (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    task_request TEXT NOT NULL,
    interval_seconds BIGINT NOT NULL,
    execute_immediately BOOLEAN NOT NULL DEFAULT FALSE,
    provider_id provider_type,
    model TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

**Key Features:**
- Nullable `provider_id` and `model` allow global defaults
- `interval_seconds` defines execution frequency (min 10 seconds)

#### 4. chat_sessions

**Purpose:** Conversation sessions with full message history

```sql
CREATE TABLE chat_sessions (
    id TEXT PRIMARY KEY,
    title TEXT,
    messages JSONB NOT NULL DEFAULT '[]',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_accessed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    assistant_id TEXT REFERENCES assistants(id) ON DELETE SET NULL,
    scheduled_task_id TEXT REFERENCES scheduled_tasks(id) ON DELETE CASCADE,
    pipeline_id TEXT REFERENCES assistant_pipelines(id) ON DELETE SET NULL,
    archived_messages JSONB NOT NULL DEFAULT '[]',
    compression_config JSONB NOT NULL DEFAULT '{}',
    compression_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT check_mutual_exclusivity CHECK (
        (assistant_id IS NOT NULL)::int +
        (scheduled_task_id IS NOT NULL)::int +
        (pipeline_id IS NOT NULL)::int <= 1
    )
);
```

**Indexes:**
- `idx_sessions_assistant` - Sessions by assistant
- `idx_sessions_task` - Sessions by scheduled task
- `idx_sessions_pipeline` - Sessions by pipeline
- `idx_sessions_last_accessed` - Recent sessions (DESC)
- `idx_sessions_created_at` - Session chronology (DESC)
- `idx_sessions_messages` - GIN index for full-text search in messages (JSONB)

**JSONB Fields:**
- `messages` - List<Message> with role, content, model, tokens, timestamp
- `archived_messages` - List<Message> from compression
- `compression_config` - Map<String, Any> for compression strategy settings

**Constraints:**
- **Mutual Exclusivity:** Session can have ONLY ONE of: `assistant_id`, `scheduled_task_id`, `pipeline_id`
- Enforced by CHECK constraint at database level

**Foreign Key Behaviors:**
- `assistant_id` → `ON DELETE SET NULL` (preserve session if assistant deleted)
- `scheduled_task_id` → `ON DELETE CASCADE` (delete session if task deleted)
- `pipeline_id` → `ON DELETE SET NULL` (preserve session if pipeline deleted)

#### 5. pipeline_executions

**Purpose:** Track multi-assistant pipeline execution

```sql
CREATE TABLE pipeline_executions (
    id TEXT PRIMARY KEY,
    pipeline_id TEXT REFERENCES assistant_pipelines(id) ON DELETE SET NULL,
    pipeline_name TEXT NOT NULL,
    session_id TEXT REFERENCES chat_sessions(id) ON DELETE CASCADE,
    initial_message TEXT NOT NULL,
    assistant_ids JSONB NOT NULL,
    provider_id provider_type NOT NULL,
    model TEXT NOT NULL,
    parameters JSONB NOT NULL DEFAULT '{}',
    steps JSONB NOT NULL DEFAULT '[]',
    status pipeline_status NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    error JSONB
);
```

**Indexes:**
- `idx_executions_pipeline` - Executions by pipeline
- `idx_executions_session` - Executions by session
- `idx_executions_status` - Filter by status
- `idx_executions_start_time` - Chronological order (DESC)

**JSONB Fields:**
- `assistant_ids` - List<String> of assistants in execution order
- `parameters` - Map<String, Any> for temperature, maxTokens
- `steps` - List<Map<String, Any>> tracking each assistant's response
- `error` - Map<String, String> with error details if failed

#### 6. users

**Purpose:** User accounts (OAuth authentication)

```sql
CREATE TABLE users (
    id TEXT PRIMARY KEY,
    email TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    provider oauth_provider NOT NULL,
    provider_id TEXT NOT NULL,
    avatar TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

**Indexes:**
- `idx_users_email` - Find user by email
- `idx_users_provider` - Find user by OAuth provider + provider_id

**OAuth Support:** GOOGLE, GITHUB, MICROSOFT

#### 7. user_preferences

**Purpose:** User AI provider preferences

```sql
CREATE TABLE user_preferences (
    id SERIAL PRIMARY KEY,
    user_id TEXT REFERENCES users(id) ON DELETE CASCADE,
    provider_id TEXT NOT NULL,
    model TEXT NOT NULL,
    temperature DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    max_tokens INTEGER NOT NULL DEFAULT 4096,
    format response_format NOT NULL DEFAULT 'PLAIN_TEXT',
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id)
);
```

**Constraints:**
- `UNIQUE(user_id)` - One preference record per user
- `ON DELETE CASCADE` - Delete preferences when user deleted

### ENUMs

#### message_role
```sql
CREATE TYPE message_role AS ENUM ('user', 'assistant', 'system');
```

#### provider_type
```sql
CREATE TYPE provider_type AS ENUM ('CLAUDE', 'OPENAI', 'HUGGINGFACE', 'GEMINI', 'CUSTOM');
```

#### oauth_provider
```sql
CREATE TYPE oauth_provider AS ENUM ('GOOGLE', 'GITHUB', 'MICROSOFT');
```

#### response_format
```sql
CREATE TYPE response_format AS ENUM ('PLAIN_TEXT', 'MARKDOWN', 'JSON');
```

#### pipeline_status
```sql
CREATE TYPE pipeline_status AS ENUM ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'PARTIAL');
```

---

## Configuration

### Environment Variables

**PostgreSQL Connection:**
```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/researchai
DATABASE_USER=researchai
DATABASE_PASSWORD=researchai_dev_password
DATABASE_POOL_SIZE=10
```

**Feature Flag:**
```bash
ENABLE_POSTGRES=true
```

**Docker Environment:**
```bash
# In docker-compose.yml, postgres container uses:
POSTGRES_DB=${DATABASE_NAME:-researchai}
POSTGRES_USER=${DATABASE_USER:-researchai}
POSTGRES_PASSWORD=${DATABASE_PASSWORD:-researchai_dev_password}

# App container uses:
DATABASE_URL=jdbc:postgresql://postgres:5432/researchai
```

### DatabaseConfig

**Location:** `src/main/kotlin/com/researchai/persistence/sql/DatabaseConfig.kt`

```kotlin
data class DatabaseConfig(
    val url: String,
    val driver: String = "org.postgresql.Driver",
    val user: String,
    val password: String,
    val maxPoolSize: Int = 10,
    val minIdle: Int = 5,
    val connectionTimeout: Long = 10000,
    val idleTimeout: Long = 300000
)
```

**Loading from Environment:**
```kotlin
val config = DatabaseConfig.fromEnv()
```

### AppModule Integration

**Location:** `src/main/kotlin/com/researchai/di/AppModule.kt`

```kotlin
class AppModule(config: ApplicationConfig) {
    private val enablePostgres = System.getenv("ENABLE_POSTGRES")?.toBoolean() ?: false

    init {
        if (enablePostgres) {
            try {
                DatabaseFactory.init(DatabaseConfig.fromEnv())
                logger.info("PostgreSQL initialized successfully")
            } catch (e: Exception) {
                logger.error("Failed to initialize PostgreSQL, using JSON fallback", e)
            }
        }
    }

    val persistenceStorage: PersistenceStorage =
        StorageFactory.createPersistenceStorage(enablePostgres)

    val assistantStorage: AssistantStorage =
        StorageFactory.createAssistantStorage(enablePostgres)
}
```

### Docker Compose Configuration

**File:** `docker-compose.yml`

```yaml
services:
  postgres:
    image: postgres:17-alpine
    container_name: researchai-postgres
    environment:
      POSTGRES_DB: ${DATABASE_NAME:-researchai}
      POSTGRES_USER: ${DATABASE_USER:-researchai}
      POSTGRES_PASSWORD: ${DATABASE_PASSWORD:-researchai_dev_password}
    ports:
      - "${DATABASE_PORT:-5432}:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U researchai"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - claude-network

  claude-chat:
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      DATABASE_URL: jdbc:postgresql://postgres:5432/researchai
      ENABLE_POSTGRES: "true"

volumes:
  postgres_data:

networks:
  claude-network:
    driver: bridge
```

---

## Migration Guide

### Enabling PostgreSQL

#### Step 1: Set Environment Variable

**In `.env`:**
```bash
ENABLE_POSTGRES=true
```

**In Docker:**
```bash
docker-compose up -d postgres
```

#### Step 2: Verify Database is Running

```bash
docker-compose logs postgres

# Should see:
# PostgreSQL init process complete; ready for start up.
# database system is ready to accept connections
```

#### Step 3: Start Application

```bash
./gradlew run

# Or in Docker:
docker-compose up -d
```

#### Step 4: Verify Migration

**Check logs for:**
```
INFO  c.r.p.sql.DatabaseFactory - Initializing database connection pool
INFO  c.r.p.sql.FlywayMigrator - Running Flyway migrations
INFO  c.r.p.sql.FlywayMigrator - Flyway migration completed: 2 migrations executed
INFO  c.r.p.sql.StorageFactory - Attempting to use PostgreSQL for persistence storage...
INFO  c.r.d.AppModule - PostgreSQL initialized successfully
```

### Disabling PostgreSQL (Rollback to JSON)

#### Step 1: Set Environment Variable

```bash
ENABLE_POSTGRES=false
```

#### Step 2: Restart Application

```bash
./gradlew run

# Or in Docker:
docker-compose restart claude-chat
```

#### Step 3: Verify JSON Storage

**Check logs for:**
```
INFO  c.r.p.sql.StorageFactory - Using JSON-based persistence storage (PostgreSQL disabled)
```

**Data Location:** `data/sessions/*.json`, `data/assistants/*.json`

### Migrating Existing JSON Data to PostgreSQL

**Currently:** No automatic migration tool implemented

**Manual Migration Strategy:**

1. **Export existing JSON data:**
   - Copy `data/` directory to backup location

2. **Enable PostgreSQL:**
   - Set `ENABLE_POSTGRES=true`
   - Start application (creates empty database)

3. **Write migration script:**
   ```kotlin
   // Pseudo-code for migration tool
   fun migrateJsonToPostgres() {
       val jsonStorage = JsonPersistenceStorage()
       val postgresStorage = PostgresPersistenceStorage()

       // Migrate sessions
       val sessions = jsonStorage.loadAllSessions()
       sessions.forEach { postgresStorage.saveSession(it) }

       // Migrate assistants
       val assistants = jsonStorage.loadAllAssistants()
       assistants.forEach { postgresStorage.saveAssistant(it) }
   }
   ```

4. **Verify migration:**
   - Check record counts match
   - Spot-check session content
   - Test application functionality

**Future Work:** Automated migration CLI tool

---

## Troubleshooting

### Connection Issues

#### Problem: "Failed to initialize PostgreSQL"

**Symptoms:**
```
ERROR c.r.p.sql.StorageFactory - Failed to initialize PostgreSQL persistence storage
Falling back to JSON
```

**Causes:**
1. PostgreSQL not running
2. Wrong credentials in `.env`
3. Network connectivity issues
4. Database doesn't exist

**Solutions:**

**1. Verify PostgreSQL is running:**
```bash
docker-compose ps

# Should show postgres container as "Up" and "healthy"
```

**2. Check PostgreSQL logs:**
```bash
docker-compose logs postgres

# Look for errors or "database system is ready"
```

**3. Test connection manually:**
```bash
docker exec -it researchai-postgres psql -U researchai -d researchai

# Should open psql shell if credentials correct
```

**4. Verify environment variables:**
```bash
cat .env | grep DATABASE

# Should show:
# DATABASE_URL=jdbc:postgresql://localhost:5432/researchai
# DATABASE_USER=researchai
# DATABASE_PASSWORD=researchai_dev_password
# ENABLE_POSTGRES=true
```

**5. Restart PostgreSQL:**
```bash
docker-compose restart postgres

# Wait for healthcheck to pass
docker-compose ps
```

### Migration Issues

#### Problem: "Flyway migration failed"

**Symptoms:**
```
ERROR c.r.p.sql.FlywayMigrator - Flyway migration failed
org.flywaydb.core.api.exception.FlywayException
```

**Causes:**
1. Schema mismatch
2. Corrupted migration files
3. Manual schema changes

**Solutions:**

**1. Check Flyway schema history:**
```bash
docker exec -it researchai-postgres psql -U researchai -d researchai

SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

**2. If development environment, clean and recreate:**
```bash
# WARNING: This deletes all data!
docker-compose down -v
docker-compose up -d
```

**3. If production, manual migration repair:**
```bash
# Mark failed migration as repaired
docker exec -it researchai-postgres psql -U researchai -d researchai

UPDATE flyway_schema_history
SET success = true
WHERE version = 'X';
```

### Performance Issues

#### Problem: "Slow query performance"

**Symptoms:**
- API responses > 1 second
- High CPU on postgres container

**Diagnosis:**

**1. Check query execution plans:**
```sql
EXPLAIN ANALYZE
SELECT * FROM chat_sessions
WHERE last_accessed_at > NOW() - INTERVAL '7 days';
```

**2. Check index usage:**
```sql
SELECT
    schemaname,
    tablename,
    indexname,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch
FROM pg_stat_user_indexes
ORDER BY idx_scan;
```

**3. Check connection pool stats:**
```bash
# Check HikariCP logs for:
# - Connection acquisition time
# - Active connections
# - Idle connections
```

**Solutions:**

**1. Add missing indexes:**
```sql
-- Example: If filtering by title frequently
CREATE INDEX idx_sessions_title ON chat_sessions(title);
```

**2. Analyze and vacuum tables:**
```sql
ANALYZE chat_sessions;
VACUUM ANALYZE chat_sessions;
```

**3. Increase connection pool size:**
```bash
# In .env
DATABASE_POOL_SIZE=20
```

### Data Consistency Issues

#### Problem: "Session data corrupted"

**Symptoms:**
- JSON deserialization errors
- Missing messages in sessions

**Diagnosis:**

**1. Check JSONB structure:**
```sql
SELECT id, jsonb_pretty(messages)
FROM chat_sessions
WHERE id = 'problem-session-id';
```

**2. Validate foreign key constraints:**
```sql
SELECT
    c.id,
    c.assistant_id,
    a.id as assistant_exists
FROM chat_sessions c
LEFT JOIN assistants a ON c.assistant_id = a.id
WHERE c.assistant_id IS NOT NULL
  AND a.id IS NULL;
```

**Solutions:**

**1. Fix JSONB structure manually:**
```sql
UPDATE chat_sessions
SET messages = '[]'::jsonb
WHERE id = 'corrupted-session-id';
```

**2. Clean up orphaned references:**
```sql
UPDATE chat_sessions
SET assistant_id = NULL
WHERE assistant_id NOT IN (SELECT id FROM assistants);
```

---

## Performance Considerations

### Query Optimization

#### Indexed Queries

**All queries use indexes defined in V2 migration:**

**1. Recent sessions (most common):**
```sql
-- Uses: idx_sessions_last_accessed
SELECT * FROM chat_sessions
ORDER BY last_accessed_at DESC
LIMIT 50;
```

**2. Sessions by assistant:**
```sql
-- Uses: idx_sessions_assistant
SELECT * FROM chat_sessions
WHERE assistant_id = 'assistant-id';
```

**3. Full-text search in messages:**
```sql
-- Uses: idx_sessions_messages (GIN)
SELECT * FROM chat_sessions
WHERE messages @> '[{"content": "search term"}]'::jsonb;
```

#### JSONB Performance

**Best Practices:**

**1. Use JSONB operators for filtering:**
```sql
-- Efficient: Uses GIN index
WHERE messages @> '[{"role": "user"}]'::jsonb

-- Inefficient: Full table scan
WHERE messages::text LIKE '%user%'
```

**2. Extract frequently accessed fields:**
```sql
-- If filtering by message role frequently, consider:
ALTER TABLE chat_sessions
ADD COLUMN last_message_role TEXT;

CREATE INDEX idx_sessions_last_role ON chat_sessions(last_message_role);
```

**3. Limit JSONB array size:**
- Archive old messages via compression feature
- Keep active messages < 50 items for optimal performance

### Connection Pooling

**HikariCP Configuration:**

```kotlin
maximumPoolSize = 10    // Max concurrent connections
minimumIdle = 5         // Idle connections in pool
connectionTimeout = 10s // Max wait time for connection
idleTimeout = 5min      // Idle connection eviction time
```

**Tuning Guidelines:**

**Low Traffic (< 10 req/sec):**
```
maximumPoolSize = 10
minimumIdle = 5
```

**Medium Traffic (10-50 req/sec):**
```
maximumPoolSize = 20
minimumIdle = 10
```

**High Traffic (> 50 req/sec):**
```
maximumPoolSize = 50
minimumIdle = 20
```

**Monitor:**
```
HikariPool-1 - Pool stats (total=10, active=3, idle=7, waiting=0)
```

### Database Tuning

**PostgreSQL Configuration (postgresql.conf):**

```ini
# Memory
shared_buffers = 256MB
effective_cache_size = 1GB
work_mem = 16MB

# Connections
max_connections = 100

# WAL
wal_buffers = 16MB
checkpoint_completion_target = 0.9

# Query Planning
random_page_cost = 1.1  # For SSD
effective_io_concurrency = 200
```

**Apply changes:**
```bash
docker-compose restart postgres
```

---

## Monitoring & Operations

### Health Checks

#### Docker Healthcheck

**Defined in docker-compose.yml:**
```yaml
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U researchai"]
  interval: 10s
  timeout: 5s
  retries: 5
```

**Check status:**
```bash
docker inspect researchai-postgres | jq '.[0].State.Health'
```

#### Application Health

**Check PostgreSQL availability:**
```kotlin
suspend fun checkDatabaseHealth(): Boolean {
    return try {
        DatabaseFactory.dbQuery {
            exec("SELECT 1") { }
        }
        true
    } catch (e: Exception) {
        logger.error("Database health check failed", e)
        false
    }
}
```

### Logging

#### Application Logs

**DatabaseFactory:**
```
INFO  c.r.p.sql.DatabaseFactory - Initializing database connection pool
INFO  c.r.p.sql.DatabaseFactory - Database connection pool initialized successfully
```

**FlywayMigrator:**
```
INFO  c.r.p.sql.FlywayMigrator - Running Flyway migrations
INFO  c.r.p.sql.FlywayMigrator - Flyway migration completed: 2 migrations executed
```

**StorageFactory:**
```
INFO  c.r.p.sql.StorageFactory - Attempting to use PostgreSQL for persistence storage...
INFO  c.r.p.sql.StorageFactory - Using JSON-based persistence storage (PostgreSQL disabled)
```

**Storage Operations:**
```
DEBUG c.r.p.sql.PostgresAssistantStorage - Saved assistant: test-1
DEBUG c.r.p.sql.PostgresPersistenceStorage - Loaded session: session-1 (15 messages)
```

#### PostgreSQL Logs

**View logs:**
```bash
docker-compose logs -f postgres
```

**Common log entries:**
```
LOG:  database system is ready to accept connections
LOG:  checkpoint complete
ERROR:  duplicate key value violates unique constraint "assistants_pkey"
```

### Metrics

**Key Metrics to Monitor:**

1. **Connection Pool:**
   - Active connections
   - Idle connections
   - Wait time for connection
   - Connection acquisition time

2. **Query Performance:**
   - Average query time
   - Slow queries (> 100ms)
   - Query count per second

3. **Database Size:**
   - Total database size
   - Table sizes
   - Index sizes

**Query for metrics:**
```sql
-- Database size
SELECT pg_size_pretty(pg_database_size('researchai'));

-- Table sizes
SELECT
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;

-- Slow queries
SELECT
    query,
    calls,
    mean_exec_time,
    max_exec_time
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 10;
```

---

## Backup & Recovery

### Backup Strategy

#### 1. Docker Volume Backup

**Backup postgres_data volume:**
```bash
docker run --rm \
  -v researchai_postgres_data:/data \
  -v $(pwd)/backups:/backup \
  alpine tar czf /backup/postgres-backup-$(date +%Y%m%d-%H%M%S).tar.gz -C /data .
```

**Restore volume:**
```bash
docker run --rm \
  -v researchai_postgres_data:/data \
  -v $(pwd)/backups:/backup \
  alpine tar xzf /backup/postgres-backup-YYYYMMDD-HHMMSS.tar.gz -C /data
```

#### 2. PostgreSQL Dump

**Full database backup:**
```bash
docker exec researchai-postgres pg_dump -U researchai -Fc researchai > backup.dump
```

**Restore from dump:**
```bash
docker exec -i researchai-postgres pg_restore -U researchai -d researchai < backup.dump
```

**Schema-only backup:**
```bash
docker exec researchai-postgres pg_dump -U researchai --schema-only researchai > schema.sql
```

#### 3. Automated Backups

**Cron job (daily backup at 2 AM):**
```bash
0 2 * * * /path/to/backup-script.sh
```

**backup-script.sh:**
```bash
#!/bin/bash
BACKUP_DIR="/backups/postgresql"
DATE=$(date +%Y%m%d-%H%M%S)

mkdir -p $BACKUP_DIR

docker exec researchai-postgres pg_dump -U researchai -Fc researchai \
  > $BACKUP_DIR/researchai-$DATE.dump

# Keep only last 7 days
find $BACKUP_DIR -name "*.dump" -mtime +7 -delete
```

### Disaster Recovery

#### Scenario 1: Database Corruption

**Steps:**

1. **Stop application:**
```bash
docker-compose stop claude-chat
```

2. **Restore from latest backup:**
```bash
docker exec -i researchai-postgres pg_restore -U researchai -d researchai --clean < latest.dump
```

3. **Verify data:**
```bash
docker exec -it researchai-postgres psql -U researchai -d researchai

SELECT COUNT(*) FROM chat_sessions;
SELECT COUNT(*) FROM assistants;
```

4. **Restart application:**
```bash
docker-compose start claude-chat
```

#### Scenario 2: Complete Data Loss

**Steps:**

1. **Recreate database:**
```bash
docker-compose down -v
docker-compose up -d postgres
```

2. **Wait for healthcheck:**
```bash
docker-compose ps
# Wait until postgres is "healthy"
```

3. **Restore from backup:**
```bash
docker exec -i researchai-postgres pg_restore -U researchai -d researchai < latest.dump
```

4. **Start application:**
```bash
docker-compose up -d claude-chat
```

#### Scenario 3: Rollback to JSON Storage

**Steps:**

1. **Disable PostgreSQL:**
```bash
# In .env
ENABLE_POSTGRES=false
```

2. **Ensure JSON data exists:**
```bash
ls -la data/sessions/
ls -la data/assistants/
```

3. **Restart application:**
```bash
docker-compose restart claude-chat
```

4. **Verify JSON storage active:**
```
INFO  c.r.p.sql.StorageFactory - Using JSON-based persistence storage (PostgreSQL disabled)
```

---

## Security

### Access Control

#### Database Credentials

**Default Credentials (Development):**
```
User: researchai
Password: researchai_dev_password
```

**Production Best Practices:**

1. **Use strong passwords:**
```bash
# Generate secure password
openssl rand -base64 32
```

2. **Store in secrets manager:**
```bash
# Example: AWS Secrets Manager
DATABASE_PASSWORD=$(aws secretsmanager get-secret-value \
  --secret-id researchai/db-password \
  --query SecretString --output text)
```

3. **Rotate credentials regularly:**
```sql
ALTER USER researchai WITH PASSWORD 'new-secure-password';
```

#### Network Security

**Docker Network Isolation:**
```yaml
networks:
  claude-network:
    driver: bridge
```

**Restrict external access:**
```yaml
# Don't expose postgres port to host in production
# Remove this:
ports:
  - "5432:5432"

# Keep only internal network access
```

### Data Protection

#### SSL/TLS Connection

**Enable SSL in PostgreSQL:**
```bash
# In postgresql.conf
ssl = on
ssl_cert_file = '/var/lib/postgresql/server.crt'
ssl_key_file = '/var/lib/postgresql/server.key'
```

**Connect with SSL:**
```kotlin
DatabaseConfig(
    url = "jdbc:postgresql://postgres:5432/researchai?ssl=true&sslmode=require"
)
```

#### Data Encryption

**At Rest:**
- Use encrypted Docker volumes (LUKS, dm-crypt)
- PostgreSQL pgcrypto extension for column-level encryption

**In Transit:**
- SSL/TLS for all connections
- VPN for remote database access

### Audit Logging

**Enable PostgreSQL audit log:**
```bash
# In postgresql.conf
log_statement = 'all'
log_connections = on
log_disconnections = on
log_duration = on
```

**View audit logs:**
```bash
docker-compose logs postgres | grep "AUDIT"
```

---

## Appendix

### Dependencies

**build.gradle.kts:**
```kotlin
dependencies {
    // Exposed ORM
    implementation("org.jetbrains.exposed:exposed-core:0.55.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.55.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.55.0")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.55.0")
    implementation("org.jetbrains.exposed:exposed-json:0.55.0")

    // PostgreSQL
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.zaxxer:HikariCP:6.2.1")

    // Flyway
    implementation("org.flywaydb:flyway-core:11.1.0")
    implementation("org.flywaydb:flyway-database-postgresql:11.1.0")

    // Testing
    testImplementation("com.h2database:h2:2.2.224")
    testImplementation("org.testcontainers:postgresql:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
}
```

### File Structure

```
ResearchAI/
├── src/
│   ├── main/
│   │   ├── kotlin/com/researchai/
│   │   │   ├── persistence/
│   │   │   │   ├── sql/
│   │   │   │   │   ├── DatabaseConfig.kt
│   │   │   │   │   ├── DatabaseFactory.kt
│   │   │   │   │   ├── FlywayMigrator.kt
│   │   │   │   │   ├── StorageFactory.kt
│   │   │   │   │   ├── PostgresAssistantStorage.kt
│   │   │   │   │   ├── PostgresPersistenceStorage.kt
│   │   │   │   │   ├── PostgresTaskStorage.kt
│   │   │   │   │   ├── PostgresPipelineStorage.kt
│   │   │   │   │   ├── PostgresExecutionStorage.kt
│   │   │   │   │   ├── PostgresUserStorage.kt
│   │   │   │   │   ├── PostgresPreferencesStorage.kt
│   │   │   │   │   └── tables/
│   │   │   │   │       ├── AssistantsTable.kt
│   │   │   │   │       ├── AssistantPipelinesTable.kt
│   │   │   │   │       ├── ScheduledTasksTable.kt
│   │   │   │   │       ├── ChatSessionsTable.kt
│   │   │   │   │       ├── PipelineExecutionsTable.kt
│   │   │   │   │       ├── UsersTable.kt
│   │   │   │   │       └── UserPreferencesTable.kt
│   │   └── resources/
│   │       └── db/
│   │           └── migration/
│   │               ├── V1__create_initial_schema.sql
│   │               └── V2__add_indexes.sql
│   └── test/
│       └── kotlin/com/researchai/
│           └── persistence/sql/
│               ├── TestDatabaseFactory.kt
│               ├── DatabaseConfigTest.kt
│               ├── StorageFactoryTest.kt
│               ├── PostgresAssistantStorageTest.kt
│               └── PostgreSQLIntegrationTest.kt
├── Documents/
│   └── POSTGRESQL_ARCHITECTURE.md
├── .env
├── docker-compose.yml
└── build.gradle.kts
```

### Testing Strategy

**Test Pyramid:**

```
              ┌─────────────────┐
              │  Integration    │  ← 6 tests (Testcontainers)
              │  Tests (Slow)   │     Real PostgreSQL 17
              └─────────────────┘
                     ▲
                     │
         ┌───────────────────────┐
         │   Integration Tests   │  ← 10 tests (H2)
         │   (Fast)              │     PostgreSQL compatibility mode
         └───────────────────────┘
                     ▲
                     │
            ┌─────────────────┐
            │   Unit Tests    │  ← 13 tests
            │   (Fastest)     │     DatabaseConfig, StorageFactory
            └─────────────────┘
```

**Total: 29 tests, 100% pass rate**

### Useful Commands

**Development:**
```bash
# Start PostgreSQL only
docker-compose up -d postgres

# Run Flyway migrations
./gradlew run

# Clean database (development only!)
docker-compose down -v
```

**Debugging:**
```bash
# Connect to PostgreSQL shell
docker exec -it researchai-postgres psql -U researchai -d researchai

# View running queries
SELECT pid, query, state FROM pg_stat_activity WHERE state != 'idle';

# Kill long-running query
SELECT pg_terminate_backend(pid);
```

**Monitoring:**
```bash
# Connection count
SELECT count(*) FROM pg_stat_activity;

# Database size
SELECT pg_size_pretty(pg_database_size('researchai'));

# Table row counts
SELECT
    schemaname,
    tablename,
    n_live_tup
FROM pg_stat_user_tables;
```

---

## Changelog

### Version 1.0 (2025-11-24)

**Added:**
- PostgreSQL 17 integration
- Hybrid storage strategy (PostgreSQL + JSON fallback)
- 7 storage implementations
- 7 Exposed table definitions
- 2 Flyway migrations (V1, V2)
- 16 performance indexes
- 29 comprehensive tests
- POSTGRESQL_ARCHITECTURE.md documentation

**Status:** Production Ready

---

## References

- [Exposed ORM Documentation](https://github.com/JetBrains/Exposed)
- [Flyway Documentation](https://flywaydb.org/documentation/)
- [HikariCP Configuration](https://github.com/brettwooldridge/HikariCP)
- [PostgreSQL 17 Documentation](https://www.postgresql.org/docs/17/)
- [Docker Compose](https://docs.docker.com/compose/)
- [Testcontainers](https://www.testcontainers.org/)

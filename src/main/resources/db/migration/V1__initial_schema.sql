-- V1: Initial database schema for ResearchAI
-- Creates all tables and enums for PostgreSQL persistence

-- ========================================
-- ENUMS
-- ========================================

CREATE TYPE message_role AS ENUM ('user', 'assistant', 'system');
CREATE TYPE provider_type AS ENUM ('CLAUDE', 'OPENAI', 'HUGGINGFACE', 'GEMINI', 'CUSTOM');
CREATE TYPE oauth_provider AS ENUM ('GOOGLE', 'GITHUB', 'MICROSOFT');
CREATE TYPE response_format AS ENUM ('PLAIN_TEXT', 'MARKDOWN', 'JSON');
CREATE TYPE pipeline_status AS ENUM ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'PARTIAL');

-- ========================================
-- TABLES
-- ========================================

-- Users table (OAuth authentication)
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

-- User preferences table
CREATE TABLE user_preferences (
    id SERIAL PRIMARY KEY,
    user_id TEXT REFERENCES users(id) ON DELETE CASCADE,
    provider_id TEXT NOT NULL,  -- AI provider (not FK)
    model TEXT NOT NULL,
    temperature DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    max_tokens INTEGER NOT NULL DEFAULT 4096,
    format response_format NOT NULL DEFAULT 'PLAIN_TEXT',
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id)
);

-- Assistants table (AI personas)
CREATE TABLE assistants (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    system_prompt TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Assistant pipelines table (multi-assistant workflows)
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

-- Scheduled tasks table (recurring chat tasks)
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

-- Chat sessions table (conversation history)
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
    -- Mutual exclusivity constraint: session can have only one of assistantId, scheduledTaskId, or pipelineId
    CONSTRAINT check_mutual_exclusivity CHECK (
        (assistant_id IS NOT NULL)::int +
        (scheduled_task_id IS NOT NULL)::int +
        (pipeline_id IS NOT NULL)::int <= 1
    )
);

-- Pipeline executions table (execution history)
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

-- ========================================
-- COMMENTS
-- ========================================

COMMENT ON TABLE users IS 'OAuth authenticated users';
COMMENT ON TABLE user_preferences IS 'User-specific AI provider preferences';
COMMENT ON TABLE assistants IS 'AI assistant personas with custom system prompts';
COMMENT ON TABLE assistant_pipelines IS 'Multi-assistant workflow configurations';
COMMENT ON TABLE scheduled_tasks IS 'Recurring automated chat tasks';
COMMENT ON TABLE chat_sessions IS 'Conversation history with messages stored as JSONB';
COMMENT ON TABLE pipeline_executions IS 'History of pipeline executions';

COMMENT ON COLUMN chat_sessions.messages IS 'Array of messages stored as JSONB for efficient querying';
COMMENT ON COLUMN chat_sessions.archived_messages IS 'Compressed/archived messages from compression operations';
COMMENT ON COLUMN chat_sessions.compression_config IS 'Configuration for automatic message compression';

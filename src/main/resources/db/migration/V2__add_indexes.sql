-- V2: Add performance indexes
-- Creates indexes for frequently queried columns

-- ========================================
-- USERS TABLE INDEXES
-- ========================================

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_provider ON users(provider, provider_id);

-- ========================================
-- ASSISTANTS TABLE INDEXES
-- ========================================

CREATE INDEX idx_assistants_is_system ON assistants(is_system);
CREATE INDEX idx_assistants_name ON assistants(name);

-- ========================================
-- PIPELINES TABLE INDEXES
-- ========================================

CREATE INDEX idx_pipelines_name ON assistant_pipelines(name);
CREATE INDEX idx_pipelines_provider ON assistant_pipelines(provider_id);

-- ========================================
-- CHAT SESSIONS TABLE INDEXES
-- ========================================

-- Foreign key indexes
CREATE INDEX idx_sessions_assistant ON chat_sessions(assistant_id);
CREATE INDEX idx_sessions_task ON chat_sessions(scheduled_task_id);
CREATE INDEX idx_sessions_pipeline ON chat_sessions(pipeline_id);

-- Query optimization indexes
CREATE INDEX idx_sessions_last_accessed ON chat_sessions(last_accessed_at DESC);
CREATE INDEX idx_sessions_created_at ON chat_sessions(created_at DESC);

-- JSONB GIN index for full-text search in messages
CREATE INDEX idx_sessions_messages ON chat_sessions USING GIN (messages);

-- ========================================
-- PIPELINE EXECUTIONS TABLE INDEXES
-- ========================================

CREATE INDEX idx_executions_pipeline ON pipeline_executions(pipeline_id);
CREATE INDEX idx_executions_session ON pipeline_executions(session_id);
CREATE INDEX idx_executions_status ON pipeline_executions(status);
CREATE INDEX idx_executions_start_time ON pipeline_executions(start_time DESC);

-- ========================================
-- COMMENTS
-- ========================================

COMMENT ON INDEX idx_sessions_messages IS 'GIN index for efficient JSONB queries on messages array';
COMMENT ON INDEX idx_sessions_last_accessed IS 'Optimizes queries for recent sessions list';

-- V3: Add RAG tests table
-- Stores RAG test cases with queries as JSONB for structured storage

-- ========================================
-- RAG TESTS TABLE
-- ========================================

CREATE TABLE rag_tests (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    queries JSONB NOT NULL DEFAULT '[]',
    evaluation_metrics JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ========================================
-- INDEXES
-- ========================================

CREATE INDEX idx_rag_tests_name ON rag_tests(name);
CREATE INDEX idx_rag_tests_created_at ON rag_tests(created_at DESC);

-- GIN index for efficient JSONB queries on queries array
CREATE INDEX idx_rag_tests_queries ON rag_tests USING GIN (queries);

-- ========================================
-- COMMENTS
-- ========================================

COMMENT ON TABLE rag_tests IS 'RAG test cases for verifying search quality';
COMMENT ON COLUMN rag_tests.queries IS 'Array of test queries stored as JSONB with fields: id, query, explanation, scenario, expectedTopResult, expectedChunkKeywords, rankingTrap';
COMMENT ON COLUMN rag_tests.evaluation_metrics IS 'Optional evaluation metrics configuration';
COMMENT ON INDEX idx_rag_tests_queries IS 'GIN index for efficient JSONB queries on queries array';

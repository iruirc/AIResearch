-- Migration to remove GEMINI and CUSTOM, add OLLAMA to provider_type enum
-- ========================================

-- Step 1: Add OLLAMA to the enum (if not already present)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_enum e
        JOIN pg_type t ON e.enumtypid = t.oid
        WHERE t.typname = 'provider_type'
        AND e.enumlabel = 'OLLAMA'
    ) THEN
        ALTER TYPE provider_type ADD VALUE 'OLLAMA';
    END IF;
END$$;

-- Step 2: Update any existing GEMINI and CUSTOM references to CLAUDE
-- (This prevents orphaned data if GEMINI or CUSTOM were used)
UPDATE scheduled_tasks SET provider_id = 'CLAUDE' WHERE provider_id IN ('GEMINI', 'CUSTOM');

-- Note: PostgreSQL does not support removing enum values directly.
-- The GEMINI and CUSTOM values remain in the enum but are no longer used in the application.
-- To fully remove them, a more complex migration with enum recreation would be required.
-- Since they're not causing issues and may have historical data, we leave them in place.

-- Step 3: Add comment documenting the deprecated values
COMMENT ON TYPE provider_type IS 'AI Provider types. Note: GEMINI and CUSTOM are deprecated and should not be used.';

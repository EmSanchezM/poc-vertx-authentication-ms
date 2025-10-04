-- Rollback script for V7__Add_case_insensitive_username_index.sql
-- Use this script if you need to revert the username indexing changes
-- WARNING: This will remove database-level username uniqueness enforcement

-- Remove check constraints
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_username_format;
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_username_not_reserved;

-- Drop the reserved username function
DROP FUNCTION IF EXISTS is_reserved_username(TEXT);

-- Drop the new indexes
DROP INDEX IF EXISTS idx_users_username_unique_ci;
DROP INDEX IF EXISTS idx_users_username_ci;

-- Recreate the original simple index on username
CREATE INDEX idx_users_username ON users(username);

-- Log rollback completion
DO $$
BEGIN
    RAISE NOTICE 'Username indexing migration rollback completed. Database-level constraints have been removed.';
END $$;
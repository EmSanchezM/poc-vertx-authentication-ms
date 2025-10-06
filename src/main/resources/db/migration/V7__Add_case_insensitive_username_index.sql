-- Migration to add case-insensitive unique index on username column
-- This migration supports the automatic username generation feature
-- Requirements: 4.1, 4.2

-- First, check for any duplicate usernames that would violate the new constraint
-- This query will help identify any existing data issues
DO $$
DECLARE
    duplicate_count INTEGER;
    rec RECORD;
BEGIN
    -- Count potential duplicates (case-insensitive)
    SELECT COUNT(*) INTO duplicate_count
    FROM (
        SELECT LOWER(username), COUNT(*)
        FROM users 
        WHERE username IS NOT NULL
        GROUP BY LOWER(username)
        HAVING COUNT(*) > 1
    ) duplicates;
    
    -- If duplicates exist, log them for manual resolution
    IF duplicate_count > 0 THEN
        RAISE NOTICE 'Found % groups of duplicate usernames (case-insensitive). Manual resolution may be required.', duplicate_count;
        
        -- Log the actual duplicates for reference
        FOR rec IN 
            SELECT LOWER(username) as lower_username, array_agg(username) as usernames, COUNT(*) as count
            FROM users 
            WHERE username IS NOT NULL
            GROUP BY LOWER(username)
            HAVING COUNT(*) > 1
        LOOP
            RAISE NOTICE 'Duplicate usernames for "%": % (count: %)', rec.lower_username, rec.usernames, rec.count;
        END LOOP;
    ELSE
        RAISE NOTICE 'No duplicate usernames found. Safe to proceed with case-insensitive unique constraint.';
    END IF;
END $$;

-- Remove the existing regular index on username
DROP INDEX IF EXISTS idx_users_username;

-- Create a case-insensitive unique index on username
-- This will enforce uniqueness at the database level regardless of case
CREATE UNIQUE INDEX idx_users_username_unique_ci 
ON users (LOWER(username)) 
WHERE username IS NOT NULL;

-- Create a regular index for performance on username lookups
-- This supports both case-sensitive and case-insensitive queries efficiently
CREATE INDEX idx_users_username_ci 
ON users (LOWER(username)) 
WHERE username IS NOT NULL;

-- Add a function to check for reserved usernames
-- This will be used by the application but also provides database-level protection
CREATE OR REPLACE FUNCTION is_reserved_username(username_to_check TEXT)
RETURNS BOOLEAN AS $$
DECLARE
    reserved_words TEXT[] := ARRAY[
        'admin', 'root', 'system', 'api', 'www', 'mail', 'ftp',
        'test', 'demo', 'guest', 'anonymous', 'null', 'undefined',
        'support', 'help', 'info', 'contact', 'about', 'terms',
        'privacy', 'security', 'login', 'logout', 'register', 'signup'
    ];
BEGIN
    RETURN LOWER(username_to_check) = ANY(reserved_words);
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Update any existing reserved usernames before adding the constraint
-- Change 'admin' to 'systemadmin' to avoid conflicts with reserved words
UPDATE users 
SET username = 'systemadmin' 
WHERE username = 'admin';

-- Update any other reserved usernames that might exist
UPDATE users 
SET username = 'system-' || username 
WHERE username IS NOT NULL 
  AND is_reserved_username(username) 
  AND username != 'systemadmin'; -- Avoid double-prefixing

-- Add a check constraint to ensure username format compliance
-- This enforces the business rules at the database level
ALTER TABLE users 
ADD CONSTRAINT chk_username_format 
CHECK (
    username IS NULL OR (
        LENGTH(username) >= 3 AND 
        LENGTH(username) <= 64 AND
        username ~ '^[a-z0-9.-]+$' AND
        username !~ '^[.-]' AND
        username !~ '[.-]$' AND
        username !~ '[.-]{2,}'
    )
);

-- Add a check constraint for reserved usernames
ALTER TABLE users 
ADD CONSTRAINT chk_username_not_reserved 
CHECK (username IS NULL OR NOT is_reserved_username(username));

-- Update any existing usernames to lowercase to ensure consistency
-- This is safe because we're enforcing case-insensitive uniqueness
UPDATE users 
SET username = LOWER(username) 
WHERE username IS NOT NULL AND username != LOWER(username);

-- Add comments for documentation
COMMENT ON INDEX idx_users_username_unique_ci IS 'Case-insensitive unique index on username to prevent duplicates regardless of case';
COMMENT ON INDEX idx_users_username_ci IS 'Case-insensitive index on username for efficient lookups';
COMMENT ON CONSTRAINT chk_username_format IS 'Ensures username meets format requirements: 3-64 chars, lowercase alphanumeric with dots/hyphens, no leading/trailing/consecutive special chars';
COMMENT ON CONSTRAINT chk_username_not_reserved IS 'Prevents use of reserved system usernames';
COMMENT ON FUNCTION is_reserved_username(TEXT) IS 'Function to check if a username is in the reserved words list';

-- Log completion
DO $$
BEGIN
    RAISE NOTICE 'Username indexing migration completed successfully. Case-insensitive unique constraint and format validation are now enforced at database level.';
END $$;
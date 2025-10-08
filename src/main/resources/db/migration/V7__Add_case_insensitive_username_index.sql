-- Migration to add case-insensitive unique index on username column
-- This migration supports the automatic username generation feature

-- First, check for any duplicate usernames that would violate the new constraint
DO $$
DECLARE
    duplicate_count INTEGER;
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
    ELSE
        RAISE NOTICE 'No duplicate usernames found. Safe to proceed with case-insensitive unique constraint.';
    END IF;
END $$;

-- Remove the existing regular index on username
DROP INDEX IF EXISTS idx_users_username;

-- Create a case-insensitive unique index on username
CREATE UNIQUE INDEX idx_users_username_unique_ci 
ON users (LOWER(username)) 
WHERE username IS NOT NULL;

-- Create a regular index for performance on username lookups
CREATE INDEX idx_users_username_ci 
ON users (LOWER(username)) 
WHERE username IS NOT NULL;

-- Add a function to check for reserved usernames
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
UPDATE users 
SET username = 'systemadmin' 
WHERE username = 'admin';

-- Update any other reserved usernames that might exist
UPDATE users 
SET username = 'system-' || username 
WHERE username IS NOT NULL 
  AND is_reserved_username(username) 
  AND username != 'systemadmin';

-- Add a check constraint to ensure username format compliance
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
UPDATE users 
SET username = LOWER(username) 
WHERE username IS NOT NULL AND username != LOWER(username);

-- Log completion
DO $$
BEGIN
    RAISE NOTICE 'Username indexing migration completed successfully.';
END $$;
-- Test script to validate the username migration
-- This script can be run against a test database to verify the migration works correctly

-- Test 1: Create test data with potential case conflicts
INSERT INTO users (username, email, password_hash, first_name, last_name) VALUES
('TestUser', 'test1@example.com', 'hash1', 'Test', 'User'),
('testuser', 'test2@example.com', 'hash2', 'Test', 'User2'),
('TESTUSER', 'test3@example.com', 'hash3', 'Test', 'User3');

-- Test 2: Check for duplicates before migration
SELECT 
    LOWER(username) as lower_username, 
    array_agg(username) as usernames, 
    COUNT(*) as count
FROM users 
WHERE username IS NOT NULL
GROUP BY LOWER(username)
HAVING COUNT(*) > 1;

-- Test 3: After running the migration, verify the unique constraint works
-- This should fail if run after the migration:
-- INSERT INTO users (username, email, password_hash, first_name, last_name) VALUES
-- ('elvin.sanchez', 'test4@example.com', 'hash4', 'Elvin', 'Sanchez'),
-- ('ELVIN.SANCHEZ', 'test5@example.com', 'hash5', 'Elvin', 'Sanchez');

-- Test 4: Verify format constraints work
-- These should fail after migration:
-- INSERT INTO users (username, email, password_hash, first_name, last_name) VALUES
-- ('ab', 'test6@example.com', 'hash6', 'Test', 'User'), -- Too short
-- ('.invalid', 'test7@example.com', 'hash7', 'Test', 'User'), -- Starts with dot
-- ('invalid.', 'test8@example.com', 'hash8', 'Test', 'User'), -- Ends with dot
-- ('invalid..user', 'test9@example.com', 'hash9', 'Test', 'User'), -- Consecutive dots
-- ('admin', 'test10@example.com', 'hash10', 'Test', 'User'); -- Reserved word

-- Test 5: Verify valid usernames still work
-- These should succeed after migration:
-- INSERT INTO users (username, email, password_hash, first_name, last_name) VALUES
-- ('valid.user', 'test11@example.com', 'hash11', 'Valid', 'User'),
-- ('user123', 'test12@example.com', 'hash12', 'User', 'OneTwo'),
-- ('test-user', 'test13@example.com', 'hash13', 'Test', 'User');

-- Clean up test data
-- DELETE FROM users WHERE email LIKE 'test%@example.com';
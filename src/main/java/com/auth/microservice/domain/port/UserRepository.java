package com.auth.microservice.domain.port;

import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.model.Email;
import io.vertx.core.Future;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for User entity operations
 */
public interface UserRepository extends Repository<User, UUID> {
    
    /**
     * Find user by email address
     * @param email User email
     * @return Future containing optional user
     */
    Future<Optional<User>> findByEmail(Email email);
    
    /**
     * Find user by username
     * @param username Username
     * @return Future containing optional user
     */
    Future<Optional<User>> findByUsername(String username);
    
    /**
     * Find user with their roles loaded
     * @param userId User ID
     * @return Future containing optional user with roles
     */
    Future<Optional<User>> findByIdWithRoles(UUID userId);
    
    /**
     * Find user by email with their roles loaded
     * @param email User email
     * @return Future containing optional user with roles
     */
    Future<Optional<User>> findByEmailWithRoles(Email email);
    
    /**
     * Find user by username with their roles loaded
     * @param username Username
     * @return Future containing optional user with roles
     */
    Future<Optional<User>> findByUsernameWithRoles(String username);
    
    /**
     * Find all users with pagination
     * @param pagination Pagination parameters
     * @return Future containing paginated list of users
     */
    Future<List<User>> findAll(Pagination pagination);
    
    /**
     * Find active users only
     * @param pagination Pagination parameters
     * @return Future containing paginated list of active users
     */
    Future<List<User>> findActiveUsers(Pagination pagination);
    
    /**
     * Search users by name or email with pagination
     * @param searchTerm Search term to match against first name, last name, or email
     * @param pagination Pagination parameters
     * @param includeInactive Whether to include inactive users in search
     * @return Future containing paginated list of matching users
     */
    Future<List<User>> searchUsers(String searchTerm, Pagination pagination, boolean includeInactive);
    
    /**
     * Check if email already exists
     * @param email Email to check
     * @return Future containing boolean result
     */
    Future<Boolean> existsByEmail(Email email);
    
    /**
     * Check if username already exists
     * @param username Username to check
     * @return Future containing boolean result
     */
    Future<Boolean> existsByUsername(String username);
    
    /**
     * Count all users in the system
     * @return Future containing total user count
     */
    Future<Long> countAll();
    
    /**
     * Count active users in the system
     * @return Future containing active user count
     */
    Future<Long> countActive();
    
    /**
     * Count users created since a specific date
     * @param since Date to count from
     * @return Future containing count of users created since the date
     */
    Future<Long> countCreatedSince(java.time.OffsetDateTime since);
    
    /**
     * Check if username exists with case-insensitive comparison.
     * Used for username generation to ensure uniqueness regardless of case.
     * 
     * @param username Username to check (case will be ignored)
     * @return Future containing boolean result - true if username exists
     */
    Future<Boolean> existsByUsernameIgnoreCase(String username);
    
    /**
     * Find usernames that start with a given prefix.
     * Used for collision detection and suffix generation during username creation.
     * Results are ordered alphabetically to facilitate collision resolution.
     * 
     * @param usernamePrefix Prefix to search for (case-insensitive)
     * @param limit Maximum number of results to return
     * @return Future containing list of matching usernames
     */
    Future<List<String>> findUsernamesStartingWith(String usernamePrefix, int limit);
    
    /**
     * Save user with roles in a transactional manner.
     * This method ensures that both user data and role assignments are persisted atomically.
     * If any part of the operation fails, the entire transaction is rolled back.
     * 
     * @param user User entity with roles to save
     * @return Future containing saved user with roles
     */
    Future<User> saveWithRoles(User user);
}
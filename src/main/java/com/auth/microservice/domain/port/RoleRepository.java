package com.auth.microservice.domain.port;

import com.auth.microservice.domain.model.Role;
import io.vertx.core.Future;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Role entity operations
 */
public interface RoleRepository extends Repository<Role, UUID> {
    
    /**
     * Find role by name
     * @param name Role name
     * @return Future containing optional role
     */
    Future<Optional<Role>> findByName(String name);
    
    /**
     * Find role with permissions loaded
     * @param roleId Role ID
     * @return Future containing optional role with permissions
     */
    Future<Optional<Role>> findByIdWithPermissions(UUID roleId);
    
    /**
     * Find role by name with permissions loaded
     * @param name Role name
     * @return Future containing optional role with permissions
     */
    Future<Optional<Role>> findByNameWithPermissions(String name);
    
    /**
     * Find all roles for a specific user
     * @param userId User ID
     * @return Future containing list of user roles
     */
    Future<List<Role>> findByUserId(UUID userId);
    
    /**
     * Find all roles for a user with permissions loaded
     * @param userId User ID
     * @return Future containing list of user roles with permissions
     */
    Future<List<Role>> findByUserIdWithPermissions(UUID userId);
    
    /**
     * Find all roles with pagination
     * @param pagination Pagination parameters
     * @return Future containing paginated list of roles
     */
    Future<List<Role>> findAll(Pagination pagination);
    
    /**
     * Check if role name already exists
     * @param name Role name to check
     * @return Future containing boolean result
     */
    Future<Boolean> existsByName(String name);
    
    /**
     * Count all roles in the system
     * @return Future containing total role count
     */
    Future<Long> countAll();
    
    /**
     * Get role distribution (role name to user count mapping)
     * @return Future containing map of role names to user counts
     */
    Future<java.util.Map<String, Long>> getRoleDistribution();
}
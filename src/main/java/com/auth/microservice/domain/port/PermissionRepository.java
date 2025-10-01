package com.auth.microservice.domain.port;

import com.auth.microservice.domain.model.Permission;
import io.vertx.core.Future;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Repository interface for Permission entity operations
 */
public interface PermissionRepository extends Repository<Permission, UUID> {
    
    /**
     * Find permission by name
     * @param name Permission name
     * @return Future containing optional permission
     */
    Future<Optional<Permission>> findByName(String name);
    
    /**
     * Find permission by resource and action
     * @param resource Resource name
     * @param action Action name
     * @return Future containing optional permission
     */
    Future<Optional<Permission>> findByResourceAndAction(String resource, String action);
    
    /**
     * Find all permissions for a specific role
     * @param roleId Role ID
     * @return Future containing set of role permissions
     */
    Future<Set<Permission>> findByRoleId(UUID roleId);
    
    /**
     * Find all permissions for a specific user (through their roles)
     * @param userId User ID
     * @return Future containing set of user permissions
     */
    Future<Set<Permission>> findByUserId(UUID userId);
    
    /**
     * Find permissions by resource
     * @param resource Resource name
     * @return Future containing list of permissions for the resource
     */
    Future<List<Permission>> findByResource(String resource);
    
    /**
     * Find all permissions with pagination
     * @param pagination Pagination parameters
     * @return Future containing paginated list of permissions
     */
    Future<List<Permission>> findAll(Pagination pagination);
    
    /**
     * Check if permission name already exists
     * @param name Permission name to check
     * @return Future containing boolean result
     */
    Future<Boolean> existsByName(String name);
    
    /**
     * Check if permission with resource and action already exists
     * @param resource Resource name
     * @param action Action name
     * @return Future containing boolean result
     */
    Future<Boolean> existsByResourceAndAction(String resource, String action);
}
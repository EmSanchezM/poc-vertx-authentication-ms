package com.auth.microservice.application.handler;

import com.auth.microservice.application.query.GetRoleByIdQuery;
import com.auth.microservice.application.query.GetRolesQuery;
import com.auth.microservice.application.query.GetUserRolesQuery;
import com.auth.microservice.common.cqrs.QueryHandler;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.port.RoleRepository;
import com.auth.microservice.infrastructure.adapter.cache.RedisAuthCacheService;
import io.vertx.core.Future;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Base class for role-related query handlers.
 * Provides common functionality and intelligent caching strategies for role queries.
 */
public abstract class RoleQueryHandler {
    
    private final RoleRepository roleRepository;
    private final RedisAuthCacheService cacheService;
    
    protected RoleQueryHandler(RoleRepository roleRepository, RedisAuthCacheService cacheService) {
        this.roleRepository = roleRepository;
        this.cacheService = cacheService;
    }
    
    /**
     * Handle GetRolesQuery with intelligent caching
     */
    protected Future<List<Role>> handleGetRoles(GetRolesQuery query) {
        // Create cache key based on pagination and permissions flag
        String paginationKey = createPaginationKey(query);
        
        // Try cache first
        return cacheService.getCachedRolesList(paginationKey)
            .compose(cachedRoles -> {
                if (cachedRoles.isPresent()) {
                    return Future.succeededFuture(cachedRoles.get());
                } else {
                    // Load from database
                    Future<List<Role>> rolesFuture;
                    if (query.isIncludePermissions()) {
                        // Load roles with permissions - this is more expensive
                        rolesFuture = roleRepository.findAll(query.getPagination())
                            .compose(roles -> {
                                // For each role, load permissions if not already loaded
                                List<Future<Role>> roleWithPermissionsFutures = roles.stream()
                                    .map(role -> {
                                        if (role.getPermissions().isEmpty()) {
                                            return roleRepository.findByIdWithPermissions(role.getId())
                                                .map(optionalRole -> optionalRole.orElse(role));
                                        } else {
                                            return Future.succeededFuture(role);
                                        }
                                    })
                                    .toList();
                                
                                return Future.all(roleWithPermissionsFutures)
                                    .map(compositeFuture -> compositeFuture.<Role>list());
                            });
                    } else {
                        // Load roles without permissions - faster
                        rolesFuture = roleRepository.findAll(query.getPagination());
                    }
                    
                    // Cache the result and return
                    return rolesFuture.compose(roles -> 
                        cacheService.cacheRolesList(paginationKey, roles)
                            .map(v -> roles)
                            .recover(throwable -> Future.succeededFuture(roles)) // Continue even if caching fails
                    );
                }
            });
    }
    
    /**
     * Handle GetRoleByIdQuery with caching optimization
     */
    protected Future<Optional<Role>> handleGetRoleById(GetRoleByIdQuery query) {
        UUID roleId = query.getRoleId();
        
        // Try cache first
        return cacheService.getCachedRoleById(roleId)
            .compose(cachedRole -> {
                if (cachedRole.isPresent()) {
                    Optional<Role> role = cachedRole.get();
                    // Check if we need permissions and they're not cached
                    if (query.isIncludePermissions() && role.isPresent() && role.get().getPermissions().isEmpty()) {
                        // Load from database with permissions
                        return roleRepository.findByIdWithPermissions(roleId)
                            .compose(roleWithPermissions -> 
                                cacheService.cacheRoleById(roleId, roleWithPermissions)
                                    .map(v -> roleWithPermissions)
                                    .recover(throwable -> Future.succeededFuture(roleWithPermissions))
                            );
                    } else {
                        return Future.succeededFuture(role);
                    }
                } else {
                    // Load from database
                    Future<Optional<Role>> roleFuture;
                    if (query.isIncludePermissions()) {
                        roleFuture = roleRepository.findByIdWithPermissions(roleId);
                    } else {
                        roleFuture = roleRepository.findById(roleId);
                    }
                    
                    // Cache the result and return
                    return roleFuture.compose(role -> 
                        cacheService.cacheRoleById(roleId, role)
                            .map(v -> role)
                            .recover(throwable -> Future.succeededFuture(role)) // Continue even if caching fails
                    );
                }
            });
    }
    
    /**
     * Handle GetUserRolesQuery with optimized permission resolution
     */
    protected Future<List<Role>> handleGetUserRoles(GetUserRolesQuery query) {
        UUID userId = query.getTargetUserId();
        
        // Try cache first
        return cacheService.getCachedUserRoles(userId)
            .compose(cachedRoles -> {
                if (cachedRoles.isPresent()) {
                    List<Role> roles = cachedRoles.get();
                    // Check if we need permissions and they're not cached
                    if (query.isIncludePermissions() && !roles.isEmpty() && roles.get(0).getPermissions().isEmpty()) {
                        // Load from database with permissions
                        return roleRepository.findByUserIdWithPermissions(userId)
                            .compose(rolesWithPermissions -> 
                                cacheService.cacheUserRoles(userId, rolesWithPermissions)
                                    .map(v -> rolesWithPermissions)
                                    .recover(throwable -> Future.succeededFuture(rolesWithPermissions))
                            );
                    } else {
                        return Future.succeededFuture(roles);
                    }
                } else {
                    // Load from database
                    Future<List<Role>> rolesFuture;
                    if (query.isIncludePermissions()) {
                        rolesFuture = roleRepository.findByUserIdWithPermissions(userId);
                    } else {
                        rolesFuture = roleRepository.findByUserId(userId);
                    }
                    
                    // Cache the result and return
                    return rolesFuture.compose(roles -> 
                        cacheService.cacheUserRoles(userId, roles)
                            .map(v -> roles)
                            .recover(throwable -> Future.succeededFuture(roles)) // Continue even if caching fails
                    );
                }
            });
    }
    
    // Helper methods
    
    private String createPaginationKey(GetRolesQuery query) {
        return String.format("page_%d_size_%d_permissions_%s", 
            query.getPagination().getPage(),
            query.getPagination().getSize(),
            query.isIncludePermissions());
    }
}
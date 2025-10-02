package com.auth.microservice.application.handler;

import com.auth.microservice.application.query.GetUserByIdQuery;
import com.auth.microservice.application.query.GetUserProfileQuery;
import com.auth.microservice.application.query.GetUsersQuery;
import com.auth.microservice.application.result.UserProfile;
import com.auth.microservice.common.cqrs.QueryHandler;
import com.auth.microservice.domain.model.Permission;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.port.UserRepository;
import com.auth.microservice.infrastructure.adapter.cache.RedisAuthCacheService;
import io.vertx.core.Future;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Base class for user-related query handlers.
 * Provides common functionality and caching strategies for user queries.
 */
public abstract class UserQueryHandler {
    
    protected final UserRepository userRepository;
    protected final RedisAuthCacheService cacheService;
    
    protected UserQueryHandler(UserRepository userRepository, RedisAuthCacheService cacheService) {
        this.userRepository = userRepository;
        this.cacheService = cacheService;
    }
    
    // Protected helper methods for subclasses
    
    protected Future<List<User>> handlePaginatedQuery(GetUsersQuery query) {
        if (query.isIncludeInactive()) {
            // Include both active and inactive users
            return userRepository.findAll(query.getPagination());
        } else {
            // Only active users
            return userRepository.findActiveUsers(query.getPagination());
        }
    }
    
    protected Future<Set<Permission>> loadUserPermissions(User user) {
        // Try cache first
        return cacheService.getCachedUserPermissions(user.getId())
            .compose(cachedPermissions -> {
                if (cachedPermissions.isPresent()) {
                    return Future.succeededFuture(cachedPermissions.get());
                } else {
                    // Load from user's roles
                    Set<Permission> permissions = user.getAllPermissions();
                    
                    // Cache the result
                    return cacheService.cacheUserPermissions(user.getId(), permissions)
                        .map(v -> permissions)
                        .recover(throwable -> Future.succeededFuture(permissions)); // Continue even if caching fails
                }
            });
    }
    
    protected UserProfile createUserProfile(User user, Set<Permission> permissions) {
        Set<String> roleNames = user.getRoles().stream()
            .map(Role::getName)
            .collect(Collectors.toSet());
            
        Set<String> permissionNames = permissions.stream()
            .map(Permission::getName)
            .collect(Collectors.toSet());
        
        return new UserProfile(
            user.getId(),
            user.getUsername(),
            user.getEmail().getValue(),
            user.getFirstName(),
            user.getLastName(),
            user.isActive(),
            user.getCreatedAt(),
            user.getUpdatedAt(),
            roleNames,
            permissionNames
        );
    }
}
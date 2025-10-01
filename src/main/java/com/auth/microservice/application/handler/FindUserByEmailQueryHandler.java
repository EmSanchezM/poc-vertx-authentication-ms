package com.auth.microservice.application.handler;

import com.auth.microservice.application.query.CheckPermissionQuery;
import com.auth.microservice.application.query.FindUserByEmailQuery;
import com.auth.microservice.application.query.GetUserPermissionsQuery;
import com.auth.microservice.common.cqrs.QueryHandler;
import com.auth.microservice.domain.model.Permission;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.port.PermissionRepository;
import com.auth.microservice.domain.port.UserRepository;
import com.auth.microservice.infrastructure.adapter.cache.RedisAuthCacheService;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.util.Optional;
import java.util.Set;

/**
 * Query handler for FindUserByEmailQuery.
 * Implements caching strategies for optimal read performance.
 */
public class FindUserByEmailQueryHandler implements QueryHandler<FindUserByEmailQuery, Optional<User>> {
    
    private final UserRepository userRepository;
    private final RedisAuthCacheService cacheService;
    
    public FindUserByEmailQueryHandler(UserRepository userRepository, 
                                      RedisAuthCacheService cacheService) {
        this.userRepository = userRepository;
        this.cacheService = cacheService;
    }
    
    /**
     * Handle FindUserByEmailQuery with caching
     */
    public Future<Optional<User>> handle(FindUserByEmailQuery query) {
        String email = query.getEmail().getValue();
        
        // Try cache first
        return cacheService.getCachedUserByEmail(email)
            .recover(throwable -> {
                // Treat cache errors as cache miss
                return Future.succeededFuture(Optional.empty());
            })
            .compose(cachedResult -> {
                if (cachedResult.isPresent()) {
                    // Cache hit
                    Optional<User> user = cachedResult.get();
                    if (user.isPresent() && (query.isIncludeRoles() || query.isIncludePermissions())) {
                        // Need to load roles/permissions separately
                        return loadUserWithRolesIfNeeded(user.get(), query.isIncludeRoles());
                    }
                    return Future.succeededFuture(user);
                } else {
                    // Cache miss - load from database
                    return loadUserFromDatabase(query)
                        .compose(user -> {
                            // Cache the result
                            return cacheService.cacheUserByEmail(email, user)
                                .map(v -> user)
                                .recover(throwable -> Future.succeededFuture(user)); // Continue even if caching fails
                        });
                }
            });
    }
    

    
    // Private helper methods
    
    private Future<Optional<User>> loadUserFromDatabase(FindUserByEmailQuery query) {
        if (query.isIncludeRoles()) {
            return userRepository.findByEmailWithRoles(query.getEmail());
        } else {
            return userRepository.findByEmail(query.getEmail());
        }
    }
    
    private Future<Optional<User>> loadUserWithRolesIfNeeded(User user, boolean includeRoles) {
        if (includeRoles) {
            return userRepository.findByIdWithRoles(user.getId());
        } else {
            return Future.succeededFuture(Optional.of(user));
        }
    }
    

    
    @Override
    @SuppressWarnings("unchecked")
    public Class<FindUserByEmailQuery> getQueryType() {
        return FindUserByEmailQuery.class;
    }
}
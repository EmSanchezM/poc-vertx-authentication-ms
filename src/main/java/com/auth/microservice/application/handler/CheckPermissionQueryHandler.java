package com.auth.microservice.application.handler;

import com.auth.microservice.application.query.CheckPermissionQuery;
import com.auth.microservice.common.cqrs.QueryHandler;
import com.auth.microservice.domain.port.PermissionRepository;
import com.auth.microservice.infrastructure.adapter.cache.RedisAuthCacheService;
import io.vertx.core.Future;

import java.util.Optional;

/**
 * Query handler for CheckPermissionQuery.
 * Checks if a user has a specific permission with caching support.
 */
public class CheckPermissionQueryHandler implements QueryHandler<CheckPermissionQuery, Boolean> {
    
    private final PermissionRepository permissionRepository;
    private final RedisAuthCacheService cacheService;
    
    public CheckPermissionQueryHandler(PermissionRepository permissionRepository,
                                      RedisAuthCacheService cacheService) {
        this.permissionRepository = permissionRepository;
        this.cacheService = cacheService;
    }
    
    @Override
    public Future<Boolean> handle(CheckPermissionQuery query) {
        String permissionKey = createPermissionKey(query);
        
        if (query.isUseCache()) {
            // Try cache first
            return cacheService.getCachedPermissionCheck(query.getTargetUserId(), permissionKey)
                .recover(throwable -> {
                    // Treat cache errors as cache miss
                    return Future.succeededFuture(Optional.empty());
                })
                .compose(cachedResult -> {
                    if (cachedResult.isPresent()) {
                        return Future.succeededFuture(cachedResult.get());
                    } else {
                        // Cache miss - check permission
                        return checkPermissionInDatabase(query)
                            .compose(hasPermission -> {
                                // Cache the result
                                return cacheService.cachePermissionCheck(query.getTargetUserId(), permissionKey, hasPermission)
                                    .map(v -> hasPermission)
                                    .recover(throwable -> Future.succeededFuture(hasPermission));
                            });
                    }
                });
        } else {
            // Skip cache
            return checkPermissionInDatabase(query);
        }
    }
    
    private Future<Boolean> checkPermissionInDatabase(CheckPermissionQuery query) {
        if (query.isCheckByName()) {
            return permissionRepository.findByUserId(query.getTargetUserId())
                .map(permissions -> permissions.stream()
                    .anyMatch(permission -> permission.getName().equals(query.getPermissionName())));
        } else if (query.isCheckByResourceAction()) {
            return permissionRepository.findByUserId(query.getTargetUserId())
                .map(permissions -> permissions.stream()
                    .anyMatch(permission -> permission.matches(query.getResource(), query.getAction())));
        } else {
            return Future.failedFuture(new IllegalArgumentException("Invalid permission check query"));
        }
    }
    
    private String createPermissionKey(CheckPermissionQuery query) {
        if (query.isCheckByName()) {
            return "name:" + query.getPermissionName();
        } else if (query.isCheckByResourceAction()) {
            return "resource:" + query.getResource() + ":action:" + query.getAction();
        } else {
            throw new IllegalArgumentException("Invalid permission check query");
        }
    }
    
    @Override
    public Class<CheckPermissionQuery> getQueryType() {
        return CheckPermissionQuery.class;
    }
}
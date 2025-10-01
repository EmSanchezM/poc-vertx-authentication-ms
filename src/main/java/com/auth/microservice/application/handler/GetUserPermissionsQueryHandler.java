package com.auth.microservice.application.handler;

import com.auth.microservice.application.query.GetUserPermissionsQuery;
import com.auth.microservice.common.cqrs.QueryHandler;
import com.auth.microservice.domain.model.Permission;
import com.auth.microservice.domain.port.PermissionRepository;
import com.auth.microservice.infrastructure.adapter.cache.RedisAuthCacheService;
import io.vertx.core.Future;

import java.util.Optional;
import java.util.Set;

/**
 * Query handler for GetUserPermissionsQuery.
 * Retrieves all permissions for a user with caching support.
 */
public class GetUserPermissionsQueryHandler implements QueryHandler<GetUserPermissionsQuery, Set<Permission>> {
    
    private final PermissionRepository permissionRepository;
    private final RedisAuthCacheService cacheService;
    
    public GetUserPermissionsQueryHandler(PermissionRepository permissionRepository,
                                         RedisAuthCacheService cacheService) {
        this.permissionRepository = permissionRepository;
        this.cacheService = cacheService;
    }
    
    @Override
    public Future<Set<Permission>> handle(GetUserPermissionsQuery query) {
        if (query.isUseCache()) {
            // Try cache first
            return cacheService.getCachedUserPermissions(query.getTargetUserId())
                .recover(throwable -> {
                    // Treat cache errors as cache miss
                    return Future.succeededFuture(Optional.empty());
                })
                .compose(cachedPermissions -> {
                    if (cachedPermissions.isPresent()) {
                        return Future.succeededFuture(cachedPermissions.get());
                    } else {
                        // Cache miss - load from database
                        return loadPermissionsFromDatabase(query)
                            .compose(permissions -> {
                                // Cache the result
                                return cacheService.cacheUserPermissions(query.getTargetUserId(), permissions)
                                    .map(v -> permissions)
                                    .recover(throwable -> Future.succeededFuture(permissions));
                            });
                    }
                });
        } else {
            // Skip cache
            return loadPermissionsFromDatabase(query);
        }
    }
    
    private Future<Set<Permission>> loadPermissionsFromDatabase(GetUserPermissionsQuery query) {
        return permissionRepository.findByUserId(query.getTargetUserId());
    }
    
    @Override
    public Class<GetUserPermissionsQuery> getQueryType() {
        return GetUserPermissionsQuery.class;
    }
}
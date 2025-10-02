package com.auth.microservice.application.handler;

import com.auth.microservice.application.query.GetUserRolesQuery;
import com.auth.microservice.common.cqrs.QueryHandler;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.port.RoleRepository;
import com.auth.microservice.infrastructure.adapter.cache.RedisAuthCacheService;
import io.vertx.core.Future;

import java.util.List;

/**
 * Query handler for GetUserRolesQuery.
 * Handles user role lookup with optimized permission resolution.
 */
public class GetUserRolesQueryHandler extends RoleQueryHandler implements QueryHandler<GetUserRolesQuery, List<Role>> {
    
    public GetUserRolesQueryHandler(RoleRepository roleRepository, RedisAuthCacheService cacheService) {
        super(roleRepository, cacheService);
    }
    
    @Override
    public Future<List<Role>> handle(GetUserRolesQuery query) {
        return handleGetUserRoles(query);
    }
    
    @Override
    public Class<GetUserRolesQuery> getQueryType() {
        return GetUserRolesQuery.class;
    }
}
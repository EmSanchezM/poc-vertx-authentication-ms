package com.auth.microservice.application.handler;

import com.auth.microservice.application.query.GetRolesQuery;
import com.auth.microservice.common.cqrs.QueryHandler;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.port.RoleRepository;
import com.auth.microservice.infrastructure.adapter.cache.RedisAuthCacheService;
import io.vertx.core.Future;

import java.util.List;

/**
 * Query handler for GetRolesQuery.
 * Handles role listing with pagination and intelligent caching.
 */
public class GetRolesQueryHandler extends RoleQueryHandler implements QueryHandler<GetRolesQuery, List<Role>> {
    
    public GetRolesQueryHandler(RoleRepository roleRepository, RedisAuthCacheService cacheService) {
        super(roleRepository, cacheService);
    }
    
    @Override
    public Future<List<Role>> handle(GetRolesQuery query) {
        return handleGetRoles(query);
    }
    
    @Override
    public Class<GetRolesQuery> getQueryType() {
        return GetRolesQuery.class;
    }
}
package com.auth.microservice.application.handler;

import com.auth.microservice.application.query.GetRoleByIdQuery;
import com.auth.microservice.common.cqrs.QueryHandler;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.port.RoleRepository;
import com.auth.microservice.infrastructure.adapter.cache.RedisAuthCacheService;
import io.vertx.core.Future;

import java.util.Optional;

/**
 * Query handler for GetRoleByIdQuery.
 * Handles role lookup by ID with caching optimization.
 */
public class GetRoleByIdQueryHandler extends RoleQueryHandler implements QueryHandler<GetRoleByIdQuery, Optional<Role>> {
    
    public GetRoleByIdQueryHandler(RoleRepository roleRepository, RedisAuthCacheService cacheService) {
        super(roleRepository, cacheService);
    }
    
    @Override
    public Future<Optional<Role>> handle(GetRoleByIdQuery query) {
        return handleGetRoleById(query);
    }
    
    @Override
    public Class<GetRoleByIdQuery> getQueryType() {
        return GetRoleByIdQuery.class;
    }
}
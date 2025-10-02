package com.auth.microservice.application.handler;

import com.auth.microservice.application.query.GetUserByIdQuery;
import com.auth.microservice.common.cqrs.QueryHandler;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.port.UserRepository;
import com.auth.microservice.infrastructure.adapter.cache.RedisAuthCacheService;
import io.vertx.core.Future;

import java.util.Optional;
import java.util.UUID;

/**
 * Query handler for GetUserByIdQuery.
 * Handles user lookup by ID with optional role loading and caching optimization.
 */
public class GetUserByIdQueryHandler extends UserQueryHandler implements QueryHandler<GetUserByIdQuery, Optional<User>> {
    
    public GetUserByIdQueryHandler(UserRepository userRepository, RedisAuthCacheService cacheService) {
        super(userRepository, cacheService);
    }
    
    /**
     * Handle GetUserByIdQuery with caching optimization
     */
    @Override
    public Future<Optional<User>> handle(GetUserByIdQuery query) {
        UUID targetUserId = query.getTargetUserId();
        
        // For user queries by ID, we don't have a direct cache yet, so we go to the database
        // In a production system, you might want to add user-by-id caching as well
        if (query.isIncludeRoles()) {
            return userRepository.findByIdWithRoles(targetUserId);
        } else {
            return userRepository.findById(targetUserId);
        }
    }
    
    @Override
    public Class<GetUserByIdQuery> getQueryType() {
        return GetUserByIdQuery.class;
    }
}
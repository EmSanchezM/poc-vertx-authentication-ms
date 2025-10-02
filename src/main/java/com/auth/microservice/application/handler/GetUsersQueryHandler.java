package com.auth.microservice.application.handler;

import com.auth.microservice.application.query.GetUsersQuery;
import com.auth.microservice.common.cqrs.QueryHandler;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.port.UserRepository;
import com.auth.microservice.infrastructure.adapter.cache.RedisAuthCacheService;
import io.vertx.core.Future;

import java.util.List;

/**
 * Query handler for GetUsersQuery.
 * Handles paginated user listing with filtering and search capabilities.
 */
public class GetUsersQueryHandler extends UserQueryHandler implements QueryHandler<GetUsersQuery, List<User>> {
    
    public GetUsersQueryHandler(UserRepository userRepository, RedisAuthCacheService cacheService) {
        super(userRepository, cacheService);
    }
    
    /**
     * Handle GetUsersQuery with pagination and filtering
     */
    @Override
    public Future<List<User>> handle(GetUsersQuery query) {
        // For list queries, we typically don't cache due to the variety of possible parameters
        // Instead, we optimize at the database level with proper indexing and projections
        
        if (query.hasSearchTerm()) {
            // Use search functionality when search term is provided
            return userRepository.searchUsers(query.getSearchTerm(), query.getPagination(), query.isIncludeInactive());
        } else {
            return handlePaginatedQuery(query);
        }
    }
    
    @Override
    public Class<GetUsersQuery> getQueryType() {
        return GetUsersQuery.class;
    }
}
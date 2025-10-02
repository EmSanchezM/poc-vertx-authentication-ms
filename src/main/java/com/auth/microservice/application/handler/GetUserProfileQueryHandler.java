package com.auth.microservice.application.handler;

import com.auth.microservice.application.query.GetUserProfileQuery;
import com.auth.microservice.application.result.UserProfile;
import com.auth.microservice.common.cqrs.QueryHandler;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.port.UserRepository;
import com.auth.microservice.infrastructure.adapter.cache.RedisAuthCacheService;
import io.vertx.core.Future;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Query handler for GetUserProfileQuery.
 * Handles user profile retrieval with caching and projection optimization.
 * Returns a UserProfile projection that excludes sensitive data.
 */
public class GetUserProfileQueryHandler extends UserQueryHandler implements QueryHandler<GetUserProfileQuery, Optional<UserProfile>> {
    
    public GetUserProfileQueryHandler(UserRepository userRepository, RedisAuthCacheService cacheService) {
        super(userRepository, cacheService);
    }
    
    /**
     * Handle GetUserProfileQuery with caching and projection optimization
     */
    @Override
    public Future<Optional<UserProfile>> handle(GetUserProfileQuery query) {
        UUID targetUserId = query.getTargetUserId();
        
        // Load user with roles to get complete profile information
        return userRepository.findByIdWithRoles(targetUserId)
            .compose(userOpt -> {
                if (userOpt.isEmpty()) {
                    return Future.succeededFuture(Optional.empty());
                }
                
                User user = userOpt.get();
                
                if (query.isIncludePermissions()) {
                    // Load permissions if requested
                    return loadUserPermissions(user)
                        .map(permissions -> Optional.of(createUserProfile(user, permissions)));
                } else {
                    // Create profile without permissions
                    return Future.succeededFuture(Optional.of(createUserProfile(user, Set.of())));
                }
            });
    }
    
    @Override
    public Class<GetUserProfileQuery> getQueryType() {
        return GetUserProfileQuery.class;
    }
}
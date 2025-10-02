package com.auth.microservice.application.handler;

import com.auth.microservice.application.query.GetSessionByTokenQuery;
import com.auth.microservice.common.cqrs.QueryHandler;
import com.auth.microservice.common.util.TokenHashUtil;
import com.auth.microservice.domain.model.Session;
import com.auth.microservice.domain.port.SessionRepository;
import com.auth.microservice.infrastructure.adapter.cache.RedisAuthCacheService;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Optional;

/**
 * Query handler for GetSessionByTokenQuery.
 * Retrieves a session by its token with validation and security checks.
 */
public class GetSessionByTokenQueryHandler extends SessionQueryHandler implements QueryHandler<GetSessionByTokenQuery, Optional<Session>> {

    private static final Logger logger = LoggerFactory.getLogger(GetSessionByTokenQueryHandler.class);

    public GetSessionByTokenQueryHandler(SessionRepository sessionRepository, RedisAuthCacheService cacheService) {
        super(sessionRepository, cacheService);
    }

    @Override
    public Future<Optional<Session>> handle(GetSessionByTokenQuery query) {
        logger.debug("Processing get session by token query for user: {}", query.getUserId());
        
        String tokenHash = TokenHashUtil.hashToken(query.getToken());
        
        return findSessionByToken(tokenHash, query.getTokenType())
            .compose(sessionOpt -> {
                if (sessionOpt.isEmpty()) {
                    logger.debug("Session not found for token type: {}", query.getTokenType());
                    return Future.succeededFuture(Optional.<Session>empty());
                }
                
                Session session = sessionOpt.get();
                
                // Validate expiration if requested
                if (query.isValidateExpiration() && session.isExpired()) {
                    logger.debug("Session found but expired for user: {}", session.getUserId());
                    return Future.succeededFuture(Optional.<Session>empty());
                }
                
                // Validate session is active
                if (!session.isActive()) {
                    logger.debug("Session found but inactive for user: {}", session.getUserId());
                    return Future.succeededFuture(Optional.<Session>empty());
                }
                
                // Update last used time for valid sessions
                if (session.isValid()) {
                    session.updateLastUsed();
                    return sessionRepository.save(session)
                        .compose(savedSession -> Future.succeededFuture(Optional.of(savedSession)));
                }
                
                logger.debug("Session retrieved successfully for user: {}", session.getUserId());
                return Future.succeededFuture(Optional.of(session));
            })
            .onFailure(throwable -> {
                logger.error("Error retrieving session by token", throwable);
            });
    }

    /**
     * Finds session by token based on token type
     */
    private Future<Optional<Session>> findSessionByToken(String tokenHash, GetSessionByTokenQuery.TokenType tokenType) {
        switch (tokenType) {
            case ACCESS_TOKEN:
                return sessionRepository.findByAccessTokenHash(tokenHash);
            
            case REFRESH_TOKEN:
                return sessionRepository.findByRefreshTokenHash(tokenHash);
            
            case ANY:
                // Try access token first, then refresh token
                return sessionRepository.findByAccessTokenHash(tokenHash)
                    .compose(sessionOpt -> {
                        if (sessionOpt.isPresent()) {
                            return Future.succeededFuture(sessionOpt);
                        }
                        // Try refresh token if access token not found
                        return sessionRepository.findByRefreshTokenHash(tokenHash);
                    });
            
            default:
                return Future.failedFuture(new IllegalArgumentException("Unsupported token type: " + tokenType));
        }
    }

    @Override
    public Class<GetSessionByTokenQuery> getQueryType() {
        return GetSessionByTokenQuery.class;
    }
}
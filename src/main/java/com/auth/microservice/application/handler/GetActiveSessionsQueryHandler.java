package com.auth.microservice.application.handler;

import com.auth.microservice.application.query.GetActiveSessionsQuery;
import com.auth.microservice.common.cqrs.QueryHandler;
import com.auth.microservice.domain.model.Session;
import com.auth.microservice.domain.port.SessionRepository;
import com.auth.microservice.infrastructure.adapter.cache.RedisAuthCacheService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

/**
 * Query handler for GetActiveSessionsQuery.
 * Retrieves all active sessions for a user with security filtering and anomaly detection.
 */
public class GetActiveSessionsQueryHandler extends SessionQueryHandler implements QueryHandler<GetActiveSessionsQuery, JsonObject> {

    private static final Logger logger = LoggerFactory.getLogger(GetActiveSessionsQueryHandler.class);

    public GetActiveSessionsQueryHandler(SessionRepository sessionRepository, RedisAuthCacheService cacheService) {
        super(sessionRepository, cacheService);
    }

    @Override
    public Future<JsonObject> handle(GetActiveSessionsQuery query) {
        logger.info("Processing get active sessions query for user: {} (target: {})", 
            query.getUserId(), query.getTargetUserId());
        
        return sessionRepository.findActiveSessionsByUserId(query.getTargetUserId())
            .compose(sessions -> {
                // Filter to only valid sessions
                List<Session> validSessions = filterValidSessions(sessions);
                
                // Apply result limit
                List<Session> limitedSessions = validSessions.stream()
                    .limit(query.getMaxResults())
                    .toList();
                
                // Detect suspicious activity
                boolean suspiciousActivity = detectSuspiciousActivity(validSessions);
                
                // Log security event if suspicious activity detected
                if (suspiciousActivity) {
                    logger.warn("Suspicious session activity detected for user: {}, sessionCount: {}", 
                        query.getTargetUserId(), validSessions.size());
                }
                
                // Create response
                JsonArray sessionsArray = new JsonArray();
                limitedSessions.forEach(session -> {
                    JsonObject sessionSummary = createSessionSummary(session, query.isIncludeSensitiveData());
                    sessionsArray.add(sessionSummary);
                });
                
                JsonObject response = new JsonObject()
                    .put("userId", query.getTargetUserId().toString())
                    .put("totalActiveSessions", validSessions.size())
                    .put("returnedSessions", limitedSessions.size())
                    .put("maxResults", query.getMaxResults())
                    .put("suspiciousActivity", suspiciousActivity)
                    .put("sessions", sessionsArray);
                
                logger.info("Retrieved {} active sessions for user: {}", limitedSessions.size(), query.getTargetUserId());
                return Future.succeededFuture(response);
            })
            .recover(throwable -> {
                logger.error("Error retrieving active sessions for user: " + query.getTargetUserId(), throwable);
                return Future.failedFuture(new RuntimeException("Failed to retrieve active sessions", throwable));
            });
    }

    @Override
    public Class<GetActiveSessionsQuery> getQueryType() {
        return GetActiveSessionsQuery.class;
    }
}
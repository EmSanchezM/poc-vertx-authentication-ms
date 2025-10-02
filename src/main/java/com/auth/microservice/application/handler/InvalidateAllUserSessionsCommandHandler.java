package com.auth.microservice.application.handler;

import com.auth.microservice.application.command.InvalidateAllUserSessionsCommand;
import com.auth.microservice.common.cqrs.CommandHandler;
import com.auth.microservice.common.util.TokenHashUtil;
import com.auth.microservice.domain.model.Session;
import com.auth.microservice.domain.port.SessionRepository;
import com.auth.microservice.domain.service.GeoLocationService;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

/**
 * Command handler for invalidating all sessions for a specific user.
 * Handles bulk session invalidation with security logging and anomaly detection.
 */
public class InvalidateAllUserSessionsCommandHandler implements CommandHandler<InvalidateAllUserSessionsCommand, Integer> {

    private static final Logger logger = LoggerFactory.getLogger(InvalidateAllUserSessionsCommandHandler.class);

    private final SessionRepository sessionRepository;
    private final GeoLocationService geoLocationService;

    public InvalidateAllUserSessionsCommandHandler(SessionRepository sessionRepository, GeoLocationService geoLocationService) {
        this.sessionRepository = sessionRepository;
        this.geoLocationService = geoLocationService;
    }

    @Override
    public Future<Integer> handle(InvalidateAllUserSessionsCommand command) {
        logger.info("Processing invalidation of all sessions for user: {} (target: {})", 
            command.getUserId(), command.getTargetUserId());
        
        // Log security event with geolocation
        logSecurityEventForAllSessions(command);
        
        return sessionRepository.findActiveSessionsByUserId(command.getTargetUserId())
            .compose(sessions -> {
                if (sessions.isEmpty()) {
                    logger.info("No active sessions found for user: {}", command.getTargetUserId());
                    return Future.succeededFuture(0);
                }
                
                // Filter out current session if requested
                List<Session> sessionsToInvalidate = sessions;
                if (command.isExcludeCurrentSession() && command.getCurrentSessionToken() != null) {
                    String currentTokenHash = TokenHashUtil.hashToken(command.getCurrentSessionToken());
                    sessionsToInvalidate = sessions.stream()
                        .filter(session -> !session.getAccessTokenHash().equals(currentTokenHash) && 
                                         !session.getRefreshTokenHash().equals(currentTokenHash))
                        .toList();
                }
                
                // Detect potential anomalies
                detectAnomalies(command, sessions, sessionsToInvalidate);
                
                // Invalidate all sessions
                List<Future<Session>> invalidationFutures = sessionsToInvalidate.stream()
                    .map(session -> {
                        session.invalidate();
                        return sessionRepository.save(session);
                    })
                    .toList();
                
                return Future.all(invalidationFutures)
                    .map(compositeFuture -> {
                        int invalidatedCount = sessionsToInvalidate.size();
                        logger.info("Successfully invalidated {} sessions for user: {}", 
                            invalidatedCount, command.getTargetUserId());
                        return invalidatedCount;
                    });
            })
            .recover(throwable -> {
                logger.error("Error invalidating all sessions for user: " + command.getTargetUserId(), throwable);
                return Future.failedFuture(new RuntimeException("Failed to invalidate all user sessions", throwable));
            });
    }

    /**
     * Detects potential security anomalies based on session patterns
     */
    private void detectAnomalies(InvalidateAllUserSessionsCommand command, List<Session> allSessions, List<Session> sessionsToInvalidate) {
        // Anomaly 1: Too many active sessions (potential account sharing or compromise)
        if (allSessions.size() > 10) {
            logger.warn("Security Anomaly - High session count: userId={}, sessionCount={}, reason='{}'", 
                command.getTargetUserId(), allSessions.size(), command.getReason());
        }
        
        // Anomaly 2: Sessions from multiple countries
        long distinctCountries = allSessions.stream()
            .map(Session::getIpAddress)
            .filter(ip -> ip != null && !ip.equals("unknown"))
            .distinct()
            .count();
        
        if (distinctCountries > 3) {
            logger.warn("Security Anomaly - Multiple geographic locations: userId={}, distinctIPs={}, reason='{}'", 
                command.getTargetUserId(), distinctCountries, command.getReason());
        }
        
        // Anomaly 3: Rapid session creation pattern (potential brute force or bot activity)
        long recentSessions = allSessions.stream()
            .filter(session -> session.getCreatedAt().isAfter(java.time.LocalDateTime.now().minusHours(1)))
            .count();
        
        if (recentSessions > 5) {
            logger.warn("Security Anomaly - Rapid session creation: userId={}, recentSessions={}, reason='{}'", 
                command.getTargetUserId(), recentSessions, command.getReason());
        }
        
        // Anomaly 4: Different user agents (potential device compromise)
        long distinctUserAgents = allSessions.stream()
            .map(Session::getUserAgent)
            .filter(ua -> ua != null && !ua.isEmpty())
            .distinct()
            .count();
        
        if (distinctUserAgents > 5) {
            logger.warn("Security Anomaly - Multiple user agents: userId={}, distinctUserAgents={}, reason='{}'", 
                command.getTargetUserId(), distinctUserAgents, command.getReason());
        }
    }

    /**
     * Logs security event for all sessions invalidation with geolocation
     */
    private void logSecurityEventForAllSessions(InvalidateAllUserSessionsCommand command) {
        if (command.getIpAddress() != null) {
            geoLocationService.getCountryFromIp(command.getIpAddress())
                .onSuccess(country -> {
                    logger.warn("Security Event - All sessions invalidated: userId={}, targetUserId={}, reason='{}', " +
                               "ipAddress={}, country={}, excludeCurrentSession={}", 
                        command.getUserId(), command.getTargetUserId(), command.getReason(), 
                        command.getIpAddress(), country, command.isExcludeCurrentSession());
                })
                .onFailure(error -> {
                    logger.warn("Security Event - All sessions invalidated: userId={}, targetUserId={}, reason='{}', " +
                               "ipAddress={}, excludeCurrentSession={}", 
                        command.getUserId(), command.getTargetUserId(), command.getReason(), 
                        command.getIpAddress(), command.isExcludeCurrentSession());
                });
        } else {
            logger.warn("Security Event - All sessions invalidated: userId={}, targetUserId={}, reason='{}'", 
                command.getUserId(), command.getTargetUserId(), command.getReason());
        }
    }

    @Override
    public Class<InvalidateAllUserSessionsCommand> getCommandType() {
        return InvalidateAllUserSessionsCommand.class;
    }
}
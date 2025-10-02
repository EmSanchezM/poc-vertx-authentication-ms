package com.auth.microservice.application.handler;

import com.auth.microservice.application.command.InvalidateSessionCommand;
import com.auth.microservice.common.cqrs.CommandHandler;
import com.auth.microservice.common.util.TokenHashUtil;
import com.auth.microservice.domain.exception.SessionNotFoundException;
import com.auth.microservice.domain.model.Session;
import com.auth.microservice.domain.port.SessionRepository;
import com.auth.microservice.domain.service.GeoLocationService;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * Command handler for invalidating individual sessions.
 * Handles session invalidation with security logging and geolocation tracking.
 */
public class InvalidateSessionCommandHandler implements CommandHandler<InvalidateSessionCommand, Void> {

    private static final Logger logger = LoggerFactory.getLogger(InvalidateSessionCommandHandler.class);

    private final SessionRepository sessionRepository;
    private final GeoLocationService geoLocationService;

    public InvalidateSessionCommandHandler(SessionRepository sessionRepository, GeoLocationService geoLocationService) {
        this.sessionRepository = sessionRepository;
        this.geoLocationService = geoLocationService;
    }

    /**
     * Handles InvalidateSessionCommand - invalidates a specific session by token
     */
    public Future<Void> handle(InvalidateSessionCommand command) {
        logger.info("Processing session invalidation request for user: {}", command.getUserId());
        
        String tokenHash = TokenHashUtil.hashToken(command.getToken());
        
        return sessionRepository.findByAccessTokenHash(tokenHash)
            .compose(sessionOpt -> {
                if (sessionOpt.isEmpty()) {
                    // Try refresh token hash
                    return sessionRepository.findByRefreshTokenHash(tokenHash);
                }
                return Future.succeededFuture(sessionOpt);
            })
            .compose(sessionOpt -> {
                if (sessionOpt.isEmpty()) {
                    logger.warn("Session invalidation failed: Session not found for user: {}", command.getUserId());
                    return Future.failedFuture(new SessionNotFoundException("Session not found"));
                }
                
                Session session = sessionOpt.get();
                
                // Verify the session belongs to the requesting user (security check)
                if (!session.getUserId().toString().equals(command.getUserId())) {
                    logger.warn("Session invalidation failed: User {} attempted to invalidate session belonging to user {}", 
                        command.getUserId(), session.getUserId());
                    return Future.failedFuture(new SecurityException("Cannot invalidate session belonging to another user"));
                }
                
                // Log security event with geolocation if available
                logSecurityEvent(command, session);
                
                // Invalidate the session
                session.invalidate();
                return sessionRepository.save(session);
            })
            .compose(savedSession -> {
                logger.info("Session invalidated successfully for user: {} from IP: {}", 
                    command.getUserId(), command.getIpAddress());
                return Future.succeededFuture();
            })
            .recover(throwable -> {
                if (throwable instanceof SessionNotFoundException || throwable instanceof SecurityException) {
                    return Future.failedFuture(throwable);
                }
                logger.error("Error invalidating session for user: " + command.getUserId(), throwable);
                return Future.failedFuture(new RuntimeException("Failed to invalidate session", throwable));
            });
    }



    /**
     * Logs security event for single session invalidation with geolocation
     */
    private void logSecurityEvent(InvalidateSessionCommand command, Session session) {
        if (command.getIpAddress() != null) {
            geoLocationService.getCountryFromIp(command.getIpAddress())
                .onSuccess(country -> {
                    logger.info("Security Event - Session invalidated: userId={}, sessionId={}, reason='{}', " +
                               "ipAddress={}, country={}, userAgent={}", 
                        command.getUserId(), session.getId(), command.getReason(), 
                        command.getIpAddress(), country, session.getUserAgent());
                })
                .onFailure(error -> {
                    logger.info("Security Event - Session invalidated: userId={}, sessionId={}, reason='{}', " +
                               "ipAddress={}, userAgent={}", 
                        command.getUserId(), session.getId(), command.getReason(), 
                        command.getIpAddress(), session.getUserAgent());
                });
        } else {
            logger.info("Security Event - Session invalidated: userId={}, sessionId={}, reason='{}'", 
                command.getUserId(), session.getId(), command.getReason());
        }
    }



    @Override
    public Class<InvalidateSessionCommand> getCommandType() {
        return InvalidateSessionCommand.class;
    }
}
package com.auth.microservice.application.handler;

import com.auth.microservice.common.cqrs.CommandHandler;
import com.auth.microservice.application.command.RefreshTokenCommand;
import com.auth.microservice.application.result.AuthenticationResult;
import com.auth.microservice.common.util.TokenHashUtil;
import com.auth.microservice.domain.exception.InvalidTokenException;
import com.auth.microservice.domain.model.Email;
import com.auth.microservice.domain.model.Session;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.port.SessionRepository;
import com.auth.microservice.domain.port.UserRepository;
import com.auth.microservice.domain.service.JWTService;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Command handler for token refresh operations.
 * Handles refresh token validation, session management, and new token generation.
 */
public class RefreshTokenCommandHandler implements CommandHandler<RefreshTokenCommand, AuthenticationResult> {

    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenCommandHandler.class);

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final JWTService jwtService;

    public RefreshTokenCommandHandler(UserRepository userRepository, SessionRepository sessionRepository, JWTService jwtService) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.jwtService = jwtService;
    }

    @Override
    public Future<AuthenticationResult> handle(RefreshTokenCommand command) {
        logger.info("Processing token refresh");
        
        try {
            // First, validate the refresh token format
            JWTService.TokenValidationResult validation = jwtService.validateToken(command.getRefreshToken());
            
            if (!validation.isValid()) {
                logger.warn("Token refresh failed: Invalid refresh token format");
                return Future.succeededFuture(AuthenticationResult.failure("Invalid refresh token"));
            }
            // Check if session exists and is valid
            String refreshTokenHash = TokenHashUtil.hashToken(command.getRefreshToken());
            logger.debug("Looking for session with refresh token hash: {}", refreshTokenHash.substring(0, 10) + "...");
            
            return sessionRepository.findByRefreshTokenHash(refreshTokenHash)
                .compose(sessionOpt -> {
                    if (sessionOpt.isEmpty()) {
                        logger.warn("Token refresh failed: Session not found for refresh token");
                        return Future.succeededFuture(AuthenticationResult.failure("Invalid refresh token"));
                    }
                    
                    Session session = sessionOpt.get();
                    
                    // Validate session is active and not expired
                    if (!session.isValid()) {
                        logger.warn("Token refresh failed: Session is invalid (expired or inactive) for user: {}", session.getUserId());
                        return Future.succeededFuture(AuthenticationResult.failure("Session expired"));
                    }
                    
                    // Extract user information from token for additional validation
                    Optional<String> userIdOpt = jwtService.extractUserId(command.getRefreshToken());
                    Optional<String> emailOpt = jwtService.extractUserEmail(command.getRefreshToken());
                    
                    if (userIdOpt.isEmpty() || emailOpt.isEmpty()) {
                        logger.warn("Token refresh failed: Cannot extract user information from token");
                        return Future.succeededFuture(AuthenticationResult.failure("Invalid token format"));
                    }
                    
                    // Verify token user matches session user
                    if (!session.getUserId().toString().equals(userIdOpt.get())) {
                        logger.warn("Token refresh failed: User ID mismatch between token and session");
                        return Future.succeededFuture(AuthenticationResult.failure("Invalid token"));
                    }
                    
                    Email email = new Email(emailOpt.get());
                    
                    return userRepository.findByEmailWithRoles(email)
                        .compose(userOpt -> {
                            if (userOpt.isEmpty()) {
                                logger.warn("Token refresh failed: User not found for email: {}", emailOpt.get());
                                return Future.succeededFuture(AuthenticationResult.failure("User not found"));
                            }
                            
                            User user = userOpt.get();
                            
                            if (!user.isActive()) {
                                logger.warn("Token refresh failed: User account is inactive for email: {}", emailOpt.get());
                                return Future.succeededFuture(AuthenticationResult.failure("Account is inactive"));
                            }
                            
                            // Generate new token pair
                            Set<String> permissions = user.getAllPermissions().stream()
                                .map(permission -> permission.getName())
                                .collect(Collectors.toSet());
                            
                            JWTService.TokenPair tokenPair = jwtService.generateTokenPair(
                                user.getId().toString(),
                                user.getEmail().getValue(),
                                permissions
                            );
                            
                            // Update session with new tokens
                            String newAccessTokenHash = TokenHashUtil.hashToken(tokenPair.accessToken());
                            String newRefreshTokenHash = TokenHashUtil.hashToken(tokenPair.refreshToken());
                            
                            session.updateTokens(newAccessTokenHash, newRefreshTokenHash, tokenPair.refreshTokenExpiration());
                            
                            logger.info("Updating session with ID: {} for user: {}", session.getId(), user.getId());
                            return sessionRepository.update(session)
                                .compose(savedSession -> {
                                    logger.info("Token refresh successful for user: {} from IP: {}", 
                                        user.getId(), command.getIpAddress());
                                    return Future.succeededFuture(AuthenticationResult.success(user, tokenPair));
                                });
                        });
                })
                .recover(throwable -> {
                    if (throwable instanceof IllegalArgumentException) {
                        logger.warn("Token refresh failed: Invalid token format");
                        return Future.succeededFuture(AuthenticationResult.failure("Invalid token format"));
                    }
                    logger.error("Token refresh error", throwable);
                    return Future.failedFuture(new InvalidTokenException("Token refresh failed", throwable));
                });
                
        } catch (Exception e) {
            logger.error("Token refresh error", e);
            return Future.failedFuture(new InvalidTokenException("Token refresh failed", e));
        }
    }

    @Override
    public Class<RefreshTokenCommand> getCommandType() {
        return RefreshTokenCommand.class;
    }
}
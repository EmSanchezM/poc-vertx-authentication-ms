package com.auth.microservice.application.handler;

import com.auth.microservice.common.cqrs.CommandHandler;
import com.auth.microservice.application.command.AuthenticateCommand;
import com.auth.microservice.application.result.AuthenticationResult;
import com.auth.microservice.common.util.TokenHashUtil;
import com.auth.microservice.domain.exception.AuthenticationException;
import com.auth.microservice.domain.model.Email;
import com.auth.microservice.domain.model.Session;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.port.SessionRepository;
import com.auth.microservice.domain.port.UserRepository;
import com.auth.microservice.domain.service.JWTService;
import com.auth.microservice.domain.service.PasswordService;
import com.auth.microservice.domain.service.GeoLocationService;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Command handler for user authentication operations.
 * Handles user login/authentication commands.
 */
public class AuthCommandHandler implements CommandHandler<AuthenticateCommand, AuthenticationResult> {

    private static final Logger logger = LoggerFactory.getLogger(AuthCommandHandler.class);

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordService passwordService;
    private final JWTService jwtService;
    private final GeoLocationService geoLocationService;

    public AuthCommandHandler(UserRepository userRepository, 
                             SessionRepository sessionRepository,
                             PasswordService passwordService, 
                             JWTService jwtService,
                             GeoLocationService geoLocationService) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
        this.geoLocationService = geoLocationService;
    }

    /**
     * Handles user authentication command.
     */
    public Future<AuthenticationResult> handle(AuthenticateCommand command) {
        String identifierType = command.isEmail() ? "email" : "username";
        
        try {
            Future<Optional<User>> userFuture = findUserByIdentifier(command);
            
            return userFuture
                .compose(userOpt -> {
                    if (userOpt.isEmpty()) {
                        logger.warn("Authentication failed: User not found");
                        return Future.succeededFuture(AuthenticationResult.failure("Invalid credentials"));
                    }
                    
                    User user = userOpt.get();
                    
                    if (!user.isActive()) {
                        logger.warn("Authentication failed: User account is inactive");
                        return Future.succeededFuture(AuthenticationResult.failure("Account is inactive"));
                    }
                    
                    if (!passwordService.verifyPassword(command.getPassword(), user.getPasswordHash())) {
                        logger.warn("Authentication failed: Invalid password");
                        return Future.succeededFuture(AuthenticationResult.failure("Invalid credentials"));
                    }
                    
                    logger.info("Password verification successful, proceeding to generate tokens");
                    
                    // Generate tokens
                    Set<String> permissions = user.getAllPermissions().stream()
                        .map(permission -> permission.getName())
                        .collect(Collectors.toSet());
                    
                    logger.info("Generating token pair for user: {} with permissions: {}", user.getId(), permissions);
                    
                    JWTService.TokenPair tokenPair = jwtService.generateTokenPair(
                        user.getId().toString(),
                        user.getEmail().getValue(),
                        permissions
                    );
                    
                    // Create and save session
                    String accessTokenHash = TokenHashUtil.hashToken(tokenPair.accessToken());
                    String refreshTokenHash = TokenHashUtil.hashToken(tokenPair.refreshToken());
                    OffsetDateTime expirationTime = tokenPair.refreshTokenExpiration();
                    
                    logger.info("About to save session for user: {}", user.getId());
                    
                    // Get country code from IP address
                    return geoLocationService.getCountryByIp(command.getIpAddress())
                        .recover(throwable -> {
                            logger.warn("Failed to get country code for IP {}: {}", command.getIpAddress(), throwable.getMessage());
                            return Future.succeededFuture("XX"); // Default fallback
                        })
                        .compose(countryCode -> {
                            // Ensure country code is valid (2 characters max)
                            String validCountryCode = normalizeCountryCode(countryCode);
                            
                            Session session = new Session(
                                user.getId(),
                                accessTokenHash,
                                refreshTokenHash,
                                expirationTime,
                                command.getIpAddress(),
                                command.getUserAgent(),
                                validCountryCode
                            );
                            
                            return sessionRepository.save(session);
                        })
                        .compose(savedSession -> {
                            logger.info("Authentication successful for user: {}", user.getId());
                            return Future.succeededFuture(AuthenticationResult.success(user, tokenPair));
                        })
                        .recover(sessionError -> {
                            logger.error("Failed to save session for user: {}", user.getId(), sessionError);
                            return Future.failedFuture(new AuthenticationException("Failed to create session", sessionError));
                        });
                })
                .recover(throwable -> {
                    if (throwable instanceof IllegalArgumentException) {
                        logger.warn("Authentication failed: Invalid identifier format");
                        return Future.succeededFuture(AuthenticationResult.failure("Invalid credentials"));
                    }
                    logger.error("Authentication error", throwable);
                    return Future.failedFuture(new AuthenticationException("Authentication failed", throwable));
                });
                
        } catch (Exception e) {
            logger.error("Authentication error", e);
            return Future.failedFuture(new AuthenticationException("Authentication failed", e));
        }
    }

    /**
     * Find user by either email or username based on the identifier format
     */
    private Future<Optional<User>> findUserByIdentifier(AuthenticateCommand command) {
        if (command.isEmail()) {
            try {
                Email email = new Email(command.getUsernameOrEmail());
                return userRepository.findByEmailWithRoles(email);
            } catch (IllegalArgumentException e) {
                // Invalid email format
                return Future.succeededFuture(Optional.empty());
            }
        } else {
            return userRepository.findByUsernameWithRoles(command.getUsernameOrEmail());
        }
    }

    /**
     * Normalizes country code to ensure it fits database constraints (2 characters max)
     */
    private String normalizeCountryCode(String countryCode) {
        if (countryCode == null || countryCode.trim().isEmpty()) {
            return "XX"; // Unknown
        }
        
        String normalized = countryCode.trim().toUpperCase();
        
        // Handle special cases
        if ("UNKNOWN".equals(normalized)) {
            return "XX"; // Unknown
        }
        
        // Ensure it's exactly 2 characters
        if (normalized.length() > 2) {
            return normalized.substring(0, 2);
        } else if (normalized.length() == 1) {
            return normalized + "X";
        }
        
        return normalized;
    }

    @Override
    public Class<AuthenticateCommand> getCommandType() {
        return AuthenticateCommand.class;
    }
}
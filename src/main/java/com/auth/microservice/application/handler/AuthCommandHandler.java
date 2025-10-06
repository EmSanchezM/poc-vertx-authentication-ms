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
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

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

    public AuthCommandHandler(UserRepository userRepository, 
                             SessionRepository sessionRepository,
                             PasswordService passwordService, 
                             JWTService jwtService) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
    }

    /**
     * Handles user authentication command.
     */
    public Future<AuthenticationResult> handle(AuthenticateCommand command) {
        String identifierType = command.isEmail() ? "email" : "username";
        logger.info("Processing authentication with {}", identifierType);
        
        try {
            return findUserByIdentifier(command)
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
                    
                    // Generate tokens
                    Set<String> permissions = user.getAllPermissions().stream()
                        .map(permission -> permission.getName())
                        .collect(Collectors.toSet());
                    
                    JWTService.TokenPair tokenPair = jwtService.generateTokenPair(
                        user.getId().toString(),
                        user.getEmail().getValue(),
                        permissions
                    );
                    
                    // Create and save session
                    String accessTokenHash = TokenHashUtil.hashToken(tokenPair.accessToken());
                    String refreshTokenHash = TokenHashUtil.hashToken(tokenPair.refreshToken());
                    
                    Session session = new Session(
                        user.getId(),
                        accessTokenHash,
                        refreshTokenHash,
                        tokenPair.refreshTokenExpiration(),
                        command.getIpAddress(),
                        command.getUserAgent()
                    );
                    
                    return sessionRepository.save(session)
                        .compose(savedSession -> {
                            logger.info("Authentication successful for user: {}", user.getId());
                            return Future.succeededFuture(AuthenticationResult.success(user, tokenPair));
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

    @Override
    public Class<AuthenticateCommand> getCommandType() {
        return AuthenticateCommand.class;
    }
}
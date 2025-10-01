package com.auth.microservice.application.handler;

import com.auth.microservice.common.cqrs.CommandHandler;
import com.auth.microservice.application.command.AuthenticateCommand;
import com.auth.microservice.application.result.AuthenticationResult;
import com.auth.microservice.domain.exception.AuthenticationException;
import com.auth.microservice.domain.model.Email;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.port.UserRepository;
import com.auth.microservice.domain.service.JWTService;
import com.auth.microservice.domain.service.PasswordService;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Command handler for user authentication operations.
 * Handles user login/authentication commands.
 */
public class AuthCommandHandler implements CommandHandler<AuthenticateCommand, AuthenticationResult> {

    private static final Logger logger = LoggerFactory.getLogger(AuthCommandHandler.class);

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final JWTService jwtService;

    public AuthCommandHandler(UserRepository userRepository, 
                             PasswordService passwordService, 
                             JWTService jwtService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
    }

    /**
     * Handles user authentication command.
     */
    public Future<AuthenticationResult> handle(AuthenticateCommand command) {
        logger.info("Processing authentication for email: {}", command.getEmail());
        
        try {
            Email email = new Email(command.getEmail());
            
            return userRepository.findByEmailWithRoles(email)
                .compose(userOpt -> {
                    if (userOpt.isEmpty()) {
                        logger.warn("Authentication failed: User not found for email: {}", command.getEmail());
                        return Future.succeededFuture(AuthenticationResult.failure("Invalid credentials"));
                    }
                    
                    User user = userOpt.get();
                    
                    if (!user.isActive()) {
                        logger.warn("Authentication failed: User account is inactive for email: {}", command.getEmail());
                        return Future.succeededFuture(AuthenticationResult.failure("Account is inactive"));
                    }
                    
                    if (!passwordService.verifyPassword(command.getPassword(), user.getPasswordHash())) {
                        logger.warn("Authentication failed: Invalid password for email: {}", command.getEmail());
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
                    
                    logger.info("Authentication successful for user: {}", user.getId());
                    return Future.succeededFuture(AuthenticationResult.success(user, tokenPair));
                })
                .recover(throwable -> {
                    if (throwable instanceof IllegalArgumentException) {
                        logger.warn("Authentication failed: Invalid email format: {}", command.getEmail());
                        return Future.succeededFuture(AuthenticationResult.failure("Invalid email format"));
                    }
                    logger.error("Authentication error for email: {}", command.getEmail(), throwable);
                    return Future.failedFuture(new AuthenticationException("Authentication failed", throwable));
                });
                
        } catch (Exception e) {
            logger.error("Authentication error for email: {}", command.getEmail(), e);
            return Future.failedFuture(new AuthenticationException("Authentication failed", e));
        }
    }

    @Override
    public Class<AuthenticateCommand> getCommandType() {
        return AuthenticateCommand.class;
    }
}
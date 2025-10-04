package com.auth.microservice.application.handler;

import com.auth.microservice.common.cqrs.CommandHandler;
import com.auth.microservice.application.command.RegisterUserCommand;
import com.auth.microservice.application.result.RegistrationResult;
import com.auth.microservice.domain.exception.UserAlreadyExistsException;
import com.auth.microservice.domain.exception.UsernameGenerationException;
import com.auth.microservice.domain.model.Email;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.model.UsernameValidationResult;
import com.auth.microservice.domain.port.RoleRepository;
import com.auth.microservice.domain.port.UserRepository;
import com.auth.microservice.domain.service.PasswordService;
import com.auth.microservice.domain.service.UsernameGenerationService;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Command handler for user registration operations.
 * Handles user registration commands with validation and role assignment.
 */
public class RegisterUserCommandHandler implements CommandHandler<RegisterUserCommand, RegistrationResult> {

    private static final Logger logger = LoggerFactory.getLogger(RegisterUserCommandHandler.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordService passwordService;
    private final UsernameGenerationService usernameGenerationService;

    public RegisterUserCommandHandler(UserRepository userRepository, 
                                    RoleRepository roleRepository,
                                    PasswordService passwordService,
                                    UsernameGenerationService usernameGenerationService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordService = passwordService;
        this.usernameGenerationService = usernameGenerationService;
    }

    @Override
    public Future<RegistrationResult> handle(RegisterUserCommand command) {
        logger.info("Processing registration for email: {}", command.getEmail());
        
        try {
            Email email = new Email(command.getEmail());
            
            // Validate password strength first before creating Password object
            PasswordService.PasswordValidationResult validation = 
                passwordService.validatePasswordStrength(command.getPassword());
            
            if (!validation.isValid()) {
                logger.warn("Registration failed: Weak password for email: {}", command.getEmail());
                return Future.succeededFuture(RegistrationResult.failure(validation.message()));
            }
            
            // Check if user already exists by email
            return userRepository.existsByEmail(email)
                .compose(emailExists -> {
                    if (emailExists) {
                        logger.warn("Registration failed: Email already exists: {}", command.getEmail());
                        return Future.succeededFuture(RegistrationResult.failure("Email already exists"));
                    }
                    
                    // Handle username generation or validation
                    return handleUsernameProcessing(command);
                })
                .recover(throwable -> {
                    if (throwable instanceof IllegalArgumentException) {
                        logger.warn("Registration failed: Invalid input for email: {}", command.getEmail(), throwable);
                        return Future.succeededFuture(RegistrationResult.failure(throwable.getMessage()));
                    }
                    if (throwable instanceof UsernameGenerationException) {
                        String errorType = command.hasExplicitUsername() ? "explicit username validation" : "automatic username generation";
                        logger.warn("Registration failed: {} error for email: {}", errorType, command.getEmail(), throwable);
                        return Future.succeededFuture(RegistrationResult.failure(
                            "Username processing failed: " + throwable.getMessage()));
                    }
                    logger.error("Registration error for email: {}", command.getEmail(), throwable);
                    return Future.failedFuture(new UserAlreadyExistsException("Registration failed", throwable));
                });
                
        } catch (Exception e) {
            logger.error("Registration error for email: {}", command.getEmail(), e);
            return Future.failedFuture(new UserAlreadyExistsException("Registration failed", e));
        }
    }

    /**
     * Handles username processing - either generates a new username or validates an explicit one.
     */
    private Future<RegistrationResult> handleUsernameProcessing(RegisterUserCommand command) {
        if (command.hasExplicitUsername()) {
            // Handle explicit username validation
            logger.info("Processing registration with explicit username: {} for email: {}", 
                command.getUsername(), command.getEmail());
            return handleExplicitUsername(command);
        } else {
            // Handle automatic username generation
            logger.info("Processing registration with automatic username generation for email: {} (firstName: {}, lastName: {})", 
                command.getEmail(), command.getFirstName(), command.getLastName());
            return handleAutomaticUsernameGeneration(command);
        }
    }

    /**
     * Handles registration with explicit username provided by user.
     */
    private Future<RegistrationResult> handleExplicitUsername(RegisterUserCommand command) {
        logger.debug("Validating explicit username: {} for email: {}", command.getUsername(), command.getEmail());
        
        // First validate the username format and rules
        return usernameGenerationService.validateUsername(command.getUsername())
            .compose(validationResult -> {
                if (!validationResult.isValid()) {
                    logger.warn("Registration failed: Invalid explicit username '{}' for email: {}. Violations: {}", 
                        command.getUsername(), command.getEmail(), validationResult.getViolations());
                    return Future.succeededFuture(RegistrationResult.failure(
                        "Invalid username: " + validationResult.getMessage()));
                }
                
                // Then check if username already exists
                return userRepository.existsByUsername(command.getUsername())
                    .compose(usernameExists -> {
                        if (usernameExists) {
                            logger.warn("Registration failed: Explicit username already exists: {} for email: {}", 
                                command.getUsername(), command.getEmail());
                            return Future.succeededFuture(RegistrationResult.failure("Username already exists"));
                        }
                        
                        logger.info("Explicit username validation successful: {} for email: {}", 
                            command.getUsername(), command.getEmail());
                        // Create user with explicit username
                        return createAndSaveUser(command, command.getUsername());
                    });
            })
            .recover(throwable -> {
                logger.error("Error validating explicit username '{}' for email: {}", 
                    command.getUsername(), command.getEmail(), throwable);
                return Future.succeededFuture(RegistrationResult.failure(
                    "Username validation failed: " + throwable.getMessage()));
            });
    }

    /**
     * Handles registration with automatic username generation.
     */
    private Future<RegistrationResult> handleAutomaticUsernameGeneration(RegisterUserCommand command) {
        logger.debug("Starting automatic username generation for email: {} (firstName: {}, lastName: {})", 
            command.getEmail(), command.getFirstName(), command.getLastName());
        
        long startTime = System.currentTimeMillis();
        
        return usernameGenerationService.generateUsername(command.getFirstName(), command.getLastName())
            .compose(generatedUsername -> {
                long generationTime = System.currentTimeMillis() - startTime;
                logger.info("Successfully generated username: {} for email: {} in {}ms", 
                    generatedUsername, command.getEmail(), generationTime);
                
                // Validate the generated username (additional safety check)
                return usernameGenerationService.validateUsername(generatedUsername)
                    .compose(validationResult -> {
                        if (!validationResult.isValid()) {
                            logger.error("Generated username validation failed: {} for email: {}. Violations: {}", 
                                generatedUsername, command.getEmail(), validationResult.getViolations());
                            return Future.failedFuture(new UsernameGenerationException(
                                "Generated username failed validation: " + validationResult.getMessage()));
                        }
                        
                        logger.debug("Generated username validation successful: {} for email: {}", 
                            generatedUsername, command.getEmail());
                        return createAndSaveUser(command, generatedUsername);
                    });
            })
            .recover(throwable -> {
                long totalTime = System.currentTimeMillis() - startTime;
                if (throwable instanceof UsernameGenerationException) {
                    logger.error("Username generation failed for email: {} (firstName: {}, lastName: {}) after {}ms: {}", 
                        command.getEmail(), command.getFirstName(), command.getLastName(), totalTime, throwable.getMessage());
                    return Future.failedFuture(throwable);
                }
                logger.error("Unexpected error during username generation for email: {} after {}ms", 
                    command.getEmail(), totalTime, throwable);
                return Future.failedFuture(new UsernameGenerationException(
                    "Unexpected error during username generation", throwable));
            });
    }

    /**
     * Creates and saves a user with the provided username.
     */
    private Future<RegistrationResult> createAndSaveUser(RegisterUserCommand command, String username) {
        try {
            logger.debug("Creating user with username: {} for email: {}", username, command.getEmail());
            
            Email email = new Email(command.getEmail());
            String hashedPassword = passwordService.hashPassword(command.getPassword());
            
            User user = new User(
                username,
                email,
                hashedPassword,
                command.getFirstName(),
                command.getLastName()
            );
            
            // Assign roles if specified
            if (!command.getRoleNames().isEmpty()) {
                logger.debug("Assigning roles {} to user with username: {}", command.getRoleNames(), username);
                return assignRolesToUser(user, command.getRoleNames())
                    .compose(userWithRoles -> {
                        logger.debug("Saving user with roles, username: {}", username);
                        return userRepository.save(userWithRoles);
                    })
                    .compose(savedUser -> {
                        String usernameType = command.hasExplicitUsername() ? "explicit" : "generated";
                        logger.info("User registered successfully with {} username: {} and ID: {} for email: {}", 
                            usernameType, savedUser.getUsername(), savedUser.getId(), command.getEmail());
                        return Future.succeededFuture(RegistrationResult.success(savedUser));
                    });
            } else {
                logger.debug("Saving user without roles, username: {}", username);
                return userRepository.save(user)
                    .compose(savedUser -> {
                        String usernameType = command.hasExplicitUsername() ? "explicit" : "generated";
                        logger.info("User registered successfully with {} username: {} and ID: {} for email: {}", 
                            usernameType, savedUser.getUsername(), savedUser.getId(), command.getEmail());
                        return Future.succeededFuture(RegistrationResult.success(savedUser));
                    });
            }
        } catch (Exception e) {
            logger.error("Error creating user with username: {} for email: {}", username, command.getEmail(), e);
            return Future.failedFuture(e);
        }
    }

    /**
     * Assigns roles to a user by role names.
     */
    private Future<User> assignRolesToUser(User user, Set<String> roleNames) {
        List<Future<Optional<Role>>> roleFutures = roleNames.stream()
            .map(roleRepository::findByNameWithPermissions)
            .collect(Collectors.toList());
        
        return Future.all(roleFutures)
            .compose(compositeFuture -> {
                Set<Role> roles = new HashSet<>();
                
                for (int i = 0; i < compositeFuture.size(); i++) {
                    Optional<Role> roleOpt = compositeFuture.resultAt(i);
                    if (roleOpt.isPresent()) {
                        roles.add(roleOpt.get());
                    } else {
                        String roleName = roleNames.stream().skip(i).findFirst().orElse("unknown");
                        logger.warn("Role not found: {}", roleName);
                    }
                }
                
                roles.forEach(user::addRole);
                return Future.succeededFuture(user);
            });
    }

    @Override
    public Class<RegisterUserCommand> getCommandType() {
        return RegisterUserCommand.class;
    }
}
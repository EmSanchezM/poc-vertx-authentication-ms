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
            return handleExplicitUsername(command);
        } else {
            return handleAutomaticUsernameGeneration(command);
        }
    }

    /**
     * Handles registration with explicit username provided by user.
     */
    private Future<RegistrationResult> handleExplicitUsername(RegisterUserCommand command) {
        // First validate the username format and rules
        return usernameGenerationService.validateUsername(command.getUsername())
            .compose(validationResult -> {
                if (!validationResult.isValid()) {
                    return Future.succeededFuture(RegistrationResult.failure(
                        "Invalid username: " + validationResult.getMessage()));
                }
                
                // Then check if username already exists
                return userRepository.existsByUsername(command.getUsername())
                    .compose(usernameExists -> {
                        if (usernameExists) {
                            return Future.succeededFuture(RegistrationResult.failure("Username already exists"));
                        }
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
        long startTime = System.currentTimeMillis();
        
        return usernameGenerationService.generateUsername(command.getFirstName(), command.getLastName())
            .compose(generatedUsername -> {
                long generationTime = System.currentTimeMillis() - startTime;
                
                // Validate the generated username (additional safety check)
                return usernameGenerationService.validateUsername(generatedUsername)
                    .compose(validationResult -> {
                        if (!validationResult.isValid()) {
                            return Future.failedFuture(new UsernameGenerationException(
                                "Generated username failed validation: " + validationResult.getMessage()));
                        }
                        return createAndSaveUser(command, generatedUsername);
                    });
            })
            .recover(throwable -> {
                long totalTime = System.currentTimeMillis() - startTime;
                if (throwable instanceof UsernameGenerationException) {
                    return Future.failedFuture(throwable);
                }
                return Future.failedFuture(new UsernameGenerationException(
                    "Unexpected error during username generation", throwable));
            });
    }

    /**
     * Creates and saves a user with the provided username.
     */
    private Future<RegistrationResult> createAndSaveUser(RegisterUserCommand command, String username) {
        try {
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
                logger.info("Starting role assignment for user '{}': {} roles to assign", 
                    username, command.getRoleNames().size());
                logger.debug("Roles to assign: {}", command.getRoleNames());
                
                return assignRolesToUser(user, command.getRoleNames())
                    .compose(userWithRoles -> {
                        logger.debug("Roles successfully assigned to user '{}', proceeding with transactional save", username);
                        return userRepository.saveWithRoles(userWithRoles);
                    })
                    .compose(savedUser -> {
                        logger.info("User '{}' successfully registered with {} roles persisted to database", 
                            username, savedUser.getRoles().size());
                        String usernameType = command.hasExplicitUsername() ? "explicit" : "generated";
                        return Future.succeededFuture(RegistrationResult.success(savedUser));
                    })
                    .recover(throwable -> {
                        logger.error("Failed to save user '{}' with roles: {}", username, throwable.getMessage(), throwable);
                        if (throwable.getMessage() != null && throwable.getMessage().contains("role")) {
                            return Future.succeededFuture(RegistrationResult.failure(
                                "Role assignment failed: " + throwable.getMessage()));
                        } else if (throwable.getMessage() != null && throwable.getMessage().contains("transaction")) {
                            return Future.succeededFuture(RegistrationResult.failure(
                                "Database transaction failed during user registration"));
                        } else if (throwable.getMessage() != null && throwable.getMessage().contains("constraint")) {
                            return Future.succeededFuture(RegistrationResult.failure(
                                "Database constraint violation during registration"));
                        }
                        return Future.succeededFuture(RegistrationResult.failure(
                            "User registration failed: " + throwable.getMessage()));
                    });
            } else {
                logger.debug("No roles specified for user '{}', proceeding with standard save", username);
                return userRepository.saveWithRoles(user)
                    .compose(savedUser -> {
                        logger.info("User '{}' successfully registered without roles", username);
                        String usernameType = command.hasExplicitUsername() ? "explicit" : "generated";
                        return Future.succeededFuture(RegistrationResult.success(savedUser));
                    })
                    .recover(throwable -> {
                        logger.error("Failed to save user '{}': {}", username, throwable.getMessage(), throwable);
                        if (throwable.getMessage() != null && throwable.getMessage().contains("transaction")) {
                            return Future.succeededFuture(RegistrationResult.failure(
                                "Database transaction failed during user registration"));
                        } else if (throwable.getMessage() != null && throwable.getMessage().contains("constraint")) {
                            return Future.succeededFuture(RegistrationResult.failure(
                                "Database constraint violation during registration"));
                        }
                        return Future.succeededFuture(RegistrationResult.failure(
                            "User registration failed: " + throwable.getMessage()));
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
                Set<String> notFoundRoles = new HashSet<>();
                
                int index = 0;
                for (String roleName : roleNames) {
                    Optional<Role> roleOpt = compositeFuture.resultAt(index);
                    if (roleOpt.isPresent()) {
                        roles.add(roleOpt.get());
                        logger.debug("Successfully found role '{}' for user '{}'", roleName, user.getUsername());
                    } else {
                        notFoundRoles.add(roleName);
                        logger.warn("Role '{}' not found for user '{}' - role will be skipped", roleName, user.getUsername());
                    }
                    index++;
                }
                
                if (!notFoundRoles.isEmpty()) {
                    logger.warn("User '{}' registration proceeding with {} missing roles: {}", 
                        user.getUsername(), notFoundRoles.size(), notFoundRoles);
                }
                
                if (roles.isEmpty() && !roleNames.isEmpty()) {
                    logger.error("No valid roles found for user '{}' - all specified roles were invalid: {}", 
                        user.getUsername(), roleNames);
                    return Future.failedFuture(new IllegalArgumentException(
                        "No valid roles found - all specified roles are invalid: " + roleNames));
                }
                
                logger.debug("Adding {} valid roles to user '{}': {}", 
                    roles.size(), user.getUsername(), 
                    roles.stream().map(Role::getName).collect(Collectors.toSet()));
                
                roles.forEach(user::addRole);
                return Future.succeededFuture(user);
            })
            .recover(throwable -> {
                logger.error("Error during role assignment for user '{}': {}", user.getUsername(), throwable.getMessage(), throwable);
                return Future.failedFuture(new RuntimeException("Role assignment failed: " + throwable.getMessage(), throwable));
            });
    }

    @Override
    public Class<RegisterUserCommand> getCommandType() {
        return RegisterUserCommand.class;
    }
}
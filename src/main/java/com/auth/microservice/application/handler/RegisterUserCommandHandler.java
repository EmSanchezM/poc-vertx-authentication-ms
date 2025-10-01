package com.auth.microservice.application.handler;

import com.auth.microservice.common.cqrs.CommandHandler;
import com.auth.microservice.application.command.RegisterUserCommand;
import com.auth.microservice.application.result.RegistrationResult;
import com.auth.microservice.domain.exception.UserAlreadyExistsException;
import com.auth.microservice.domain.model.Email;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.port.RoleRepository;
import com.auth.microservice.domain.port.UserRepository;
import com.auth.microservice.domain.service.PasswordService;
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

    public RegisterUserCommandHandler(UserRepository userRepository, 
                                    RoleRepository roleRepository,
                                    PasswordService passwordService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordService = passwordService;
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
            
            // Check if user already exists
            return userRepository.existsByEmail(email)
                .compose(emailExists -> {
                    if (emailExists) {
                        logger.warn("Registration failed: Email already exists: {}", command.getEmail());
                        return Future.succeededFuture(RegistrationResult.failure("Email already exists"));
                    }
                    
                    return userRepository.existsByUsername(command.getUsername())
                        .compose(usernameExists -> {
                            if (usernameExists) {
                                logger.warn("Registration failed: Username already exists: {}", command.getUsername());
                                return Future.succeededFuture(RegistrationResult.failure("Username already exists"));
                            }
                            
                            // Hash password and create user
                            String hashedPassword = passwordService.hashPassword(command.getPassword());
                            User user = new User(
                                command.getUsername(),
                                email,
                                hashedPassword,
                                command.getFirstName(),
                                command.getLastName()
                            );
                            
                            // Assign roles if specified
                            if (!command.getRoleNames().isEmpty()) {
                                return assignRolesToUser(user, command.getRoleNames())
                                    .compose(userWithRoles -> userRepository.save(userWithRoles))
                                    .compose(savedUser -> {
                                        logger.info("User registered successfully: {}", savedUser.getId());
                                        return Future.succeededFuture(RegistrationResult.success(savedUser));
                                    });
                            } else {
                                return userRepository.save(user)
                                    .compose(savedUser -> {
                                        logger.info("User registered successfully: {}", savedUser.getId());
                                        return Future.succeededFuture(RegistrationResult.success(savedUser));
                                    });
                            }
                        });
                })
                .recover(throwable -> {
                    if (throwable instanceof IllegalArgumentException) {
                        logger.warn("Registration failed: Invalid input for email: {}", command.getEmail(), throwable);
                        return Future.succeededFuture(RegistrationResult.failure(throwable.getMessage()));
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
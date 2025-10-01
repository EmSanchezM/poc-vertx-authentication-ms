package com.auth.microservice.application.handler;

import com.auth.microservice.common.cqrs.CommandHandler;
import com.auth.microservice.application.command.CreateUserCommand;
import com.auth.microservice.application.result.UserCreationResult;
import com.auth.microservice.domain.event.UserCreatedEvent;
import com.auth.microservice.domain.exception.UserAlreadyExistsException;
import com.auth.microservice.domain.model.Email;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.port.EventPublisher;
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
 * Command handler for user creation operations.
 * Handles user creation with business validation and audit events.
 */
public class CreateUserCommandHandler implements CommandHandler<CreateUserCommand, UserCreationResult> {

    private static final Logger logger = LoggerFactory.getLogger(CreateUserCommandHandler.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordService passwordService;
    private final EventPublisher eventPublisher;

    public CreateUserCommandHandler(UserRepository userRepository, 
                                  RoleRepository roleRepository,
                                  PasswordService passwordService,
                                  EventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordService = passwordService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Handles user creation command.
     */
    @Override
    public Future<UserCreationResult> handle(CreateUserCommand command) {
        logger.info("Processing user creation for email: {}", command.getEmail());
        
        try {
            Email email = new Email(command.getEmail());
            
            // Check if user already exists
            return userRepository.existsByEmail(email)
                .compose(emailExists -> {
                    if (emailExists) {
                        logger.warn("User creation failed: Email already exists: {}", command.getEmail());
                        return Future.succeededFuture(UserCreationResult.failure("Email already exists"));
                    }
                    
                    return userRepository.existsByUsername(command.getUsername())
                        .compose(usernameExists -> {
                            if (usernameExists) {
                                logger.warn("User creation failed: Username already exists: {}", command.getUsername());
                                return Future.succeededFuture(UserCreationResult.failure("Username already exists"));
                            }
                            
                            // Generate a temporary password for admin-created users
                            String tempPassword = passwordService.generateTemporaryPassword();
                            String hashedPassword = passwordService.hashPassword(tempPassword);
                            
                            User user = new User(
                                command.getUsername(),
                                email,
                                hashedPassword,
                                command.getFirstName(),
                                command.getLastName()
                            );
                            
                            // Set active status
                            if (!command.isActive()) {
                                user.deactivate();
                            }
                            
                            // Assign roles if specified
                            if (!command.getRoleNames().isEmpty()) {
                                return assignRolesToUser(user, command.getRoleNames())
                                    .compose(userWithRoles -> userRepository.save(userWithRoles))
                                    .compose(savedUser -> publishUserCreatedEvent(savedUser, command))
                                    .compose(savedUser -> {
                                        logger.info("User created successfully: {}", savedUser.getId());
                                        return Future.succeededFuture(UserCreationResult.success(savedUser));
                                    });
                            } else {
                                return userRepository.save(user)
                                    .compose(savedUser -> publishUserCreatedEvent(savedUser, command))
                                    .compose(savedUser -> {
                                        logger.info("User created successfully: {}", savedUser.getId());
                                        return Future.succeededFuture(UserCreationResult.success(savedUser));
                                    });
                            }
                        });
                })
                .recover(throwable -> {
                    if (throwable instanceof IllegalArgumentException) {
                        logger.warn("User creation failed: Invalid input for email: {}", command.getEmail(), throwable);
                        return Future.succeededFuture(UserCreationResult.failure(throwable.getMessage()));
                    }
                    logger.error("User creation error for email: {}", command.getEmail(), throwable);
                    return Future.failedFuture(new UserAlreadyExistsException("User creation failed", throwable));
                });
                
        } catch (Exception e) {
            logger.error("User creation error for email: {}", command.getEmail(), e);
            return Future.failedFuture(new UserAlreadyExistsException("User creation failed", e));
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

    /**
     * Publishes user created event for audit purposes.
     */
    private Future<User> publishUserCreatedEvent(User user, CreateUserCommand command) {
        Set<String> roleNames = user.getRoles().stream()
            .map(Role::getName)
            .collect(Collectors.toSet());
            
        UserCreatedEvent event = new UserCreatedEvent(
            user.getId().toString(),
            user.getUsername(),
            user.getEmail().getValue(),
            user.getFirstName(),
            user.getLastName(),
            roleNames,
            command.getUserId(),
            command.getIpAddress(),
            command.getUserAgent()
        );
        
        return eventPublisher.publish(event)
            .compose(v -> Future.succeededFuture(user))
            .recover(throwable -> {
                logger.warn("Failed to publish user created event for user: {}", user.getId(), throwable);
                return Future.succeededFuture(user); // Don't fail the operation if event publishing fails
            });
    }

    @Override
    public Class<CreateUserCommand> getCommandType() {
        return CreateUserCommand.class;
    }
}
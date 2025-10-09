package com.auth.microservice.verification;

import com.auth.microservice.application.command.RegisterUserCommand;
import com.auth.microservice.application.handler.RegisterUserCommandHandler;
import com.auth.microservice.application.result.RegistrationResult;
import com.auth.microservice.domain.model.Email;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.model.UsernameValidationResult;
import com.auth.microservice.domain.port.RoleRepository;
import com.auth.microservice.domain.port.UserRepository;
import com.auth.microservice.domain.service.PasswordService;
import com.auth.microservice.domain.service.UsernameGenerationService;
import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Verification test for user role assignment functionality
 * This test verifies that the complete implementation works correctly
 */
@ExtendWith({VertxExtension.class, MockitoExtension.class})
class UserRoleAssignmentVerificationTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private UsernameGenerationService usernameGenerationService;

    private RegisterUserCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RegisterUserCommandHandler(
            userRepository, 
            roleRepository, 
            passwordService, 
            usernameGenerationService
        );
    }

    @Test
    void shouldSaveUserWithRolesSuccessfully(VertxTestContext testContext) {
        // Given
        RegisterUserCommand command = new RegisterUserCommand(
            null, // username - will be auto-generated
            "test@example.com",
            "StrongPassword123!",
            "John",
            "Doe",
            Set.of("USER", "ADMIN"),
            "192.168.1.1",
            "Mozilla/5.0"
        );

        Role userRole = new Role(UUID.randomUUID(), "USER", "User role", OffsetDateTime.now());
        Role adminRole = new Role(UUID.randomUUID(), "ADMIN", "Admin role", OffsetDateTime.now());

        User savedUser = new User(
            UUID.randomUUID(),
            "john.doe",
            new Email("test@example.com"),
            "hashedPassword",
            "John",
            "Doe",
            true,
            OffsetDateTime.now(),
            OffsetDateTime.now()
        );
        savedUser.addRole(userRole);
        savedUser.addRole(adminRole);

        // Mock password validation
        when(passwordService.validatePasswordStrength(anyString()))
            .thenReturn(new PasswordService.PasswordValidationResult(true, "Valid password"));
        
        // Mock email existence check
        when(userRepository.existsByEmail(any(Email.class)))
            .thenReturn(Future.succeededFuture(false));
        
        // Mock username generation
        when(usernameGenerationService.generateUsername("John", "Doe"))
            .thenReturn(Future.succeededFuture("john.doe"));
        
        // Mock username validation
        when(usernameGenerationService.validateUsername("john.doe"))
            .thenReturn(Future.succeededFuture(UsernameValidationResult.valid()));
        
        // Mock password hashing
        when(passwordService.hashPassword(anyString()))
            .thenReturn("hashedPassword");
        
        // Mock role finding
        when(roleRepository.findByNameWithPermissions("USER"))
            .thenReturn(Future.succeededFuture(Optional.of(userRole)));
        when(roleRepository.findByNameWithPermissions("ADMIN"))
            .thenReturn(Future.succeededFuture(Optional.of(adminRole)));
        
        // Mock saveWithRoles - this is the key method we're testing
        when(userRepository.saveWithRoles(any(User.class)))
            .thenReturn(Future.succeededFuture(savedUser));

        // When
        Future<RegistrationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(registrationResult -> testContext.verify(() -> {
            // Verify the registration was successful
            assertTrue(registrationResult.isSuccess());
            assertNotNull(registrationResult.getUser());
            
            // Verify the user has the correct roles
            User resultUser = registrationResult.getUser();
            assertEquals(2, resultUser.getRoles().size());
            
            // Verify specific roles are present
            Set<String> roleNames = Set.of(
                resultUser.getRoles().stream()
                    .map(Role::getName)
                    .toArray(String[]::new)
            );
            assertTrue(roleNames.contains("USER"));
            assertTrue(roleNames.contains("ADMIN"));
            
            // Verify that saveWithRoles was called instead of regular save
            verify(userRepository).saveWithRoles(any(User.class));
            verify(userRepository, never()).save(any(User.class));
            
            // Verify role assignment process was executed
            verify(roleRepository).findByNameWithPermissions("USER");
            verify(roleRepository).findByNameWithPermissions("ADMIN");
            
            testContext.completeNow();
        })));
    }

    @Test
    void shouldSaveUserWithoutRolesWhenNoneSpecified(VertxTestContext testContext) {
        // Given
        RegisterUserCommand command = new RegisterUserCommand(
            null, // username - will be auto-generated
            "test@example.com",
            "StrongPassword123!",
            "John",
            "Doe",
            Set.of(), // No roles specified
            "192.168.1.1",
            "Mozilla/5.0"
        );

        User savedUser = new User(
            UUID.randomUUID(),
            "john.doe",
            new Email("test@example.com"),
            "hashedPassword",
            "John",
            "Doe",
            true,
            OffsetDateTime.now(),
            OffsetDateTime.now()
        );

        // Mock password validation
        when(passwordService.validatePasswordStrength(anyString()))
            .thenReturn(new PasswordService.PasswordValidationResult(true, "Valid password"));
        
        // Mock email existence check
        when(userRepository.existsByEmail(any(Email.class)))
            .thenReturn(Future.succeededFuture(false));
        
        // Mock username generation
        when(usernameGenerationService.generateUsername("John", "Doe"))
            .thenReturn(Future.succeededFuture("john.doe"));
        
        // Mock username validation
        when(usernameGenerationService.validateUsername("john.doe"))
            .thenReturn(Future.succeededFuture(UsernameValidationResult.valid()));
        
        // Mock password hashing
        when(passwordService.hashPassword(anyString()))
            .thenReturn("hashedPassword");
        
        // Mock saveWithRoles for user without roles
        when(userRepository.saveWithRoles(any(User.class)))
            .thenReturn(Future.succeededFuture(savedUser));

        // When
        Future<RegistrationResult> result = handler.handle(command);

        // Then
        result.onComplete(testContext.succeeding(registrationResult -> testContext.verify(() -> {
            // Verify the registration was successful
            assertTrue(registrationResult.isSuccess());
            assertNotNull(registrationResult.getUser());
            
            // Verify the user has no roles
            User resultUser = registrationResult.getUser();
            assertEquals(0, resultUser.getRoles().size());
            
            // Verify that saveWithRoles was still called (for consistency)
            verify(userRepository).saveWithRoles(any(User.class));
            verify(userRepository, never()).save(any(User.class));
            
            // Verify no role repository calls were made
            verifyNoInteractions(roleRepository);
            
            testContext.completeNow();
        })));
    }
}
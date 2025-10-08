package com.auth.microservice.application.handler;

import com.auth.microservice.application.query.GetUserProfileQuery;
import com.auth.microservice.application.result.UserProfile;
import com.auth.microservice.domain.model.Email;
import com.auth.microservice.domain.model.Permission;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.port.UserRepository;
import com.auth.microservice.infrastructure.adapter.cache.RedisAuthCacheService;
import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
@DisplayName("GetUserProfileQueryHandler Tests")
class GetUserProfileQueryHandlerTest {

    @Mock
    private UserRepository userRepository;
    
    @Mock
    private RedisAuthCacheService cacheService;
    
    private GetUserProfileQueryHandler handler;
    private UUID testUserId;
    private User testUser;
    private Role testRole;
    private Permission testPermission;
    private GetUserProfileQuery testQuery;

    @BeforeEach
    void setUp() {
        handler = new GetUserProfileQueryHandler(userRepository, cacheService);
        testUserId = UUID.randomUUID();
        
        testUser = new User(
            testUserId,
            "testuser",
            new Email("test@example.com"),
            "hashedPassword",
            "John",
            "Doe",
            true,
            OffsetDateTime.now().minusDays(1),
            OffsetDateTime.now()
        );
        
        testPermission = new Permission(
            UUID.randomUUID(),
            "READ_USERS",
            "users",
            "read",
            "Permission to read users"
        );
        
        testRole = new Role(
            UUID.randomUUID(),
            "USER",
            "Standard user role",
            OffsetDateTime.now().minusDays(1)
        );
        testRole.addPermission(testPermission);
        testUser.addRole(testRole);
        
        testQuery = new GetUserProfileQuery("requester-id", testUserId);
    }

    @Nested
    @DisplayName("Handle GetUserProfileQuery")
    class HandleGetUserProfileQuery {

        @Test
        @DisplayName("Should return user profile without permissions when not requested")
        void shouldReturnUserProfileWithoutPermissionsWhenNotRequested(VertxTestContext testContext) {
            // Given
            when(userRepository.findByIdWithRoles(testUserId))
                .thenReturn(Future.succeededFuture(Optional.of(testUser)));

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isPresent());
                    UserProfile profile = result.get();
                    assertEquals(testUser.getId(), profile.getId());
                    assertEquals(testUser.getUsername(), profile.getUsername());
                    assertEquals(testUser.getEmail().getValue(), profile.getEmail());
                    assertEquals(testUser.getFirstName(), profile.getFirstName());
                    assertEquals(testUser.getLastName(), profile.getLastName());
                    assertEquals(testUser.isActive(), profile.isActive());
                    assertTrue(profile.getRoleNames().contains("USER"));
                    assertTrue(profile.getPermissions().isEmpty()); // No permissions requested
                    
                    verify(userRepository).findByIdWithRoles(testUserId);
                    verify(cacheService, never()).getCachedUserPermissions(any());
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should return user profile with permissions when requested and cached")
        void shouldReturnUserProfileWithPermissionsWhenRequestedAndCached(VertxTestContext testContext) {
            // Given
            GetUserProfileQuery queryWithPermissions = new GetUserProfileQuery("requester-id", testUserId, true);
            Set<Permission> cachedPermissions = Set.of(testPermission);
            
            when(userRepository.findByIdWithRoles(testUserId))
                .thenReturn(Future.succeededFuture(Optional.of(testUser)));
            when(cacheService.getCachedUserPermissions(testUserId))
                .thenReturn(Future.succeededFuture(Optional.of(cachedPermissions)));

            // When
            handler.handle(queryWithPermissions)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isPresent());
                    UserProfile profile = result.get();
                    assertEquals(testUser.getId(), profile.getId());
                    assertTrue(profile.getRoleNames().contains("USER"));
                    assertTrue(profile.getPermissions().contains("READ_USERS"));
                    
                    verify(userRepository).findByIdWithRoles(testUserId);
                    verify(cacheService).getCachedUserPermissions(testUserId);
                    verify(cacheService, never()).cacheUserPermissions(any(), any());
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should return user profile with permissions when requested and not cached")
        void shouldReturnUserProfileWithPermissionsWhenRequestedAndNotCached(VertxTestContext testContext) {
            // Given
            GetUserProfileQuery queryWithPermissions = new GetUserProfileQuery("requester-id", testUserId, true);
            
            when(userRepository.findByIdWithRoles(testUserId))
                .thenReturn(Future.succeededFuture(Optional.of(testUser)));
            when(cacheService.getCachedUserPermissions(testUserId))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(cacheService.cacheUserPermissions(eq(testUserId), any()))
                .thenReturn(Future.succeededFuture());

            // When
            handler.handle(queryWithPermissions)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isPresent());
                    UserProfile profile = result.get();
                    assertEquals(testUser.getId(), profile.getId());
                    assertTrue(profile.getRoleNames().contains("USER"));
                    assertTrue(profile.getPermissions().contains("READ_USERS"));
                    
                    verify(userRepository).findByIdWithRoles(testUserId);
                    verify(cacheService).getCachedUserPermissions(testUserId);
                    verify(cacheService).cacheUserPermissions(eq(testUserId), any());
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should return empty when user not found")
        void shouldReturnEmptyWhenUserNotFound(VertxTestContext testContext) {
            // Given
            when(userRepository.findByIdWithRoles(testUserId))
                .thenReturn(Future.succeededFuture(Optional.empty()));

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isEmpty());
                    verify(userRepository).findByIdWithRoles(testUserId);
                    verify(cacheService, never()).getCachedUserPermissions(any());
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should handle repository failure")
        void shouldHandleRepositoryFailure(VertxTestContext testContext) {
            // Given
            RuntimeException repositoryError = new RuntimeException("Database error");
            when(userRepository.findByIdWithRoles(testUserId))
                .thenReturn(Future.failedFuture(repositoryError));

            // When
            handler.handle(testQuery)
                .onComplete(testContext.failing(error -> testContext.verify(() -> {
                    // Then
                    assertEquals(repositoryError, error);
                    verify(userRepository).findByIdWithRoles(testUserId);
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should continue when caching fails")
        void shouldContinueWhenCachingFails(VertxTestContext testContext) {
            // Given
            GetUserProfileQuery queryWithPermissions = new GetUserProfileQuery("requester-id", testUserId, true);
            
            when(userRepository.findByIdWithRoles(testUserId))
                .thenReturn(Future.succeededFuture(Optional.of(testUser)));
            when(cacheService.getCachedUserPermissions(testUserId))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(cacheService.cacheUserPermissions(eq(testUserId), any()))
                .thenReturn(Future.failedFuture(new RuntimeException("Cache error")));

            // When
            handler.handle(queryWithPermissions)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isPresent());
                    UserProfile profile = result.get();
                    assertEquals(testUser.getId(), profile.getId());
                    assertTrue(profile.getPermissions().contains("READ_USERS"));
                    
                    verify(userRepository).findByIdWithRoles(testUserId);
                    verify(cacheService).getCachedUserPermissions(testUserId);
                    verify(cacheService).cacheUserPermissions(eq(testUserId), any());
                    testContext.completeNow();
                })));
        }
    }

    @Test
    @DisplayName("Should return correct query type")
    void shouldReturnCorrectQueryType() {
        assertEquals(GetUserProfileQuery.class, handler.getQueryType());
    }
}
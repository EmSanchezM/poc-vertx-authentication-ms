package com.auth.microservice.application.handler;

import com.auth.microservice.application.query.GetUserRolesQuery;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.port.RoleRepository;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
@DisplayName("GetUserRolesQueryHandler Tests")
class GetUserRolesQueryHandlerTest {

    @Mock
    private RoleRepository roleRepository;
    
    @Mock
    private RedisAuthCacheService cacheService;
    
    private GetUserRolesQueryHandler handler;
    private UUID testUserId;
    private List<Role> testRoles;
    private GetUserRolesQuery testQuery;

    @BeforeEach
    void setUp() {
        handler = new GetUserRolesQueryHandler(roleRepository, cacheService);
        testUserId = UUID.randomUUID();
        
        Role role1 = new Role(UUID.randomUUID(), "ADMIN", "Administrator role", OffsetDateTime.now());
        Role role2 = new Role(UUID.randomUUID(), "USER", "Regular user role", OffsetDateTime.now());
        testRoles = Arrays.asList(role1, role2);
        
        testQuery = new GetUserRolesQuery("requester-id", testUserId);
    }

    @Nested
    @DisplayName("Handle GetUserRolesQuery")
    class HandleGetUserRolesQuery {

        @Test
        @DisplayName("Should return user roles from cache when available")
        void shouldReturnUserRolesFromCacheWhenAvailable(VertxTestContext testContext) {
            // Given
            when(cacheService.getCachedUserRoles(testUserId))
                .thenReturn(Future.succeededFuture(Optional.of(testRoles)));

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertEquals(2, result.size());
                    assertEquals("ADMIN", result.get(0).getName());
                    assertEquals("USER", result.get(1).getName());
                    verify(cacheService).getCachedUserRoles(testUserId);
                    verify(roleRepository, never()).findByUserId(any());
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should load from repository and cache when not cached")
        void shouldLoadFromRepositoryAndCacheWhenNotCached(VertxTestContext testContext) {
            // Given
            when(cacheService.getCachedUserRoles(testUserId))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(roleRepository.findByUserId(testUserId))
                .thenReturn(Future.succeededFuture(testRoles));
            when(cacheService.cacheUserRoles(testUserId, testRoles))
                .thenReturn(Future.succeededFuture());

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertEquals(2, result.size());
                    assertEquals("ADMIN", result.get(0).getName());
                    verify(cacheService).getCachedUserRoles(testUserId);
                    verify(roleRepository).findByUserId(testUserId);
                    verify(cacheService).cacheUserRoles(testUserId, testRoles);
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should load roles with permissions when requested and not cached with permissions")
        void shouldLoadRolesWithPermissionsWhenRequestedAndNotCachedWithPermissions(VertxTestContext testContext) {
            // Given
            GetUserRolesQuery queryWithPermissions = new GetUserRolesQuery("requester-id", testUserId, true);
            List<Role> rolesWithoutPermissions = Arrays.asList(
                new Role(UUID.randomUUID(), "ADMIN", "Administrator role", OffsetDateTime.now()),
                new Role(UUID.randomUUID(), "USER", "Regular user role", OffsetDateTime.now())
            );
            
            when(cacheService.getCachedUserRoles(testUserId))
                .thenReturn(Future.succeededFuture(Optional.of(rolesWithoutPermissions)));
            when(roleRepository.findByUserIdWithPermissions(testUserId))
                .thenReturn(Future.succeededFuture(testRoles));
            when(cacheService.cacheUserRoles(testUserId, testRoles))
                .thenReturn(Future.succeededFuture());

            // When
            handler.handle(queryWithPermissions)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertEquals(2, result.size());
                    verify(roleRepository).findByUserIdWithPermissions(testUserId);
                    verify(cacheService).cacheUserRoles(testUserId, testRoles);
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should return cached roles with permissions when available")
        void shouldReturnCachedRolesWithPermissionsWhenAvailable(VertxTestContext testContext) {
            // Given
            GetUserRolesQuery queryWithPermissions = new GetUserRolesQuery("requester-id", testUserId, true);
            // Simulate roles with permissions already cached
            Role roleWithPermissions = new Role(UUID.randomUUID(), "ADMIN", "Administrator role", OffsetDateTime.now());
            roleWithPermissions.addPermission(new com.auth.microservice.domain.model.Permission(
                UUID.randomUUID(), "READ_USERS", "users", "read", "Read users permission"
            ));
            List<Role> rolesWithPermissions = Arrays.asList(roleWithPermissions);
            
            when(cacheService.getCachedUserRoles(testUserId))
                .thenReturn(Future.succeededFuture(Optional.of(rolesWithPermissions)));

            // When
            handler.handle(queryWithPermissions)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertEquals(1, result.size());
                    assertEquals("ADMIN", result.get(0).getName());
                    assertFalse(result.get(0).getPermissions().isEmpty());
                    verify(cacheService).getCachedUserRoles(testUserId);
                    verify(roleRepository, never()).findByUserIdWithPermissions(any());
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should continue even if caching fails")
        void shouldContinueEvenIfCachingFails(VertxTestContext testContext) {
            // Given
            when(cacheService.getCachedUserRoles(testUserId))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(roleRepository.findByUserId(testUserId))
                .thenReturn(Future.succeededFuture(testRoles));
            when(cacheService.cacheUserRoles(testUserId, testRoles))
                .thenReturn(Future.failedFuture(new RuntimeException("Cache error")));

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertEquals(2, result.size());
                    verify(roleRepository).findByUserId(testUserId);
                    verify(cacheService).cacheUserRoles(testUserId, testRoles);
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should handle repository failure")
        void shouldHandleRepositoryFailure(VertxTestContext testContext) {
            // Given
            RuntimeException repositoryError = new RuntimeException("Database error");
            when(cacheService.getCachedUserRoles(testUserId))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(roleRepository.findByUserId(testUserId))
                .thenReturn(Future.failedFuture(repositoryError));

            // When
            handler.handle(testQuery)
                .onComplete(testContext.failing(error -> testContext.verify(() -> {
                    // Then
                    assertEquals(repositoryError, error);
                    verify(roleRepository).findByUserId(testUserId);
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should return empty list when user has no roles")
        void shouldReturnEmptyListWhenUserHasNoRoles(VertxTestContext testContext) {
            // Given
            when(cacheService.getCachedUserRoles(testUserId))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(roleRepository.findByUserId(testUserId))
                .thenReturn(Future.succeededFuture(Arrays.asList()));
            when(cacheService.cacheUserRoles(eq(testUserId), any()))
                .thenReturn(Future.succeededFuture());

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isEmpty());
                    verify(roleRepository).findByUserId(testUserId);
                    testContext.completeNow();
                })));
        }
    }

    @Test
    @DisplayName("Should return correct query type")
    void shouldReturnCorrectQueryType() {
        assertEquals(GetUserRolesQuery.class, handler.getQueryType());
    }
}
package com.auth.microservice.application.handler;

import com.auth.microservice.application.query.GetUserPermissionsQuery;
import com.auth.microservice.domain.model.Permission;
import com.auth.microservice.domain.port.PermissionRepository;
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

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
@DisplayName("GetUserPermissionsQueryHandler Tests")
class GetUserPermissionsQueryHandlerTest {

    @Mock
    private PermissionRepository permissionRepository;
    
    @Mock
    private RedisAuthCacheService cacheService;
    
    private GetUserPermissionsQueryHandler handler;
    private UUID testUserId;
    private Set<Permission> testPermissions;
    private GetUserPermissionsQuery testQuery;

    @BeforeEach
    void setUp() {
        handler = new GetUserPermissionsQueryHandler(permissionRepository, cacheService);
        testUserId = UUID.randomUUID();
        
        testPermissions = new HashSet<>();
        testPermissions.add(new Permission("READ_USER", "user", "read", "Read user data"));
        testPermissions.add(new Permission("WRITE_USER", "user", "write", "Write user data"));
        testPermissions.add(new Permission("DELETE_USER", "user", "delete", "Delete user data"));
        
        testQuery = new GetUserPermissionsQuery("requester-id", testUserId, true, true);
    }

    @Nested
    @DisplayName("Cache Enabled Scenarios")
    class CacheEnabledScenarios {

        @Test
        @DisplayName("Should return cached permissions when cache hit")
        void shouldReturnCachedPermissionsWhenCacheHit(VertxTestContext testContext) {
            // Given
            when(cacheService.getCachedUserPermissions(testUserId))
                .thenReturn(Future.succeededFuture(Optional.of(testPermissions)));

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertEquals(testPermissions.size(), result.size());
                    assertTrue(result.containsAll(testPermissions));
                    
                    // Verify repository was not called
                    verify(permissionRepository, never()).findByUserId(any());
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should load from database and cache when cache miss")
        void shouldLoadFromDatabaseAndCacheWhenCacheMiss(VertxTestContext testContext) {
            // Given
            when(cacheService.getCachedUserPermissions(testUserId))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(permissionRepository.findByUserId(testUserId))
                .thenReturn(Future.succeededFuture(testPermissions));
            when(cacheService.cacheUserPermissions(testUserId, testPermissions))
                .thenReturn(Future.succeededFuture());

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertEquals(testPermissions.size(), result.size());
                    assertTrue(result.containsAll(testPermissions));
                    
                    // Verify database was called
                    verify(permissionRepository).findByUserId(testUserId);
                    
                    // Verify caching was attempted
                    verify(cacheService).cacheUserPermissions(testUserId, testPermissions);
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should return empty set when user has no permissions")
        void shouldReturnEmptySetWhenUserHasNoPermissions(VertxTestContext testContext) {
            // Given
            Set<Permission> emptyPermissions = new HashSet<>();
            when(cacheService.getCachedUserPermissions(testUserId))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(permissionRepository.findByUserId(testUserId))
                .thenReturn(Future.succeededFuture(emptyPermissions));
            when(cacheService.cacheUserPermissions(testUserId, emptyPermissions))
                .thenReturn(Future.succeededFuture());

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isEmpty());
                    
                    // Verify empty result was cached
                    verify(cacheService).cacheUserPermissions(testUserId, emptyPermissions);
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should continue when caching fails")
        void shouldContinueWhenCachingFails(VertxTestContext testContext) {
            // Given
            when(cacheService.getCachedUserPermissions(testUserId))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(permissionRepository.findByUserId(testUserId))
                .thenReturn(Future.succeededFuture(testPermissions));
            when(cacheService.cacheUserPermissions(testUserId, testPermissions))
                .thenReturn(Future.failedFuture(new RuntimeException("Cache error")));

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertEquals(testPermissions.size(), result.size());
                    assertTrue(result.containsAll(testPermissions));
                    
                    testContext.completeNow();
                })));
        }
    }

    @Nested
    @DisplayName("Cache Disabled Scenarios")
    class CacheDisabledScenarios {

        @Test
        @DisplayName("Should skip cache when cache disabled")
        void shouldSkipCacheWhenCacheDisabled(VertxTestContext testContext) {
            // Given
            GetUserPermissionsQuery noCacheQuery = new GetUserPermissionsQuery("requester-id", testUserId, true, false);
            when(permissionRepository.findByUserId(testUserId))
                .thenReturn(Future.succeededFuture(testPermissions));

            // When
            handler.handle(noCacheQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertEquals(testPermissions.size(), result.size());
                    assertTrue(result.containsAll(testPermissions));
                    
                    // Verify cache was not accessed
                    verify(cacheService, never()).getCachedUserPermissions(any());
                    verify(cacheService, never()).cacheUserPermissions(any(), any());
                    
                    // Verify repository was called directly
                    verify(permissionRepository).findByUserId(testUserId);
                    
                    testContext.completeNow();
                })));
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("Should fail when repository fails")
        void shouldFailWhenRepositoryFails(VertxTestContext testContext) {
            // Given
            when(cacheService.getCachedUserPermissions(testUserId))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(permissionRepository.findByUserId(testUserId))
                .thenReturn(Future.failedFuture(new RuntimeException("Database error")));

            // When
            handler.handle(testQuery)
                .onComplete(testContext.failing(throwable -> testContext.verify(() -> {
                    // Then
                    assertEquals("Database error", throwable.getMessage());
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should treat cache errors as cache miss")
        void shouldTreatCacheErrorsAsCacheMiss(VertxTestContext testContext) {
            // Given
            when(cacheService.getCachedUserPermissions(testUserId))
                .thenReturn(Future.failedFuture(new RuntimeException("Cache connection error")));
            when(permissionRepository.findByUserId(testUserId))
                .thenReturn(Future.succeededFuture(testPermissions));
            when(cacheService.cacheUserPermissions(testUserId, testPermissions))
                .thenReturn(Future.succeededFuture());

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertEquals(testPermissions.size(), result.size());
                    assertTrue(result.containsAll(testPermissions));
                    
                    // Verify fallback to database occurred
                    verify(permissionRepository).findByUserId(testUserId);
                    
                    testContext.completeNow();
                })));
        }
    }

    @Nested
    @DisplayName("Query Variations")
    class QueryVariations {

        @Test
        @DisplayName("Should handle inherited permissions flag")
        void shouldHandleInheritedPermissionsFlag(VertxTestContext testContext) {
            // Given
            GetUserPermissionsQuery inheritedQuery = new GetUserPermissionsQuery("requester-id", testUserId, false, true);
            when(cacheService.getCachedUserPermissions(testUserId))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(permissionRepository.findByUserId(testUserId))
                .thenReturn(Future.succeededFuture(testPermissions));
            when(cacheService.cacheUserPermissions(testUserId, testPermissions))
                .thenReturn(Future.succeededFuture());

            // When
            handler.handle(inheritedQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertEquals(testPermissions.size(), result.size());
                    assertFalse(inheritedQuery.isIncludeInherited());
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should handle convenience constructor")
        void shouldHandleConvenienceConstructor(VertxTestContext testContext) {
            // Given
            GetUserPermissionsQuery simpleQuery = new GetUserPermissionsQuery("requester-id", testUserId);
            when(cacheService.getCachedUserPermissions(testUserId))
                .thenReturn(Future.succeededFuture(Optional.of(testPermissions)));

            // When
            handler.handle(simpleQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertEquals(testPermissions.size(), result.size());
                    assertTrue(simpleQuery.isIncludeInherited());
                    assertTrue(simpleQuery.isUseCache());
                    
                    testContext.completeNow();
                })));
        }
    }

    @Test
    @DisplayName("Should return correct query type")
    void shouldReturnCorrectQueryType() {
        // When & Then
        assertEquals(GetUserPermissionsQuery.class, handler.getQueryType());
    }
}
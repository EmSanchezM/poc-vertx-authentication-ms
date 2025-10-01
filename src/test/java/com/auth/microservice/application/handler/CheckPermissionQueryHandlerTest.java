package com.auth.microservice.application.handler;

import com.auth.microservice.application.query.CheckPermissionQuery;
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
@DisplayName("CheckPermissionQueryHandler Tests")
class CheckPermissionQueryHandlerTest {

    @Mock
    private PermissionRepository permissionRepository;
    
    @Mock
    private RedisAuthCacheService cacheService;
    
    private CheckPermissionQueryHandler handler;
    private UUID testUserId;
    private Set<Permission> testPermissions;

    @BeforeEach
    void setUp() {
        handler = new CheckPermissionQueryHandler(permissionRepository, cacheService);
        testUserId = UUID.randomUUID();
        
        testPermissions = new HashSet<>();
        testPermissions.add(new Permission("READ_USER", "user", "read", "Read user data"));
        testPermissions.add(new Permission("WRITE_USER", "user", "write", "Write user data"));
        testPermissions.add(new Permission("DELETE_ADMIN", "admin", "delete", "Delete admin data"));
    }

    @Nested
    @DisplayName("Permission Check by Name")
    class PermissionCheckByName {

        @Test
        @DisplayName("Should return true when user has permission by name (cached)")
        void shouldReturnTrueWhenUserHasPermissionByNameCached(VertxTestContext testContext) {
            // Given
            CheckPermissionQuery query = new CheckPermissionQuery("requester-id", testUserId, "READ_USER", true);
            when(cacheService.getCachedPermissionCheck(testUserId, "name:READ_USER"))
                .thenReturn(Future.succeededFuture(Optional.of(true)));

            // When
            handler.handle(query)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result);
                    
                    // Verify repository was not called
                    verify(permissionRepository, never()).findByUserId(any());
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should return false when user lacks permission by name (cached)")
        void shouldReturnFalseWhenUserLacksPermissionByNameCached(VertxTestContext testContext) {
            // Given
            CheckPermissionQuery query = new CheckPermissionQuery("requester-id", testUserId, "SUPER_ADMIN", true);
            when(cacheService.getCachedPermissionCheck(testUserId, "name:SUPER_ADMIN"))
                .thenReturn(Future.succeededFuture(Optional.of(false)));

            // When
            handler.handle(query)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertFalse(result);
                    
                    // Verify repository was not called
                    verify(permissionRepository, never()).findByUserId(any());
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should check database and cache result when cache miss")
        void shouldCheckDatabaseAndCacheResultWhenCacheMiss(VertxTestContext testContext) {
            // Given
            CheckPermissionQuery query = new CheckPermissionQuery("requester-id", testUserId, "READ_USER", true);
            when(cacheService.getCachedPermissionCheck(testUserId, "name:READ_USER"))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(permissionRepository.findByUserId(testUserId))
                .thenReturn(Future.succeededFuture(testPermissions));
            when(cacheService.cachePermissionCheck(testUserId, "name:READ_USER", true))
                .thenReturn(Future.succeededFuture());

            // When
            handler.handle(query)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result);
                    
                    // Verify database was called
                    verify(permissionRepository).findByUserId(testUserId);
                    
                    // Verify caching was attempted
                    verify(cacheService).cachePermissionCheck(testUserId, "name:READ_USER", true);
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should return false when permission not found in database")
        void shouldReturnFalseWhenPermissionNotFoundInDatabase(VertxTestContext testContext) {
            // Given
            CheckPermissionQuery query = new CheckPermissionQuery("requester-id", testUserId, "NONEXISTENT_PERMISSION", true);
            when(cacheService.getCachedPermissionCheck(testUserId, "name:NONEXISTENT_PERMISSION"))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(permissionRepository.findByUserId(testUserId))
                .thenReturn(Future.succeededFuture(testPermissions));
            when(cacheService.cachePermissionCheck(testUserId, "name:NONEXISTENT_PERMISSION", false))
                .thenReturn(Future.succeededFuture());

            // When
            handler.handle(query)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertFalse(result);
                    
                    // Verify negative result was cached
                    verify(cacheService).cachePermissionCheck(testUserId, "name:NONEXISTENT_PERMISSION", false);
                    
                    testContext.completeNow();
                })));
        }
    }

    @Nested
    @DisplayName("Permission Check by Resource and Action")
    class PermissionCheckByResourceAction {

        @Test
        @DisplayName("Should return true when user has permission by resource and action")
        void shouldReturnTrueWhenUserHasPermissionByResourceAction(VertxTestContext testContext) {
            // Given
            CheckPermissionQuery query = new CheckPermissionQuery("requester-id", testUserId, "user", "read", true);
            when(cacheService.getCachedPermissionCheck(testUserId, "resource:user:action:read"))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(permissionRepository.findByUserId(testUserId))
                .thenReturn(Future.succeededFuture(testPermissions));
            when(cacheService.cachePermissionCheck(testUserId, "resource:user:action:read", true))
                .thenReturn(Future.succeededFuture());

            // When
            handler.handle(query)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result);
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should return false when user lacks permission by resource and action")
        void shouldReturnFalseWhenUserLacksPermissionByResourceAction(VertxTestContext testContext) {
            // Given
            CheckPermissionQuery query = new CheckPermissionQuery("requester-id", testUserId, "admin", "create", true);
            when(cacheService.getCachedPermissionCheck(testUserId, "resource:admin:action:create"))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(permissionRepository.findByUserId(testUserId))
                .thenReturn(Future.succeededFuture(testPermissions));
            when(cacheService.cachePermissionCheck(testUserId, "resource:admin:action:create", false))
                .thenReturn(Future.succeededFuture());

            // When
            handler.handle(query)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertFalse(result);
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should use convenience constructor correctly")
        void shouldUseConvenienceConstructorCorrectly(VertxTestContext testContext) {
            // Given
            CheckPermissionQuery query = new CheckPermissionQuery("requester-id", testUserId, "user", "read");
            when(cacheService.getCachedPermissionCheck(testUserId, "resource:user:action:read"))
                .thenReturn(Future.succeededFuture(Optional.of(true)));

            // When
            handler.handle(query)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result);
                    assertTrue(query.isUseCache());
                    assertTrue(query.isCheckByResourceAction());
                    assertFalse(query.isCheckByName());
                    
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
            CheckPermissionQuery query = new CheckPermissionQuery("requester-id", testUserId, "READ_USER", false);
            when(permissionRepository.findByUserId(testUserId))
                .thenReturn(Future.succeededFuture(testPermissions));

            // When
            handler.handle(query)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result);
                    
                    // Verify cache was not accessed
                    verify(cacheService, never()).getCachedPermissionCheck(any(), any());
                    verify(cacheService, never()).cachePermissionCheck(any(), any(), anyBoolean());
                    
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
        @DisplayName("Should fail with invalid query (neither name nor resource/action)")
        void shouldFailWithInvalidQuery(VertxTestContext testContext) {
            // Given - Create a mock query that returns false for both check methods
            CheckPermissionQuery invalidQuery = mock(CheckPermissionQuery.class);
            when(invalidQuery.getTargetUserId()).thenReturn(testUserId);
            when(invalidQuery.isUseCache()).thenReturn(false);
            when(invalidQuery.isCheckByName()).thenReturn(false);
            when(invalidQuery.isCheckByResourceAction()).thenReturn(false);

            // When
            handler.handle(invalidQuery)
                .onComplete(testContext.failing(throwable -> testContext.verify(() -> {
                    // Then
                    assertTrue(throwable instanceof IllegalArgumentException);
                    assertEquals("Invalid permission check query", throwable.getMessage());
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should fail when repository fails")
        void shouldFailWhenRepositoryFails(VertxTestContext testContext) {
            // Given
            CheckPermissionQuery query = new CheckPermissionQuery("requester-id", testUserId, "READ_USER", true);
            when(cacheService.getCachedPermissionCheck(testUserId, "name:READ_USER"))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(permissionRepository.findByUserId(testUserId))
                .thenReturn(Future.failedFuture(new RuntimeException("Database error")));

            // When
            handler.handle(query)
                .onComplete(testContext.failing(throwable -> testContext.verify(() -> {
                    // Then
                    assertEquals("Database error", throwable.getMessage());
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should continue when caching fails")
        void shouldContinueWhenCachingFails(VertxTestContext testContext) {
            // Given
            CheckPermissionQuery query = new CheckPermissionQuery("requester-id", testUserId, "READ_USER", true);
            when(cacheService.getCachedPermissionCheck(testUserId, "name:READ_USER"))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(permissionRepository.findByUserId(testUserId))
                .thenReturn(Future.succeededFuture(testPermissions));
            when(cacheService.cachePermissionCheck(testUserId, "name:READ_USER", true))
                .thenReturn(Future.failedFuture(new RuntimeException("Cache error")));

            // When
            handler.handle(query)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result);
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should treat cache errors as cache miss")
        void shouldTreatCacheErrorsAsCacheMiss(VertxTestContext testContext) {
            // Given
            CheckPermissionQuery query = new CheckPermissionQuery("requester-id", testUserId, "READ_USER", true);
            when(cacheService.getCachedPermissionCheck(testUserId, "name:READ_USER"))
                .thenReturn(Future.failedFuture(new RuntimeException("Cache connection error")));
            when(permissionRepository.findByUserId(testUserId))
                .thenReturn(Future.succeededFuture(testPermissions));
            when(cacheService.cachePermissionCheck(testUserId, "name:READ_USER", true))
                .thenReturn(Future.succeededFuture());

            // When
            handler.handle(query)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result);
                    
                    // Verify fallback to database occurred
                    verify(permissionRepository).findByUserId(testUserId);
                    
                    testContext.completeNow();
                })));
        }
    }

    @Nested
    @DisplayName("Permission Key Generation")
    class PermissionKeyGeneration {

        @Test
        @DisplayName("Should generate correct key for name-based check")
        void shouldGenerateCorrectKeyForNameBasedCheck(VertxTestContext testContext) {
            // Given
            CheckPermissionQuery query = new CheckPermissionQuery("requester-id", testUserId, "READ_USER", true);
            when(cacheService.getCachedPermissionCheck(testUserId, "name:READ_USER"))
                .thenReturn(Future.succeededFuture(Optional.of(true)));

            // When
            handler.handle(query)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result);
                    verify(cacheService).getCachedPermissionCheck(testUserId, "name:READ_USER");
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should generate correct key for resource/action-based check")
        void shouldGenerateCorrectKeyForResourceActionBasedCheck(VertxTestContext testContext) {
            // Given
            CheckPermissionQuery query = new CheckPermissionQuery("requester-id", testUserId, "user", "read", true);
            when(cacheService.getCachedPermissionCheck(testUserId, "resource:user:action:read"))
                .thenReturn(Future.succeededFuture(Optional.of(true)));

            // When
            handler.handle(query)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result);
                    verify(cacheService).getCachedPermissionCheck(testUserId, "resource:user:action:read");
                    
                    testContext.completeNow();
                })));
        }
    }

    @Test
    @DisplayName("Should return correct query type")
    void shouldReturnCorrectQueryType() {
        // When & Then
        assertEquals(CheckPermissionQuery.class, handler.getQueryType());
    }
}
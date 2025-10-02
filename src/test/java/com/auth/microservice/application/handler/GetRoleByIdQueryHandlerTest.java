package com.auth.microservice.application.handler;

import com.auth.microservice.application.query.GetRoleByIdQuery;
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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
@DisplayName("GetRoleByIdQueryHandler Tests")
class GetRoleByIdQueryHandlerTest {

    @Mock
    private RoleRepository roleRepository;
    
    @Mock
    private RedisAuthCacheService cacheService;
    
    private GetRoleByIdQueryHandler handler;
    private UUID testRoleId;
    private Role testRole;
    private GetRoleByIdQuery testQuery;

    @BeforeEach
    void setUp() {
        handler = new GetRoleByIdQueryHandler(roleRepository, cacheService);
        testRoleId = UUID.randomUUID();
        testRole = new Role(testRoleId, "ADMIN", "Administrator role", LocalDateTime.now());
        testQuery = new GetRoleByIdQuery("requester-id", testRoleId);
    }

    @Nested
    @DisplayName("Handle GetRoleByIdQuery")
    class HandleGetRoleByIdQuery {

        @Test
        @DisplayName("Should return role from cache when available")
        void shouldReturnRoleFromCacheWhenAvailable(VertxTestContext testContext) {
            // Given
            when(cacheService.getCachedRoleById(testRoleId))
                .thenReturn(Future.succeededFuture(Optional.of(Optional.of(testRole))));

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isPresent());
                    assertEquals(testRole.getId(), result.get().getId());
                    assertEquals("ADMIN", result.get().getName());
                    verify(cacheService).getCachedRoleById(testRoleId);
                    verify(roleRepository, never()).findById(any());
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should return empty from cache when role not found")
        void shouldReturnEmptyFromCacheWhenRoleNotFound(VertxTestContext testContext) {
            // Given
            when(cacheService.getCachedRoleById(testRoleId))
                .thenReturn(Future.succeededFuture(Optional.of(Optional.empty())));

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isEmpty());
                    verify(cacheService).getCachedRoleById(testRoleId);
                    verify(roleRepository, never()).findById(any());
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should load from repository and cache when not cached")
        void shouldLoadFromRepositoryAndCacheWhenNotCached(VertxTestContext testContext) {
            // Given
            when(cacheService.getCachedRoleById(testRoleId))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(roleRepository.findById(testRoleId))
                .thenReturn(Future.succeededFuture(Optional.of(testRole)));
            when(cacheService.cacheRoleById(testRoleId, Optional.of(testRole)))
                .thenReturn(Future.succeededFuture());

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isPresent());
                    assertEquals(testRole.getId(), result.get().getId());
                    verify(cacheService).getCachedRoleById(testRoleId);
                    verify(roleRepository).findById(testRoleId);
                    verify(cacheService).cacheRoleById(testRoleId, Optional.of(testRole));
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should load role with permissions when requested and not cached with permissions")
        void shouldLoadRoleWithPermissionsWhenRequestedAndNotCachedWithPermissions(VertxTestContext testContext) {
            // Given
            GetRoleByIdQuery queryWithPermissions = new GetRoleByIdQuery("requester-id", testRoleId, true);
            Role roleWithoutPermissions = new Role(testRoleId, "ADMIN", "Administrator role", LocalDateTime.now());
            
            when(cacheService.getCachedRoleById(testRoleId))
                .thenReturn(Future.succeededFuture(Optional.of(Optional.of(roleWithoutPermissions))));
            when(roleRepository.findByIdWithPermissions(testRoleId))
                .thenReturn(Future.succeededFuture(Optional.of(testRole)));
            when(cacheService.cacheRoleById(testRoleId, Optional.of(testRole)))
                .thenReturn(Future.succeededFuture());

            // When
            handler.handle(queryWithPermissions)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isPresent());
                    assertEquals(testRole.getId(), result.get().getId());
                    verify(roleRepository).findByIdWithPermissions(testRoleId);
                    verify(cacheService).cacheRoleById(testRoleId, Optional.of(testRole));
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should continue even if caching fails")
        void shouldContinueEvenIfCachingFails(VertxTestContext testContext) {
            // Given
            when(cacheService.getCachedRoleById(testRoleId))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(roleRepository.findById(testRoleId))
                .thenReturn(Future.succeededFuture(Optional.of(testRole)));
            when(cacheService.cacheRoleById(testRoleId, Optional.of(testRole)))
                .thenReturn(Future.failedFuture(new RuntimeException("Cache error")));

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isPresent());
                    assertEquals(testRole.getId(), result.get().getId());
                    verify(roleRepository).findById(testRoleId);
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should handle repository failure")
        void shouldHandleRepositoryFailure(VertxTestContext testContext) {
            // Given
            RuntimeException repositoryError = new RuntimeException("Database error");
            when(cacheService.getCachedRoleById(testRoleId))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(roleRepository.findById(testRoleId))
                .thenReturn(Future.failedFuture(repositoryError));

            // When
            handler.handle(testQuery)
                .onComplete(testContext.failing(error -> testContext.verify(() -> {
                    // Then
                    assertEquals(repositoryError, error);
                    verify(roleRepository).findById(testRoleId);
                    testContext.completeNow();
                })));
        }
    }

    @Test
    @DisplayName("Should return correct query type")
    void shouldReturnCorrectQueryType() {
        assertEquals(GetRoleByIdQuery.class, handler.getQueryType());
    }
}
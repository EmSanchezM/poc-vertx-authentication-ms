package com.auth.microservice.application.handler;

import com.auth.microservice.application.query.GetRolesQuery;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.port.Pagination;
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
@DisplayName("GetRolesQueryHandler Tests")
class GetRolesQueryHandlerTest {

    @Mock
    private RoleRepository roleRepository;
    
    @Mock
    private RedisAuthCacheService cacheService;
    
    private GetRolesQueryHandler handler;
    private List<Role> testRoles;
    private Pagination testPagination;
    private GetRolesQuery testQuery;

    @BeforeEach
    void setUp() {
        handler = new GetRolesQueryHandler(roleRepository, cacheService);
        
        Role role1 = new Role(UUID.randomUUID(), "ADMIN", "Administrator role", OffsetDateTime.now());
        Role role2 = new Role(UUID.randomUUID(), "USER", "Regular user role", OffsetDateTime.now());
        testRoles = Arrays.asList(role1, role2);
        
        testPagination = new Pagination(0, 10);
        testQuery = new GetRolesQuery("requester-id", testPagination);
    }

    @Nested
    @DisplayName("Handle GetRolesQuery")
    class HandleGetRolesQuery {

        @Test
        @DisplayName("Should return roles from cache when available")
        void shouldReturnRolesFromCacheWhenAvailable(VertxTestContext testContext) {
            // Given
            String expectedCacheKey = "page_0_size_10_permissions_false";
            when(cacheService.getCachedRolesList(expectedCacheKey))
                .thenReturn(Future.succeededFuture(Optional.of(testRoles)));

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertEquals(2, result.size());
                    assertEquals("ADMIN", result.get(0).getName());
                    assertEquals("USER", result.get(1).getName());
                    verify(cacheService).getCachedRolesList(expectedCacheKey);
                    verify(roleRepository, never()).findAll(any());
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should load from repository and cache when not cached")
        void shouldLoadFromRepositoryAndCacheWhenNotCached(VertxTestContext testContext) {
            // Given
            String expectedCacheKey = "page_0_size_10_permissions_false";
            when(cacheService.getCachedRolesList(expectedCacheKey))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(roleRepository.findAll(testPagination))
                .thenReturn(Future.succeededFuture(testRoles));
            when(cacheService.cacheRolesList(expectedCacheKey, testRoles))
                .thenReturn(Future.succeededFuture());

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertEquals(2, result.size());
                    assertEquals("ADMIN", result.get(0).getName());
                    verify(cacheService).getCachedRolesList(expectedCacheKey);
                    verify(roleRepository).findAll(testPagination);
                    verify(cacheService).cacheRolesList(expectedCacheKey, testRoles);
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should continue even if caching fails")
        void shouldContinueEvenIfCachingFails(VertxTestContext testContext) {
            // Given
            String expectedCacheKey = "page_0_size_10_permissions_false";
            when(cacheService.getCachedRolesList(expectedCacheKey))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(roleRepository.findAll(testPagination))
                .thenReturn(Future.succeededFuture(testRoles));
            when(cacheService.cacheRolesList(expectedCacheKey, testRoles))
                .thenReturn(Future.failedFuture(new RuntimeException("Cache error")));

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertEquals(2, result.size());
                    verify(roleRepository).findAll(testPagination);
                    verify(cacheService).cacheRolesList(expectedCacheKey, testRoles);
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should load roles with permissions when requested")
        void shouldLoadRolesWithPermissionsWhenRequested(VertxTestContext testContext) {
            // Given
            GetRolesQuery queryWithPermissions = new GetRolesQuery("requester-id", testPagination, true);
            String expectedCacheKey = "page_0_size_10_permissions_true";
            
            when(cacheService.getCachedRolesList(expectedCacheKey))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(roleRepository.findAll(testPagination))
                .thenReturn(Future.succeededFuture(testRoles));
            when(roleRepository.findByIdWithPermissions(any()))
                .thenReturn(Future.succeededFuture(Optional.of(testRoles.get(0))))
                .thenReturn(Future.succeededFuture(Optional.of(testRoles.get(1))));
            when(cacheService.cacheRolesList(eq(expectedCacheKey), any()))
                .thenReturn(Future.succeededFuture());

            // When
            handler.handle(queryWithPermissions)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertEquals(2, result.size());
                    verify(roleRepository).findAll(testPagination);
                    verify(roleRepository, times(2)).findByIdWithPermissions(any());
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should handle repository failure")
        void shouldHandleRepositoryFailure(VertxTestContext testContext) {
            // Given
            String expectedCacheKey = "page_0_size_10_permissions_false";
            RuntimeException repositoryError = new RuntimeException("Database error");
            when(cacheService.getCachedRolesList(expectedCacheKey))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(roleRepository.findAll(testPagination))
                .thenReturn(Future.failedFuture(repositoryError));

            // When
            handler.handle(testQuery)
                .onComplete(testContext.failing(error -> testContext.verify(() -> {
                    // Then
                    assertEquals(repositoryError, error);
                    verify(roleRepository).findAll(testPagination);
                    testContext.completeNow();
                })));
        }
    }

    @Test
    @DisplayName("Should return correct query type")
    void shouldReturnCorrectQueryType() {
        assertEquals(GetRolesQuery.class, handler.getQueryType());
    }
}
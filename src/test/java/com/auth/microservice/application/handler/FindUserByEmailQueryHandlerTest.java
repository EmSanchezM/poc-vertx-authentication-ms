package com.auth.microservice.application.handler;

import com.auth.microservice.application.query.FindUserByEmailQuery;
import com.auth.microservice.domain.model.Email;
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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
@DisplayName("FindUserByEmailQueryHandler Tests")
class FindUserByEmailQueryHandlerTest {

    @Mock
    private UserRepository userRepository;
    
    @Mock
    private RedisAuthCacheService cacheService;
    
    private FindUserByEmailQueryHandler handler;
    private Email testEmail;
    private User testUser;
    private FindUserByEmailQuery testQuery;

    @BeforeEach
    void setUp() {
        handler = new FindUserByEmailQueryHandler(userRepository, cacheService);
        testEmail = new Email("test@example.com");
        testUser = new User(
            UUID.randomUUID(),
            "testuser",
            testEmail,
            "hashedPassword",
            "John",
            "Doe",
            true,
            LocalDateTime.now().minusDays(1),
            LocalDateTime.now()
        );
        testQuery = new FindUserByEmailQuery("requester-id", testEmail, false, false);
    }

    @Nested
    @DisplayName("Cache Hit Scenarios")
    class CacheHitScenarios {

        @Test
        @DisplayName("Should return cached user when cache hit occurs")
        void shouldReturnCachedUserWhenCacheHit(VertxTestContext testContext) {
            // Given
            when(cacheService.getCachedUserByEmail(testEmail.getValue()))
                .thenReturn(Future.succeededFuture(Optional.of(Optional.of(testUser))));

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isPresent());
                    assertEquals(testUser.getId(), result.get().getId());
                    assertEquals(testUser.getEmail(), result.get().getEmail());
                    
                    // Verify repository was not called
                    verify(userRepository, never()).findByEmail(any());
                    verify(userRepository, never()).findByEmailWithRoles(any());
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should return empty when cached as not found")
        void shouldReturnEmptyWhenCachedAsNotFound(VertxTestContext testContext) {
            // Given
            when(cacheService.getCachedUserByEmail(testEmail.getValue()))
                .thenReturn(Future.succeededFuture(Optional.of(Optional.empty())));

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertFalse(result.isPresent());
                    
                    // Verify repository was not called
                    verify(userRepository, never()).findByEmail(any());
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should load roles when cache hit but roles are requested")
        void shouldLoadRolesWhenCacheHitButRolesRequested(VertxTestContext testContext) {
            // Given
            FindUserByEmailQuery queryWithRoles = new FindUserByEmailQuery("requester-id", testEmail, true, false);
            when(cacheService.getCachedUserByEmail(testEmail.getValue()))
                .thenReturn(Future.succeededFuture(Optional.of(Optional.of(testUser))));
            when(userRepository.findByIdWithRoles(testUser.getId()))
                .thenReturn(Future.succeededFuture(Optional.of(testUser)));

            // When
            handler.handle(queryWithRoles)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isPresent());
                    assertEquals(testUser.getId(), result.get().getId());
                    
                    // Verify roles were loaded
                    verify(userRepository).findByIdWithRoles(testUser.getId());
                    
                    testContext.completeNow();
                })));
        }
    }

    @Nested
    @DisplayName("Cache Miss Scenarios")
    class CacheMissScenarios {

        @Test
        @DisplayName("Should load from database and cache result when cache miss")
        void shouldLoadFromDatabaseAndCacheWhenCacheMiss(VertxTestContext testContext) {
            // Given
            when(cacheService.getCachedUserByEmail(testEmail.getValue()))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(userRepository.findByEmail(testEmail))
                .thenReturn(Future.succeededFuture(Optional.of(testUser)));
            when(cacheService.cacheUserByEmail(testEmail.getValue(), Optional.of(testUser)))
                .thenReturn(Future.succeededFuture());

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isPresent());
                    assertEquals(testUser.getId(), result.get().getId());
                    
                    // Verify database was called
                    verify(userRepository).findByEmail(testEmail);
                    
                    // Verify caching was attempted
                    verify(cacheService).cacheUserByEmail(testEmail.getValue(), Optional.of(testUser));
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should load with roles when requested and cache miss")
        void shouldLoadWithRolesWhenRequestedAndCacheMiss(VertxTestContext testContext) {
            // Given
            FindUserByEmailQuery queryWithRoles = new FindUserByEmailQuery("requester-id", testEmail, true, false);
            when(cacheService.getCachedUserByEmail(testEmail.getValue()))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(userRepository.findByEmailWithRoles(testEmail))
                .thenReturn(Future.succeededFuture(Optional.of(testUser)));
            when(cacheService.cacheUserByEmail(testEmail.getValue(), Optional.of(testUser)))
                .thenReturn(Future.succeededFuture());

            // When
            handler.handle(queryWithRoles)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isPresent());
                    assertEquals(testUser.getId(), result.get().getId());
                    
                    // Verify correct repository method was called
                    verify(userRepository).findByEmailWithRoles(testEmail);
                    verify(userRepository, never()).findByEmail(any());
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should cache not found result when user not exists")
        void shouldCacheNotFoundWhenUserNotExists(VertxTestContext testContext) {
            // Given
            when(cacheService.getCachedUserByEmail(testEmail.getValue()))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(userRepository.findByEmail(testEmail))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(cacheService.cacheUserByEmail(testEmail.getValue(), Optional.empty()))
                .thenReturn(Future.succeededFuture());

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertFalse(result.isPresent());
                    
                    // Verify caching was attempted for not found result
                    verify(cacheService).cacheUserByEmail(testEmail.getValue(), Optional.empty());
                    
                    testContext.completeNow();
                })));
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("Should continue when caching fails")
        void shouldContinueWhenCachingFails(VertxTestContext testContext) {
            // Given
            when(cacheService.getCachedUserByEmail(testEmail.getValue()))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(userRepository.findByEmail(testEmail))
                .thenReturn(Future.succeededFuture(Optional.of(testUser)));
            when(cacheService.cacheUserByEmail(testEmail.getValue(), Optional.of(testUser)))
                .thenReturn(Future.failedFuture(new RuntimeException("Cache error")));

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isPresent());
                    assertEquals(testUser.getId(), result.get().getId());
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should fail when repository fails")
        void shouldFailWhenRepositoryFails(VertxTestContext testContext) {
            // Given
            when(cacheService.getCachedUserByEmail(testEmail.getValue()))
                .thenReturn(Future.succeededFuture(Optional.empty()));
            when(userRepository.findByEmail(testEmail))
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
            when(cacheService.getCachedUserByEmail(testEmail.getValue()))
                .thenReturn(Future.failedFuture(new RuntimeException("Cache connection error")));
            when(userRepository.findByEmail(testEmail))
                .thenReturn(Future.succeededFuture(Optional.of(testUser)));
            when(cacheService.cacheUserByEmail(testEmail.getValue(), Optional.of(testUser)))
                .thenReturn(Future.succeededFuture());

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isPresent());
                    assertEquals(testUser.getId(), result.get().getId());
                    
                    // Verify fallback to database occurred
                    verify(userRepository).findByEmail(testEmail);
                    
                    testContext.completeNow();
                })));
        }
    }

    @Test
    @DisplayName("Should return correct query type")
    void shouldReturnCorrectQueryType() {
        // When & Then
        assertEquals(FindUserByEmailQuery.class, handler.getQueryType());
    }
}
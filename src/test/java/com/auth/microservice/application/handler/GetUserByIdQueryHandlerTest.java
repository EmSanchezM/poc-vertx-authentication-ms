package com.auth.microservice.application.handler;

import com.auth.microservice.application.query.GetUserByIdQuery;
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

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
@DisplayName("GetUserByIdQueryHandler Tests")
class GetUserByIdQueryHandlerTest {

    @Mock
    private UserRepository userRepository;
    
    @Mock
    private RedisAuthCacheService cacheService;
    
    private GetUserByIdQueryHandler handler;
    private UUID testUserId;
    private User testUser;
    private GetUserByIdQuery testQuery;

    @BeforeEach
    void setUp() {
        handler = new GetUserByIdQueryHandler(userRepository, cacheService);
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
        testQuery = new GetUserByIdQuery("requester-id", testUserId);
    }

    @Nested
    @DisplayName("Handle GetUserByIdQuery")
    class HandleGetUserByIdQuery {

        @Test
        @DisplayName("Should return user when found without roles")
        void shouldReturnUserWhenFoundWithoutRoles(VertxTestContext testContext) {
            // Given
            when(userRepository.findById(testUserId))
                .thenReturn(Future.succeededFuture(Optional.of(testUser)));

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isPresent());
                    assertEquals(testUser.getId(), result.get().getId());
                    assertEquals(testUser.getEmail().getValue(), result.get().getEmail().getValue());
                    verify(userRepository).findById(testUserId);
                    verify(userRepository, never()).findByIdWithRoles(any());
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should return user when found with roles")
        void shouldReturnUserWhenFoundWithRoles(VertxTestContext testContext) {
            // Given
            GetUserByIdQuery queryWithRoles = new GetUserByIdQuery("requester-id", testUserId, true, false);
            when(userRepository.findByIdWithRoles(testUserId))
                .thenReturn(Future.succeededFuture(Optional.of(testUser)));

            // When
            handler.handle(queryWithRoles)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isPresent());
                    assertEquals(testUser.getId(), result.get().getId());
                    verify(userRepository).findByIdWithRoles(testUserId);
                    verify(userRepository, never()).findById(any());
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should return empty when user not found")
        void shouldReturnEmptyWhenUserNotFound(VertxTestContext testContext) {
            // Given
            when(userRepository.findById(testUserId))
                .thenReturn(Future.succeededFuture(Optional.empty()));

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isEmpty());
                    verify(userRepository).findById(testUserId);
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should handle repository failure")
        void shouldHandleRepositoryFailure(VertxTestContext testContext) {
            // Given
            RuntimeException repositoryError = new RuntimeException("Database error");
            when(userRepository.findById(testUserId))
                .thenReturn(Future.failedFuture(repositoryError));

            // When
            handler.handle(testQuery)
                .onComplete(testContext.failing(error -> testContext.verify(() -> {
                    // Then
                    assertEquals(repositoryError, error);
                    verify(userRepository).findById(testUserId);
                    testContext.completeNow();
                })));
        }
    }

    @Test
    @DisplayName("Should return correct query type")
    void shouldReturnCorrectQueryType() {
        assertEquals(GetUserByIdQuery.class, handler.getQueryType());
    }
}
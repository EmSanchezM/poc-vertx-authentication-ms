package com.auth.microservice.application.handler;

import com.auth.microservice.application.query.GetUsersQuery;
import com.auth.microservice.domain.model.Email;
import com.auth.microservice.domain.model.User;
import com.auth.microservice.domain.port.Pagination;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
@DisplayName("GetUsersQueryHandler Tests")
class GetUsersQueryHandlerTest {

    @Mock
    private UserRepository userRepository;
    
    @Mock
    private RedisAuthCacheService cacheService;
    
    private GetUsersQueryHandler handler;
    private Pagination testPagination;
    private List<User> testUsers;
    private GetUsersQuery testQuery;

    @BeforeEach
    void setUp() {
        handler = new GetUsersQueryHandler(userRepository, cacheService);
        testPagination = new Pagination(0, 10);
        
        testUsers = Arrays.asList(
            new User(
                UUID.randomUUID(),
                "user1",
                new Email("user1@example.com"),
                "hashedPassword1",
                "John",
                "Doe",
                true,
                OffsetDateTime.now().minusDays(2),
                OffsetDateTime.now().minusDays(1)
            ),
            new User(
                UUID.randomUUID(),
                "user2",
                new Email("user2@example.com"),
                "hashedPassword2",
                "Jane",
                "Smith",
                true,
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now()
            )
        );
        
        testQuery = new GetUsersQuery("requester-id", testPagination);
    }

    @Nested
    @DisplayName("Handle GetUsersQuery")
    class HandleGetUsersQuery {

        @Test
        @DisplayName("Should return active users when includeInactive is false")
        void shouldReturnActiveUsersWhenIncludeInactiveIsFalse(VertxTestContext testContext) {
            // Given
            when(userRepository.findActiveUsers(testPagination))
                .thenReturn(Future.succeededFuture(testUsers));

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertEquals(2, result.size());
                    assertEquals(testUsers.get(0).getId(), result.get(0).getId());
                    assertEquals(testUsers.get(1).getId(), result.get(1).getId());
                    verify(userRepository).findActiveUsers(testPagination);
                    verify(userRepository, never()).findAll(any());
                    verify(userRepository, never()).searchUsers(any(), any(), anyBoolean());
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should return all users when includeInactive is true")
        void shouldReturnAllUsersWhenIncludeInactiveIsTrue(VertxTestContext testContext) {
            // Given
            GetUsersQuery queryWithInactive = new GetUsersQuery("requester-id", testPagination, true);
            when(userRepository.findAll(testPagination))
                .thenReturn(Future.succeededFuture(testUsers));

            // When
            handler.handle(queryWithInactive)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertEquals(2, result.size());
                    verify(userRepository).findAll(testPagination);
                    verify(userRepository, never()).findActiveUsers(any());
                    verify(userRepository, never()).searchUsers(any(), any(), anyBoolean());
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should use search when search term is provided")
        void shouldUseSearchWhenSearchTermIsProvided(VertxTestContext testContext) {
            // Given
            String searchTerm = "john";
            GetUsersQuery searchQuery = new GetUsersQuery("requester-id", testPagination, false, false, searchTerm);
            when(userRepository.searchUsers(searchTerm, testPagination, false))
                .thenReturn(Future.succeededFuture(testUsers.subList(0, 1)));

            // When
            handler.handle(searchQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertEquals(1, result.size());
                    assertEquals(testUsers.get(0).getId(), result.get(0).getId());
                    verify(userRepository).searchUsers(searchTerm, testPagination, false);
                    verify(userRepository, never()).findActiveUsers(any());
                    verify(userRepository, never()).findAll(any());
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should use search with includeInactive when both are specified")
        void shouldUseSearchWithIncludeInactiveWhenBothAreSpecified(VertxTestContext testContext) {
            // Given
            String searchTerm = "jane";
            GetUsersQuery searchQuery = new GetUsersQuery("requester-id", testPagination, true, false, searchTerm);
            when(userRepository.searchUsers(searchTerm, testPagination, true))
                .thenReturn(Future.succeededFuture(testUsers.subList(1, 2)));

            // When
            handler.handle(searchQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertEquals(1, result.size());
                    assertEquals(testUsers.get(1).getId(), result.get(0).getId());
                    verify(userRepository).searchUsers(searchTerm, testPagination, true);
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should handle repository failure")
        void shouldHandleRepositoryFailure(VertxTestContext testContext) {
            // Given
            RuntimeException repositoryError = new RuntimeException("Database error");
            when(userRepository.findActiveUsers(testPagination))
                .thenReturn(Future.failedFuture(repositoryError));

            // When
            handler.handle(testQuery)
                .onComplete(testContext.failing(error -> testContext.verify(() -> {
                    // Then
                    assertEquals(repositoryError, error);
                    verify(userRepository).findActiveUsers(testPagination);
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should return empty list when no users found")
        void shouldReturnEmptyListWhenNoUsersFound(VertxTestContext testContext) {
            // Given
            when(userRepository.findActiveUsers(testPagination))
                .thenReturn(Future.succeededFuture(List.of()));

            // When
            handler.handle(testQuery)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isEmpty());
                    verify(userRepository).findActiveUsers(testPagination);
                    testContext.completeNow();
                })));
        }
    }

    @Test
    @DisplayName("Should return correct query type")
    void shouldReturnCorrectQueryType() {
        assertEquals(GetUsersQuery.class, handler.getQueryType());
    }
}
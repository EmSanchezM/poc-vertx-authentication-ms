package com.auth.microservice.infrastructure.adapter.cache;

import com.auth.microservice.domain.model.Email;
import com.auth.microservice.domain.model.Permission;
import com.auth.microservice.domain.model.User;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
@DisplayName("RedisAuthCacheService Tests")
class RedisAuthCacheServiceTest {

    @Mock
    private RedisAPI redisAPI;
    
    @Mock
    private Response response;
    
    private RedisAuthCacheService cacheService;
    private User testUser;
    private Set<Permission> testPermissions;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        cacheService = new RedisAuthCacheService(redisAPI);
        
        testUserId = UUID.randomUUID();
        testUser = new User(
            testUserId,
            "testuser",
            new Email("test@example.com"),
            "hashedPassword",
            "John",
            "Doe",
            true,
            LocalDateTime.now().minusDays(1),
            LocalDateTime.now()
        );
        
        testPermissions = new HashSet<>();
        testPermissions.add(new Permission("READ_USER", "user", "read", "Read user data"));
        testPermissions.add(new Permission("WRITE_USER", "user", "write", "Write user data"));
    }

    @Nested
    @DisplayName("User Caching Tests")
    class UserCachingTests {

        @Test
        @DisplayName("Should cache user successfully")
        void shouldCacheUserSuccessfully(VertxTestContext testContext) {
            // Given
            when(redisAPI.setex(anyString(), anyString(), anyString()))
                .thenReturn(Future.succeededFuture(response));

            // When
            cacheService.cacheUserByEmail("test@example.com", Optional.of(testUser))
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    verify(redisAPI).setex(
                        eq("auth:user:email:test@example.com"),
                        eq("300"),
                        contains("\"id\":\"" + testUser.getId().toString() + "\"")
                    );
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should cache user not found")
        void shouldCacheUserNotFound(VertxTestContext testContext) {
            // Given
            when(redisAPI.setex(anyString(), anyString(), anyString()))
                .thenReturn(Future.succeededFuture(response));

            // When
            cacheService.cacheUserByEmail("notfound@example.com", Optional.empty())
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    verify(redisAPI).setex(
                        eq("auth:user:email:notfound@example.com"),
                        eq("300"),
                        eq("null")
                    );
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should retrieve cached user")
        void shouldRetrieveCachedUser(VertxTestContext testContext) {
            // Given
            JsonObject userJson = new JsonObject()
                .put("id", testUser.getId().toString())
                .put("username", testUser.getUsername())
                .put("email", testUser.getEmail().getValue())
                .put("firstName", testUser.getFirstName())
                .put("lastName", testUser.getLastName())
                .put("isActive", testUser.isActive())
                .put("createdAt", testUser.getCreatedAt().toString())
                .put("updatedAt", testUser.getUpdatedAt().toString());
            
            when(response.toString()).thenReturn(userJson.encode());
            when(redisAPI.get("auth:user:email:test@example.com"))
                .thenReturn(Future.succeededFuture(response));

            // When
            cacheService.getCachedUserByEmail("test@example.com")
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isPresent());
                    assertTrue(result.get().isPresent());
                    
                    User cachedUser = result.get().get();
                    assertEquals(testUser.getId(), cachedUser.getId());
                    assertEquals(testUser.getEmail().getValue(), cachedUser.getEmail().getValue());
                    assertEquals(testUser.getFirstName(), cachedUser.getFirstName());
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should retrieve cached user not found")
        void shouldRetrieveCachedUserNotFound(VertxTestContext testContext) {
            // Given
            when(response.toString()).thenReturn("null");
            when(redisAPI.get("auth:user:email:notfound@example.com"))
                .thenReturn(Future.succeededFuture(response));

            // When
            cacheService.getCachedUserByEmail("notfound@example.com")
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isPresent());
                    assertFalse(result.get().isPresent());
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should return empty when not cached")
        void shouldReturnEmptyWhenNotCached(VertxTestContext testContext) {
            // Given
            when(redisAPI.get("auth:user:email:test@example.com"))
                .thenReturn(Future.succeededFuture(null));

            // When
            cacheService.getCachedUserByEmail("test@example.com")
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertFalse(result.isPresent());
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should handle cache retrieval errors gracefully")
        void shouldHandleCacheRetrievalErrorsGracefully(VertxTestContext testContext) {
            // Given
            when(redisAPI.get("auth:user:email:test@example.com"))
                .thenReturn(Future.failedFuture(new RuntimeException("Redis connection error")));

            // When
            cacheService.getCachedUserByEmail("test@example.com")
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertFalse(result.isPresent());
                    
                    testContext.completeNow();
                })));
        }
    }

    @Nested
    @DisplayName("Permissions Caching Tests")
    class PermissionsCachingTests {

        @Test
        @DisplayName("Should cache user permissions successfully")
        void shouldCacheUserPermissionsSuccessfully(VertxTestContext testContext) {
            // Given
            when(redisAPI.setex(anyString(), anyString(), anyString()))
                .thenReturn(Future.succeededFuture(response));

            // When
            cacheService.cacheUserPermissions(testUserId, testPermissions)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    verify(redisAPI).setex(
                        eq("auth:user:permissions:" + testUserId.toString()),
                        eq("600"),
                        contains("READ_USER")
                    );
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should retrieve cached permissions")
        void shouldRetrieveCachedPermissions(VertxTestContext testContext) {
            // Given
            JsonArray permissionsArray = new JsonArray();
            testPermissions.forEach(permission -> {
                JsonObject permissionJson = new JsonObject()
                    .put("id", permission.getId().toString())
                    .put("name", permission.getName())
                    .put("resource", permission.getResource())
                    .put("action", permission.getAction())
                    .put("description", permission.getDescription());
                permissionsArray.add(permissionJson);
            });
            
            when(response.toString()).thenReturn(permissionsArray.encode());
            when(redisAPI.get("auth:user:permissions:" + testUserId.toString()))
                .thenReturn(Future.succeededFuture(response));

            // When
            cacheService.getCachedUserPermissions(testUserId)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isPresent());
                    assertEquals(testPermissions.size(), result.get().size());
                    
                    // Verify permission names are present
                    Set<String> cachedPermissionNames = new HashSet<>();
                    result.get().forEach(permission -> cachedPermissionNames.add(permission.getName()));
                    assertTrue(cachedPermissionNames.contains("READ_USER"));
                    assertTrue(cachedPermissionNames.contains("WRITE_USER"));
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should cache empty permissions set")
        void shouldCacheEmptyPermissionsSet(VertxTestContext testContext) {
            // Given
            Set<Permission> emptyPermissions = new HashSet<>();
            when(redisAPI.setex(anyString(), anyString(), anyString()))
                .thenReturn(Future.succeededFuture(response));

            // When
            cacheService.cacheUserPermissions(testUserId, emptyPermissions)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    verify(redisAPI).setex(
                        eq("auth:user:permissions:" + testUserId.toString()),
                        eq("600"),
                        eq("[]")
                    );
                    
                    testContext.completeNow();
                })));
        }
    }

    @Nested
    @DisplayName("Permission Check Caching Tests")
    class PermissionCheckCachingTests {

        @Test
        @DisplayName("Should cache permission check result")
        void shouldCachePermissionCheckResult(VertxTestContext testContext) {
            // Given
            String permissionKey = "name:READ_USER";
            when(redisAPI.setex(anyString(), anyString(), anyString()))
                .thenReturn(Future.succeededFuture(response));

            // When
            cacheService.cachePermissionCheck(testUserId, permissionKey, true)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    verify(redisAPI).setex(
                        eq("auth:permission:check:" + testUserId.toString() + ":" + permissionKey),
                        eq("300"),
                        eq("true")
                    );
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should retrieve cached permission check result")
        void shouldRetrieveCachedPermissionCheckResult(VertxTestContext testContext) {
            // Given
            String permissionKey = "name:READ_USER";
            when(response.toString()).thenReturn("true");
            when(redisAPI.get("auth:permission:check:" + testUserId.toString() + ":" + permissionKey))
                .thenReturn(Future.succeededFuture(response));

            // When
            cacheService.getCachedPermissionCheck(testUserId, permissionKey)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertTrue(result.isPresent());
                    assertTrue(result.get());
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should cache negative permission check result")
        void shouldCacheNegativePermissionCheckResult(VertxTestContext testContext) {
            // Given
            String permissionKey = "name:ADMIN_ACCESS";
            when(redisAPI.setex(anyString(), anyString(), anyString()))
                .thenReturn(Future.succeededFuture(response));

            // When
            cacheService.cachePermissionCheck(testUserId, permissionKey, false)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    verify(redisAPI).setex(
                        eq("auth:permission:check:" + testUserId.toString() + ":" + permissionKey),
                        eq("300"),
                        eq("false")
                    );
                    
                    testContext.completeNow();
                })));
        }
    }

    @Nested
    @DisplayName("Cache Invalidation Tests")
    class CacheInvalidationTests {

        @Test
        @DisplayName("Should invalidate user cache successfully")
        void shouldInvalidateUserCacheSuccessfully(VertxTestContext testContext) {
            // Given
            String email = "test@example.com";
            when(redisAPI.del(anyList()))
                .thenReturn(Future.succeededFuture(response));
            when(redisAPI.keys("auth:permission:check:" + testUserId.toString() + ":*"))
                .thenReturn(Future.succeededFuture(response));
            when(response.size()).thenReturn(0);

            // When
            cacheService.invalidateUserCache(testUserId, email)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    verify(redisAPI).del(argThat(keys -> 
                        keys.contains("auth:user:email:" + email.toLowerCase()) &&
                        keys.contains("auth:user:permissions:" + testUserId.toString())
                    ));
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should invalidate permission check cache entries")
        void shouldInvalidatePermissionCheckCacheEntries(VertxTestContext testContext) {
            // Given
            String email = "test@example.com";
            Response keysResponse = mock(Response.class);
            Response keyResponse1 = mock(Response.class);
            Response keyResponse2 = mock(Response.class);
            
            when(keyResponse1.toString()).thenReturn("auth:permission:check:" + testUserId + ":name:READ_USER");
            when(keyResponse2.toString()).thenReturn("auth:permission:check:" + testUserId + ":resource:user:action:read");
            
            when(keysResponse.size()).thenReturn(2);
            when(keysResponse.get(0)).thenReturn(keyResponse1);
            when(keysResponse.get(1)).thenReturn(keyResponse2);
            
            // First call for basic keys, second call for permission check keys
            when(redisAPI.del(anyList()))
                .thenReturn(Future.succeededFuture(response))
                .thenReturn(Future.succeededFuture(response));
            when(redisAPI.keys("auth:permission:check:" + testUserId.toString() + ":*"))
                .thenReturn(Future.succeededFuture(keysResponse));

            // When
            cacheService.invalidateUserCache(testUserId, email)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    verify(redisAPI, times(2)).del(anyList());
                    
                    testContext.completeNow();
                })));
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle caching errors gracefully")
        void shouldHandleCachingErrorsGracefully(VertxTestContext testContext) {
            // Given
            when(redisAPI.setex(anyString(), anyString(), anyString()))
                .thenReturn(Future.failedFuture(new RuntimeException("Redis error")));

            // When
            cacheService.cacheUserByEmail("test@example.com", Optional.of(testUser))
                .onComplete(testContext.failing(throwable -> testContext.verify(() -> {
                    // Then
                    assertEquals("Redis error", throwable.getMessage());
                    
                    testContext.completeNow();
                })));
        }

        @Test
        @DisplayName("Should handle invalid JSON in cache gracefully")
        void shouldHandleInvalidJsonInCacheGracefully(VertxTestContext testContext) {
            // Given
            when(response.toString()).thenReturn("invalid json");
            when(redisAPI.get("auth:user:email:test@example.com"))
                .thenReturn(Future.succeededFuture(response));

            // When
            cacheService.getCachedUserByEmail("test@example.com")
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertFalse(result.isPresent()); // Treated as cache miss
                    
                    testContext.completeNow();
                })));
        }
    }
}
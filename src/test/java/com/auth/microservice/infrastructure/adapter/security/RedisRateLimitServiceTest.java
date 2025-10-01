package com.auth.microservice.infrastructure.adapter.security;

import com.auth.microservice.domain.service.RateLimitService;
import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
@DisplayName("RedisRateLimitService Tests")
class RedisRateLimitServiceTest {
    
    @Mock
    private RedisAPI redisAPI;
    
    private RedisRateLimitService rateLimitService;
    
    @BeforeEach
    void setUp() {
        rateLimitService = new RedisRateLimitService(redisAPI);
    }
    
    @Nested
    @DisplayName("Rate Limit Check Tests")
    class RateLimitCheckTests {
        
        @Test
        @DisplayName("Should allow request when under limit")
        void shouldAllowRequestWhenUnderLimit(VertxTestContext testContext) {
            // Given
            String identifier = "192.168.1.1";
            String endpoint = "/api/auth/login";
            RateLimitService.RateLimitType limitType = RateLimitService.RateLimitType.BY_IP;
            
            // Mock Redis responses
            when(redisAPI.get(anyString())).thenReturn(Future.succeededFuture(null)); // No block
            when(redisAPI.zremrangebyscore(anyString(), anyString(), anyString())).thenReturn(Future.succeededFuture(mock(Response.class)));
            
            Response mockCountResponse = mock(Response.class);
            when(mockCountResponse.toInteger()).thenReturn(2); // 2 current attempts, under limit of 5
            when(redisAPI.zcard(anyString())).thenReturn(Future.succeededFuture(mockCountResponse));
            
            // When
            rateLimitService.checkRateLimit(identifier, endpoint, limitType)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertThat(result.allowed()).isTrue();
                    assertThat(result.remainingAttempts()).isEqualTo(3); // 5 - 2 = 3
                    assertThat(result.message()).isEqualTo("Request allowed");
                    assertThat(result.resetTime()).isAfter(LocalDateTime.now());
                    testContext.completeNow();
                })));
        }
        
        @Test
        @DisplayName("Should deny request when over limit")
        void shouldDenyRequestWhenOverLimit(VertxTestContext testContext) {
            // Given
            String identifier = "192.168.1.1";
            String endpoint = "/api/auth/login";
            RateLimitService.RateLimitType limitType = RateLimitService.RateLimitType.BY_IP;
            
            // Mock Redis responses
            when(redisAPI.get(anyString())).thenReturn(Future.succeededFuture(null)); // No block
            when(redisAPI.zremrangebyscore(anyString(), anyString(), anyString())).thenReturn(Future.succeededFuture(mock(Response.class)));
            
            Response mockCountResponse = mock(Response.class);
            when(mockCountResponse.toInteger()).thenReturn(5); // 5 current attempts, at limit of 5
            when(redisAPI.zcard(anyString())).thenReturn(Future.succeededFuture(mockCountResponse));
            
            // When
            rateLimitService.checkRateLimit(identifier, endpoint, limitType)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertThat(result.allowed()).isFalse();
                    assertThat(result.remainingAttempts()).isEqualTo(0);
                    assertThat(result.message()).isEqualTo("Rate limit exceeded");
                    testContext.completeNow();
                })));
        }
        
        @Test
        @DisplayName("Should deny request when blocked")
        void shouldDenyRequestWhenBlocked(VertxTestContext testContext) {
            // Given
            String identifier = "192.168.1.1";
            String endpoint = "/api/auth/login";
            RateLimitService.RateLimitType limitType = RateLimitService.RateLimitType.BY_IP;
            
            // Mock Redis responses - identifier is blocked
            long futureTimestamp = System.currentTimeMillis() / 1000 + 3600; // 1 hour in future
            Response mockBlockResponse = mock(Response.class);
            when(mockBlockResponse.toString()).thenReturn(String.valueOf(futureTimestamp));
            when(redisAPI.get(anyString())).thenReturn(Future.succeededFuture(mockBlockResponse));
            
            // When
            rateLimitService.checkRateLimit(identifier, endpoint, limitType)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertThat(result.allowed()).isFalse();
                    assertThat(result.remainingAttempts()).isEqualTo(0);
                    assertThat(result.message()).isEqualTo("Temporarily blocked due to too many failed attempts");
                    testContext.completeNow();
                })));
        }
        
        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   "})
        @DisplayName("Should fail for empty identifier")
        void shouldFailForEmptyIdentifier(String identifier, VertxTestContext testContext) {
            // Given
            String endpoint = "/api/auth/login";
            RateLimitService.RateLimitType limitType = RateLimitService.RateLimitType.BY_IP;
            
            // When
            rateLimitService.checkRateLimit(identifier, endpoint, limitType)
                .onComplete(testContext.failing(throwable -> testContext.verify(() -> {
                    // Then
                    assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
                    assertThat(throwable.getMessage()).isEqualTo("Identifier cannot be null or empty");
                    testContext.completeNow();
                })));
        }
        
        @Test
        @DisplayName("Should fail for null identifier")
        void shouldFailForNullIdentifier(VertxTestContext testContext) {
            // Given
            String endpoint = "/api/auth/login";
            RateLimitService.RateLimitType limitType = RateLimitService.RateLimitType.BY_IP;
            
            // When
            rateLimitService.checkRateLimit(null, endpoint, limitType)
                .onComplete(testContext.failing(throwable -> testContext.verify(() -> {
                    // Then
                    assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
                    assertThat(throwable.getMessage()).isEqualTo("Identifier cannot be null or empty");
                    testContext.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("Failed Attempt Recording Tests")
    class FailedAttemptRecordingTests {
        
        @Test
        @DisplayName("Should record failed attempt successfully")
        void shouldRecordFailedAttemptSuccessfully(VertxTestContext testContext) {
            // Given
            String identifier = "192.168.1.1";
            String endpoint = "/api/auth/login";
            RateLimitService.RateLimitType limitType = RateLimitService.RateLimitType.BY_IP;
            
            // Mock Redis responses
            when(redisAPI.zadd(anyList())).thenReturn(Future.succeededFuture(mock(Response.class)));
            when(redisAPI.zremrangebyscore(anyString(), anyString(), anyString())).thenReturn(Future.succeededFuture(mock(Response.class)));
            when(redisAPI.expire(anyList())).thenReturn(Future.succeededFuture(mock(Response.class)));
            
            Response mockCountResponse = mock(Response.class);
            when(mockCountResponse.toInteger()).thenReturn(3); // 3 attempts, under limit
            when(redisAPI.zcard(anyString())).thenReturn(Future.succeededFuture(mockCountResponse));
            
            // When
            rateLimitService.recordFailedAttempt(identifier, endpoint, limitType)
                .onComplete(testContext.succeeding(v -> testContext.verify(() -> {
                    // Then
                    verify(redisAPI).zadd(anyList());
                    verify(redisAPI).zremrangebyscore(anyString(), anyString(), anyString());
                    verify(redisAPI).expire(anyList());
                    verify(redisAPI).zcard(anyString());
                    testContext.completeNow();
                })));
        }
        
        @Test
        @DisplayName("Should block identifier after max attempts")
        void shouldBlockIdentifierAfterMaxAttempts(VertxTestContext testContext) {
            // Given
            String identifier = "192.168.1.1";
            String endpoint = "/api/auth/login";
            RateLimitService.RateLimitType limitType = RateLimitService.RateLimitType.BY_IP;
            
            // Mock Redis responses
            when(redisAPI.zadd(anyList())).thenReturn(Future.succeededFuture(mock(Response.class)));
            when(redisAPI.zremrangebyscore(anyString(), anyString(), anyString())).thenReturn(Future.succeededFuture(mock(Response.class)));
            when(redisAPI.expire(anyList())).thenReturn(Future.succeededFuture(mock(Response.class)));
            
            Response mockCountResponse = mock(Response.class);
            when(mockCountResponse.toInteger()).thenReturn(5); // 5 attempts, at limit
            when(redisAPI.zcard(anyString())).thenReturn(Future.succeededFuture(mockCountResponse));
            when(redisAPI.setex(anyString(), anyString(), anyString())).thenReturn(Future.succeededFuture(mock(Response.class)));
            
            // When
            rateLimitService.recordFailedAttempt(identifier, endpoint, limitType)
                .onComplete(testContext.succeeding(v -> testContext.verify(() -> {
                    // Then
                    verify(redisAPI).setex(anyString(), anyString(), anyString()); // Block should be set
                    testContext.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("Successful Attempt Recording Tests")
    class SuccessfulAttemptRecordingTests {
        
        @Test
        @DisplayName("Should reset attempts on successful login")
        void shouldResetAttemptsOnSuccessfulLogin(VertxTestContext testContext) {
            // Given
            String identifier = "192.168.1.1";
            String endpoint = "/api/auth/login";
            RateLimitService.RateLimitType limitType = RateLimitService.RateLimitType.BY_IP;
            
            // Mock Redis responses
            when(redisAPI.del(anyList())).thenReturn(Future.succeededFuture(mock(Response.class)));
            
            // When
            rateLimitService.recordSuccessfulAttempt(identifier, endpoint, limitType)
                .onComplete(testContext.succeeding(v -> testContext.verify(() -> {
                    // Then
                    verify(redisAPI).del(anyList()); // Should delete both attempt counter and block
                    testContext.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("Temporary Block Tests")
    class TemporaryBlockTests {
        
        @Test
        @DisplayName("Should create temporary block successfully")
        void shouldCreateTemporaryBlockSuccessfully(VertxTestContext testContext) {
            // Given
            String identifier = "192.168.1.1";
            String endpoint = "/api/auth/login";
            RateLimitService.RateLimitType limitType = RateLimitService.RateLimitType.BY_IP;
            int blockDurationMinutes = 30;
            
            // Mock Redis responses
            when(redisAPI.setex(anyString(), anyString(), anyString())).thenReturn(Future.succeededFuture(mock(Response.class)));
            
            // When
            rateLimitService.temporaryBlock(identifier, endpoint, limitType, blockDurationMinutes)
                .onComplete(testContext.succeeding(v -> testContext.verify(() -> {
                    // Then
                    verify(redisAPI).setex(anyString(), eq(String.valueOf(blockDurationMinutes * 60)), anyString());
                    testContext.completeNow();
                })));
        }
        
        @Test
        @DisplayName("Should fail for invalid block duration")
        void shouldFailForInvalidBlockDuration(VertxTestContext testContext) {
            // Given
            String identifier = "192.168.1.1";
            String endpoint = "/api/auth/login";
            RateLimitService.RateLimitType limitType = RateLimitService.RateLimitType.BY_IP;
            int invalidDuration = -5;
            
            // When
            rateLimitService.temporaryBlock(identifier, endpoint, limitType, invalidDuration)
                .onComplete(testContext.failing(throwable -> testContext.verify(() -> {
                    // Then
                    assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
                    assertThat(throwable.getMessage()).isEqualTo("Block duration must be positive");
                    testContext.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("Remove Block Tests")
    class RemoveBlockTests {
        
        @Test
        @DisplayName("Should remove block successfully")
        void shouldRemoveBlockSuccessfully(VertxTestContext testContext) {
            // Given
            String identifier = "192.168.1.1";
            String endpoint = "/api/auth/login";
            RateLimitService.RateLimitType limitType = RateLimitService.RateLimitType.BY_IP;
            
            // Mock Redis responses
            when(redisAPI.del(anyList())).thenReturn(Future.succeededFuture(mock(Response.class)));
            
            // When
            rateLimitService.removeBlock(identifier, endpoint, limitType)
                .onComplete(testContext.succeeding(v -> testContext.verify(() -> {
                    // Then
                    verify(redisAPI).del(anyList());
                    testContext.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("Rate Limit Status Tests")
    class RateLimitStatusTests {
        
        @Test
        @DisplayName("Should get rate limit status successfully")
        void shouldGetRateLimitStatusSuccessfully(VertxTestContext testContext) {
            // Given
            String identifier = "192.168.1.1";
            String endpoint = "/api/auth/login";
            RateLimitService.RateLimitType limitType = RateLimitService.RateLimitType.BY_IP;
            
            // Mock Redis responses
            Response mockCountResponse = mock(Response.class);
            when(mockCountResponse.toInteger()).thenReturn(3);
            when(redisAPI.zcard(anyString())).thenReturn(Future.succeededFuture(mockCountResponse));
            when(redisAPI.get(anyString())).thenReturn(Future.succeededFuture(null)); // Not blocked
            
            // When
            rateLimitService.getRateLimitStatus(identifier, endpoint, limitType)
                .onComplete(testContext.succeeding(status -> testContext.verify(() -> {
                    // Then
                    assertThat(status.identifier()).isEqualTo(identifier);
                    assertThat(status.endpoint()).isEqualTo(endpoint);
                    assertThat(status.limitType()).isEqualTo(limitType);
                    assertThat(status.currentAttempts()).isEqualTo(3);
                    assertThat(status.maxAttempts()).isEqualTo(5); // Default IP config
                    assertThat(status.isBlocked()).isFalse();
                    assertThat(status.blockedUntil()).isNull();
                    testContext.completeNow();
                })));
        }
        
        @Test
        @DisplayName("Should show blocked status when blocked")
        void shouldShowBlockedStatusWhenBlocked(VertxTestContext testContext) {
            // Given
            String identifier = "192.168.1.1";
            String endpoint = "/api/auth/login";
            RateLimitService.RateLimitType limitType = RateLimitService.RateLimitType.BY_IP;
            
            // Mock Redis responses
            Response mockCountResponse = mock(Response.class);
            when(mockCountResponse.toInteger()).thenReturn(5);
            when(redisAPI.zcard(anyString())).thenReturn(Future.succeededFuture(mockCountResponse));
            
            long futureTimestamp = System.currentTimeMillis() / 1000 + 3600; // 1 hour in future
            Response mockBlockResponse = mock(Response.class);
            when(mockBlockResponse.toString()).thenReturn(String.valueOf(futureTimestamp));
            when(redisAPI.get(anyString())).thenReturn(Future.succeededFuture(mockBlockResponse));
            
            // When
            rateLimitService.getRateLimitStatus(identifier, endpoint, limitType)
                .onComplete(testContext.succeeding(status -> testContext.verify(() -> {
                    // Then
                    assertThat(status.currentAttempts()).isEqualTo(5);
                    assertThat(status.isBlocked()).isTrue();
                    assertThat(status.blockedUntil()).isNotNull();
                    testContext.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("Custom Configuration Tests")
    class CustomConfigurationTests {
        
        @Test
        @DisplayName("Should use custom rate limit configuration")
        void shouldUseCustomRateLimitConfiguration(VertxTestContext testContext) {
            // Given
            Map<RateLimitService.RateLimitType, RedisRateLimitService.RateLimitConfig> customConfigs = new HashMap<>();
            customConfigs.put(RateLimitService.RateLimitType.BY_IP, 
                new RedisRateLimitService.RateLimitConfig(10, 5, 15)); // 10 attempts, 5 min window, 15 min block
            
            RedisRateLimitService customService = new RedisRateLimitService(redisAPI, customConfigs);
            
            String identifier = "192.168.1.1";
            String endpoint = "/api/auth/login";
            RateLimitService.RateLimitType limitType = RateLimitService.RateLimitType.BY_IP;
            
            // Mock Redis responses
            Response mockCountResponse = mock(Response.class);
            when(mockCountResponse.toInteger()).thenReturn(8);
            when(redisAPI.zcard(anyString())).thenReturn(Future.succeededFuture(mockCountResponse));
            when(redisAPI.get(anyString())).thenReturn(Future.succeededFuture(null));
            
            // When
            customService.getRateLimitStatus(identifier, endpoint, limitType)
                .onComplete(testContext.succeeding(status -> testContext.verify(() -> {
                    // Then
                    assertThat(status.maxAttempts()).isEqualTo(10); // Custom config
                    assertThat(status.currentAttempts()).isEqualTo(8);
                    testContext.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("Different Rate Limit Types Tests")
    class DifferentRateLimitTypesTests {
        
        @Test
        @DisplayName("Should handle BY_USER rate limit type")
        void shouldHandleByUserRateLimitType(VertxTestContext testContext) {
            // Given
            String identifier = "user123";
            String endpoint = "/api/auth/login";
            RateLimitService.RateLimitType limitType = RateLimitService.RateLimitType.BY_USER;
            
            // Mock Redis responses
            when(redisAPI.get(anyString())).thenReturn(Future.succeededFuture(null));
            when(redisAPI.zremrangebyscore(anyString(), anyString(), anyString())).thenReturn(Future.succeededFuture(mock(Response.class)));
            
            Response mockCountResponse = mock(Response.class);
            when(mockCountResponse.toInteger()).thenReturn(2);
            when(redisAPI.zcard(anyString())).thenReturn(Future.succeededFuture(mockCountResponse));
            
            // When
            rateLimitService.checkRateLimit(identifier, endpoint, limitType)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertThat(result.allowed()).isTrue();
                    assertThat(result.remainingAttempts()).isEqualTo(1); // BY_USER default: 3 max attempts
                    testContext.completeNow();
                })));
        }
        
        @Test
        @DisplayName("Should handle BY_GLOBAL rate limit type")
        void shouldHandleByGlobalRateLimitType(VertxTestContext testContext) {
            // Given
            String identifier = "global";
            String endpoint = "/api/auth/login";
            RateLimitService.RateLimitType limitType = RateLimitService.RateLimitType.BY_GLOBAL;
            
            // Mock Redis responses
            when(redisAPI.get(anyString())).thenReturn(Future.succeededFuture(null));
            when(redisAPI.zremrangebyscore(anyString(), anyString(), anyString())).thenReturn(Future.succeededFuture(mock(Response.class)));
            
            Response mockCountResponse = mock(Response.class);
            when(mockCountResponse.toInteger()).thenReturn(50);
            when(redisAPI.zcard(anyString())).thenReturn(Future.succeededFuture(mockCountResponse));
            
            // When
            rateLimitService.checkRateLimit(identifier, endpoint, limitType)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    // Then
                    assertThat(result.allowed()).isTrue();
                    assertThat(result.remainingAttempts()).isEqualTo(50); // BY_GLOBAL default: 100 max attempts
                    testContext.completeNow();
                })));
        }
    }
}
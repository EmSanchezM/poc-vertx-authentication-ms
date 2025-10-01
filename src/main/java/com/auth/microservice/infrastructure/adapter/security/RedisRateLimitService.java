package com.auth.microservice.infrastructure.adapter.security;

import com.auth.microservice.domain.service.RateLimitService;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.Response;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis-based implementation of RateLimitService.
 * Uses Redis for distributed rate limiting with sliding window approach.
 */
public class RedisRateLimitService implements RateLimitService {
    
    private final RedisAPI redisAPI;
    private final Map<RateLimitType, RateLimitConfig> rateLimitConfigs;
    
    // Default rate limit configurations
    private static final RateLimitConfig DEFAULT_IP_CONFIG = new RateLimitConfig(5, 15, 30); // 5 attempts, 15 min window, 30 min block
    private static final RateLimitConfig DEFAULT_USER_CONFIG = new RateLimitConfig(3, 10, 60); // 3 attempts, 10 min window, 60 min block
    private static final RateLimitConfig DEFAULT_GLOBAL_CONFIG = new RateLimitConfig(100, 1, 5); // 100 attempts, 1 min window, 5 min block
    
    public RedisRateLimitService(RedisAPI redisAPI) {
        this.redisAPI = redisAPI;
        this.rateLimitConfigs = new HashMap<>();
        this.rateLimitConfigs.put(RateLimitType.BY_IP, DEFAULT_IP_CONFIG);
        this.rateLimitConfigs.put(RateLimitType.BY_USER, DEFAULT_USER_CONFIG);
        this.rateLimitConfigs.put(RateLimitType.BY_GLOBAL, DEFAULT_GLOBAL_CONFIG);
    }
    
    public RedisRateLimitService(RedisAPI redisAPI, Map<RateLimitType, RateLimitConfig> customConfigs) {
        this.redisAPI = redisAPI;
        this.rateLimitConfigs = new HashMap<>(customConfigs);
    }
    
    @Override
    public Future<RateLimitResult> checkRateLimit(String identifier, String endpoint, RateLimitType limitType) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return Future.failedFuture(new IllegalArgumentException("Identifier cannot be null or empty"));
        }
        if (endpoint == null || endpoint.trim().isEmpty()) {
            return Future.failedFuture(new IllegalArgumentException("Endpoint cannot be null or empty"));
        }
        
        RateLimitConfig config = rateLimitConfigs.get(limitType);
        String key = buildKey(identifier, endpoint, limitType);
        String blockKey = buildBlockKey(identifier, endpoint, limitType);
        
        Promise<RateLimitResult> promise = Promise.promise();
        
        // First check if the identifier is currently blocked
        redisAPI.get(blockKey)
            .onSuccess(blockResponse -> {
                if (blockResponse != null) {
                    // Currently blocked
                    long blockedUntilEpoch = Long.parseLong(blockResponse.toString());
                    LocalDateTime blockedUntil = LocalDateTime.ofEpochSecond(blockedUntilEpoch, 0, ZoneOffset.UTC);
                    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                    
                    if (now.isBefore(blockedUntil)) {
                        promise.complete(new RateLimitResult(false, 0, blockedUntil, "Temporarily blocked due to too many failed attempts"));
                        return;
                    } else {
                        // Block has expired, remove it
                        redisAPI.del(Arrays.asList(blockKey));
                    }
                }
                
                // Check current attempts using sliding window
                checkSlidingWindow(key, config, promise);
            })
            .onFailure(promise::fail);
        
        return promise.future();
    }
    
    @Override
    public Future<Void> recordFailedAttempt(String identifier, String endpoint, RateLimitType limitType) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return Future.failedFuture(new IllegalArgumentException("Identifier cannot be null or empty"));
        }
        
        RateLimitConfig config = rateLimitConfigs.get(limitType);
        String key = buildKey(identifier, endpoint, limitType);
        String blockKey = buildBlockKey(identifier, endpoint, limitType);
        
        Promise<Void> promise = Promise.promise();
        
        // Add failed attempt with current timestamp
        long currentTime = System.currentTimeMillis();
        
        redisAPI.zadd(Arrays.asList(key, String.valueOf(currentTime), String.valueOf(currentTime)))
            .compose(v -> {
                // Remove old entries outside the window
                long windowStart = currentTime - (config.windowMinutes * 60 * 1000);
                return redisAPI.zremrangebyscore(key, "0", String.valueOf(windowStart));
            })
            .compose(v -> {
                // Set expiration for the key
                return redisAPI.expire(Arrays.asList(key, String.valueOf(config.windowMinutes * 60 + 60))); // Add 1 minute buffer
            })
            .compose(v -> {
                // Check if we should block after this attempt
                return redisAPI.zcard(key);
            })
            .onSuccess(countResponse -> {
                int currentAttempts = countResponse.toInteger();
                
                if (currentAttempts >= config.maxAttempts) {
                    // Block the identifier
                    long blockUntil = System.currentTimeMillis() / 1000 + (config.blockMinutes * 60);
                    redisAPI.setex(blockKey, String.valueOf(config.blockMinutes * 60), String.valueOf(blockUntil))
                        .onSuccess(v -> promise.complete())
                        .onFailure(promise::fail);
                } else {
                    promise.complete();
                }
            })
            .onFailure(promise::fail);
        
        return promise.future();
    }
    
    @Override
    public Future<Void> recordSuccessfulAttempt(String identifier, String endpoint, RateLimitType limitType) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return Future.failedFuture(new IllegalArgumentException("Identifier cannot be null or empty"));
        }
        
        String key = buildKey(identifier, endpoint, limitType);
        String blockKey = buildBlockKey(identifier, endpoint, limitType);
        
        // Remove any existing blocks and reset attempt counter
        return redisAPI.del(Arrays.asList(key, blockKey))
            .mapEmpty();
    }
    
    @Override
    public Future<Void> temporaryBlock(String identifier, String endpoint, RateLimitType limitType, int blockDurationMinutes) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return Future.failedFuture(new IllegalArgumentException("Identifier cannot be null or empty"));
        }
        if (blockDurationMinutes <= 0) {
            return Future.failedFuture(new IllegalArgumentException("Block duration must be positive"));
        }
        
        String blockKey = buildBlockKey(identifier, endpoint, limitType);
        long blockUntil = System.currentTimeMillis() / 1000 + (blockDurationMinutes * 60);
        
        return redisAPI.setex(blockKey, String.valueOf(blockDurationMinutes * 60), String.valueOf(blockUntil))
            .mapEmpty();
    }
    
    @Override
    public Future<Void> removeBlock(String identifier, String endpoint, RateLimitType limitType) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return Future.failedFuture(new IllegalArgumentException("Identifier cannot be null or empty"));
        }
        
        String key = buildKey(identifier, endpoint, limitType);
        String blockKey = buildBlockKey(identifier, endpoint, limitType);
        
        return redisAPI.del(Arrays.asList(key, blockKey))
            .mapEmpty();
    }
    
    @Override
    public Future<RateLimitStatus> getRateLimitStatus(String identifier, String endpoint, RateLimitType limitType) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return Future.failedFuture(new IllegalArgumentException("Identifier cannot be null or empty"));
        }
        
        RateLimitConfig config = rateLimitConfigs.get(limitType);
        String key = buildKey(identifier, endpoint, limitType);
        String blockKey = buildBlockKey(identifier, endpoint, limitType);
        
        Promise<RateLimitStatus> promise = Promise.promise();
        
        // Get current attempts count
        redisAPI.zcard(key)
            .compose(countResponse -> {
                int currentAttempts = countResponse.toInteger();
                
                // Check if blocked
                return redisAPI.get(blockKey)
                    .map(blockResponse -> {
                        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                        LocalDateTime windowStart = now.minusMinutes(config.windowMinutes);
                        LocalDateTime windowEnd = now;
                        
                        boolean isBlocked = false;
                        LocalDateTime blockedUntil = null;
                        
                        if (blockResponse != null) {
                            long blockedUntilEpoch = Long.parseLong(blockResponse.toString());
                            blockedUntil = LocalDateTime.ofEpochSecond(blockedUntilEpoch, 0, ZoneOffset.UTC);
                            isBlocked = now.isBefore(blockedUntil);
                        }
                        
                        return new RateLimitStatus(
                            identifier,
                            endpoint,
                            limitType,
                            currentAttempts,
                            config.maxAttempts,
                            windowStart,
                            windowEnd,
                            isBlocked,
                            blockedUntil
                        );
                    });
            })
            .onSuccess(promise::complete)
            .onFailure(promise::fail);
        
        return promise.future();
    }
    
    /**
     * Checks the sliding window for rate limiting.
     */
    private void checkSlidingWindow(String key, RateLimitConfig config, Promise<RateLimitResult> promise) {
        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - (config.windowMinutes * 60 * 1000);
        
        // Remove old entries and count current attempts
        redisAPI.zremrangebyscore(key, "0", String.valueOf(windowStart))
            .compose(v -> redisAPI.zcard(key))
            .onSuccess(countResponse -> {
                int currentAttempts = countResponse.toInteger();
                int remainingAttempts = Math.max(0, config.maxAttempts - currentAttempts);
                LocalDateTime resetTime = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(config.windowMinutes);
                
                boolean allowed = currentAttempts < config.maxAttempts;
                String message = allowed ? "Request allowed" : "Rate limit exceeded";
                
                promise.complete(new RateLimitResult(allowed, remainingAttempts, resetTime, message));
            })
            .onFailure(promise::fail);
    }
    
    /**
     * Builds a Redis key for rate limiting.
     */
    private String buildKey(String identifier, String endpoint, RateLimitType limitType) {
        return String.format("rate_limit:%s:%s:%s", limitType.name().toLowerCase(), endpoint, identifier);
    }
    
    /**
     * Builds a Redis key for blocking.
     */
    private String buildBlockKey(String identifier, String endpoint, RateLimitType limitType) {
        return String.format("rate_limit_block:%s:%s:%s", limitType.name().toLowerCase(), endpoint, identifier);
    }
    
    /**
     * Configuration for rate limiting.
     */
    public static class RateLimitConfig {
        public final int maxAttempts;
        public final int windowMinutes;
        public final int blockMinutes;
        
        public RateLimitConfig(int maxAttempts, int windowMinutes, int blockMinutes) {
            this.maxAttempts = maxAttempts;
            this.windowMinutes = windowMinutes;
            this.blockMinutes = blockMinutes;
        }
    }
}
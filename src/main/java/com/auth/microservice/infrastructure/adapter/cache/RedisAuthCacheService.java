package com.auth.microservice.infrastructure.adapter.cache;

import com.auth.microservice.domain.model.Permission;
import com.auth.microservice.domain.model.User;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.Response;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Redis-based cache service for authentication queries.
 * Provides caching for frequently accessed user and permission data.
 */
public class RedisAuthCacheService {
    
    private final RedisAPI redisAPI;
    
    // Cache key prefixes
    private static final String USER_BY_EMAIL_PREFIX = "auth:user:email:";
    private static final String USER_PERMISSIONS_PREFIX = "auth:user:permissions:";
    private static final String PERMISSION_CHECK_PREFIX = "auth:permission:check:";
    
    // Cache TTL configurations (in seconds)
    private static final int USER_CACHE_TTL = 300; // 5 minutes
    private static final int PERMISSIONS_CACHE_TTL = 600; // 10 minutes
    private static final int PERMISSION_CHECK_TTL = 300; // 5 minutes
    
    public RedisAuthCacheService(RedisAPI redisAPI) {
        this.redisAPI = redisAPI;
    }
    
    /**
     * Cache user by email
     */
    public Future<Void> cacheUserByEmail(String email, Optional<User> user) {
        Promise<Void> promise = Promise.promise();
        
        String key = USER_BY_EMAIL_PREFIX + email.toLowerCase();
        String value = user.map(this::userToJson).orElse("null");
        
        redisAPI.setex(key, String.valueOf(USER_CACHE_TTL), value)
            .onSuccess(response -> promise.complete())
            .onFailure(promise::fail);
            
        return promise.future();
    }
    
    /**
     * Get cached user by email
     */
    public Future<Optional<Optional<User>>> getCachedUserByEmail(String email) {
        Promise<Optional<Optional<User>>> promise = Promise.promise();
        
        String key = USER_BY_EMAIL_PREFIX + email.toLowerCase();
        
        redisAPI.get(key)
            .onSuccess(response -> {
                if (response == null) {
                    promise.complete(Optional.empty()); // Not cached
                } else {
                    String value = response.toString();
                    if ("null".equals(value)) {
                        promise.complete(Optional.of(Optional.empty())); // Cached as not found
                    } else {
                        try {
                            User user = jsonToUser(new JsonObject(value));
                            promise.complete(Optional.of(Optional.of(user)));
                        } catch (Exception e) {
                            promise.complete(Optional.empty()); // Invalid cache, treat as not cached
                        }
                    }
                }
            })
            .onFailure(throwable -> promise.complete(Optional.empty())); // On error, treat as not cached
            
        return promise.future();
    }
    
    /**
     * Cache user permissions
     */
    public Future<Void> cacheUserPermissions(UUID userId, Set<Permission> permissions) {
        Promise<Void> promise = Promise.promise();
        
        String key = USER_PERMISSIONS_PREFIX + userId.toString();
        JsonArray permissionsArray = new JsonArray();
        permissions.forEach(permission -> permissionsArray.add(permissionToJson(permission)));
        
        redisAPI.setex(key, String.valueOf(PERMISSIONS_CACHE_TTL), permissionsArray.encode())
            .onSuccess(response -> promise.complete())
            .onFailure(promise::fail);
            
        return promise.future();
    }
    
    /**
     * Get cached user permissions
     */
    public Future<Optional<Set<Permission>>> getCachedUserPermissions(UUID userId) {
        Promise<Optional<Set<Permission>>> promise = Promise.promise();
        
        String key = USER_PERMISSIONS_PREFIX + userId.toString();
        
        redisAPI.get(key)
            .onSuccess(response -> {
                if (response == null) {
                    promise.complete(Optional.empty());
                } else {
                    try {
                        JsonArray permissionsArray = new JsonArray(response.toString());
                        Set<Permission> permissions = new HashSet<>();
                        permissionsArray.forEach(item -> {
                            JsonObject permissionJson = (JsonObject) item;
                            permissions.add(jsonToPermission(permissionJson));
                        });
                        promise.complete(Optional.of(permissions));
                    } catch (Exception e) {
                        promise.complete(Optional.empty()); // Invalid cache
                    }
                }
            })
            .onFailure(throwable -> promise.complete(Optional.empty()));
            
        return promise.future();
    }
    
    /**
     * Cache permission check result
     */
    public Future<Void> cachePermissionCheck(UUID userId, String permissionKey, boolean hasPermission) {
        Promise<Void> promise = Promise.promise();
        
        String key = PERMISSION_CHECK_PREFIX + userId.toString() + ":" + permissionKey;
        String value = String.valueOf(hasPermission);
        
        redisAPI.setex(key, String.valueOf(PERMISSION_CHECK_TTL), value)
            .onSuccess(response -> promise.complete())
            .onFailure(promise::fail);
            
        return promise.future();
    }
    
    /**
     * Get cached permission check result
     */
    public Future<Optional<Boolean>> getCachedPermissionCheck(UUID userId, String permissionKey) {
        Promise<Optional<Boolean>> promise = Promise.promise();
        
        String key = PERMISSION_CHECK_PREFIX + userId.toString() + ":" + permissionKey;
        
        redisAPI.get(key)
            .onSuccess(response -> {
                if (response == null) {
                    promise.complete(Optional.empty());
                } else {
                    try {
                        boolean hasPermission = Boolean.parseBoolean(response.toString());
                        promise.complete(Optional.of(hasPermission));
                    } catch (Exception e) {
                        promise.complete(Optional.empty());
                    }
                }
            })
            .onFailure(throwable -> promise.complete(Optional.empty()));
            
        return promise.future();
    }
    
    /**
     * Invalidate user-related caches
     */
    public Future<Void> invalidateUserCache(UUID userId, String email) {
        Promise<Void> promise = Promise.promise();
        
        String emailKey = USER_BY_EMAIL_PREFIX + email.toLowerCase();
        String permissionsKey = USER_PERMISSIONS_PREFIX + userId.toString();
        String permissionCheckPattern = PERMISSION_CHECK_PREFIX + userId.toString() + ":*";
        
        // Delete specific keys and pattern-based keys
        redisAPI.del(java.util.Arrays.asList(emailKey, permissionsKey))
            .compose(response -> {
                // Delete permission check cache entries for this user
                return redisAPI.keys(permissionCheckPattern)
                    .compose(keysResponse -> {
                        if (keysResponse != null && keysResponse.size() > 0) {
                            java.util.List<String> keys = new java.util.ArrayList<>();
                            keysResponse.forEach(key -> keys.add(key.toString()));
                            return redisAPI.del(keys);
                        } else {
                            return Future.succeededFuture();
                        }
                    });
            })
            .onSuccess(response -> promise.complete())
            .onFailure(promise::fail);
            
        return promise.future();
    }
    
    // Helper methods for JSON serialization
    private String userToJson(User user) {
        JsonObject json = new JsonObject()
            .put("id", user.getId().toString())
            .put("username", user.getUsername())
            .put("email", user.getEmail().getValue())
            .put("firstName", user.getFirstName())
            .put("lastName", user.getLastName())
            .put("isActive", user.isActive())
            .put("createdAt", user.getCreatedAt().toString())
            .put("updatedAt", user.getUpdatedAt().toString());
            
        return json.encode();
    }
    
    private User jsonToUser(JsonObject json) {
        // Note: This creates a user without roles for cache purposes
        // Roles should be loaded separately if needed
        return new User(
            UUID.fromString(json.getString("id")),
            json.getString("username"),
            new com.auth.microservice.domain.model.Email(json.getString("email")),
            "cached_placeholder", // Password hash not cached for security - placeholder value
            json.getString("firstName"),
            json.getString("lastName"),
            json.getBoolean("isActive"),
            java.time.LocalDateTime.parse(json.getString("createdAt")),
            java.time.LocalDateTime.parse(json.getString("updatedAt"))
        );
    }
    
    private JsonObject permissionToJson(Permission permission) {
        return new JsonObject()
            .put("id", permission.getId().toString())
            .put("name", permission.getName())
            .put("resource", permission.getResource())
            .put("action", permission.getAction())
            .put("description", permission.getDescription());
    }
    
    private Permission jsonToPermission(JsonObject json) {
        return new Permission(
            UUID.fromString(json.getString("id")),
            json.getString("name"),
            json.getString("resource"),
            json.getString("action"),
            json.getString("description")
        );
    }
}
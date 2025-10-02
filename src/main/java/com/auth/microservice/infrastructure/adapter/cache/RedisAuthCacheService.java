package com.auth.microservice.infrastructure.adapter.cache;

import com.auth.microservice.domain.model.Permission;
import com.auth.microservice.domain.model.Role;
import com.auth.microservice.domain.model.User;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.Response;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private static final String ROLE_BY_ID_PREFIX = "auth:role:id:";
    private static final String USER_ROLES_PREFIX = "auth:user:roles:";
    private static final String ROLES_LIST_PREFIX = "auth:roles:list:";
    
    // Cache TTL configurations (in seconds)
    private static final int USER_CACHE_TTL = 300; // 5 minutes
    private static final int PERMISSIONS_CACHE_TTL = 600; // 10 minutes
    private static final int PERMISSION_CHECK_TTL = 300; // 5 minutes
    private static final int ROLE_CACHE_TTL = 900; // 15 minutes (roles change less frequently)
    private static final int USER_ROLES_CACHE_TTL = 600; // 10 minutes
    private static final int ROLES_LIST_CACHE_TTL = 1800; // 30 minutes (role list changes infrequently)
    
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
     * Cache role by ID
     */
    public Future<Void> cacheRoleById(UUID roleId, Optional<Role> role) {
        Promise<Void> promise = Promise.promise();
        
        String key = ROLE_BY_ID_PREFIX + roleId.toString();
        String value = role.map(this::roleToJson).orElse("null");
        
        redisAPI.setex(key, String.valueOf(ROLE_CACHE_TTL), value)
            .onSuccess(response -> promise.complete())
            .onFailure(promise::fail);
            
        return promise.future();
    }
    
    /**
     * Get cached role by ID
     */
    public Future<Optional<Optional<Role>>> getCachedRoleById(UUID roleId) {
        Promise<Optional<Optional<Role>>> promise = Promise.promise();
        
        String key = ROLE_BY_ID_PREFIX + roleId.toString();
        
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
                            Role role = jsonToRole(new JsonObject(value));
                            promise.complete(Optional.of(Optional.of(role)));
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
     * Cache user roles
     */
    public Future<Void> cacheUserRoles(UUID userId, List<Role> roles) {
        Promise<Void> promise = Promise.promise();
        
        String key = USER_ROLES_PREFIX + userId.toString();
        JsonArray rolesArray = new JsonArray();
        roles.forEach(role -> rolesArray.add(roleToJson(role)));
        
        redisAPI.setex(key, String.valueOf(USER_ROLES_CACHE_TTL), rolesArray.encode())
            .onSuccess(response -> promise.complete())
            .onFailure(promise::fail);
            
        return promise.future();
    }
    
    /**
     * Get cached user roles
     */
    public Future<Optional<List<Role>>> getCachedUserRoles(UUID userId) {
        Promise<Optional<List<Role>>> promise = Promise.promise();
        
        String key = USER_ROLES_PREFIX + userId.toString();
        
        redisAPI.get(key)
            .onSuccess(response -> {
                if (response == null) {
                    promise.complete(Optional.empty());
                } else {
                    try {
                        JsonArray rolesArray = new JsonArray(response.toString());
                        List<Role> roles = rolesArray.stream()
                            .map(item -> jsonToRole((JsonObject) item))
                            .collect(Collectors.toList());
                        promise.complete(Optional.of(roles));
                    } catch (Exception e) {
                        promise.complete(Optional.empty()); // Invalid cache
                    }
                }
            })
            .onFailure(throwable -> promise.complete(Optional.empty()));
            
        return promise.future();
    }
    
    /**
     * Cache roles list with pagination key
     */
    public Future<Void> cacheRolesList(String paginationKey, List<Role> roles) {
        Promise<Void> promise = Promise.promise();
        
        String key = ROLES_LIST_PREFIX + paginationKey;
        JsonArray rolesArray = new JsonArray();
        roles.forEach(role -> rolesArray.add(roleToJson(role)));
        
        redisAPI.setex(key, String.valueOf(ROLES_LIST_CACHE_TTL), rolesArray.encode())
            .onSuccess(response -> promise.complete())
            .onFailure(promise::fail);
            
        return promise.future();
    }
    
    /**
     * Get cached roles list
     */
    public Future<Optional<List<Role>>> getCachedRolesList(String paginationKey) {
        Promise<Optional<List<Role>>> promise = Promise.promise();
        
        String key = ROLES_LIST_PREFIX + paginationKey;
        
        redisAPI.get(key)
            .onSuccess(response -> {
                if (response == null) {
                    promise.complete(Optional.empty());
                } else {
                    try {
                        JsonArray rolesArray = new JsonArray(response.toString());
                        List<Role> roles = rolesArray.stream()
                            .map(item -> jsonToRole((JsonObject) item))
                            .collect(Collectors.toList());
                        promise.complete(Optional.of(roles));
                    } catch (Exception e) {
                        promise.complete(Optional.empty()); // Invalid cache
                    }
                }
            })
            .onFailure(throwable -> promise.complete(Optional.empty()));
            
        return promise.future();
    }
    
    /**
     * Invalidate role-related caches
     */
    public Future<Void> invalidateRoleCache(UUID roleId) {
        Promise<Void> promise = Promise.promise();
        
        String roleKey = ROLE_BY_ID_PREFIX + roleId.toString();
        String rolesListPattern = ROLES_LIST_PREFIX + "*";
        String userRolesPattern = USER_ROLES_PREFIX + "*";
        
        // Delete role by ID cache
        redisAPI.del(java.util.Arrays.asList(roleKey))
            .compose(response -> {
                // Delete roles list cache entries
                return redisAPI.keys(rolesListPattern)
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
            .compose(response -> {
                // Delete user roles cache entries (since role might have changed)
                return redisAPI.keys(userRolesPattern)
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
    
    private String roleToJson(Role role) {
        JsonObject json = new JsonObject()
            .put("id", role.getId().toString())
            .put("name", role.getName())
            .put("description", role.getDescription())
            .put("createdAt", role.getCreatedAt().toString());
            
        // Include permissions if they are loaded
        if (!role.getPermissions().isEmpty()) {
            JsonArray permissionsArray = new JsonArray();
            role.getPermissions().forEach(permission -> permissionsArray.add(permissionToJson(permission)));
            json.put("permissions", permissionsArray);
        }
            
        return json.encode();
    }
    
    private Role jsonToRole(JsonObject json) {
        Role role = new Role(
            UUID.fromString(json.getString("id")),
            json.getString("name"),
            json.getString("description"),
            java.time.LocalDateTime.parse(json.getString("createdAt"))
        );
        
        // Load permissions if they exist in the cached data
        JsonArray permissionsArray = json.getJsonArray("permissions");
        if (permissionsArray != null) {
            Set<Permission> permissions = new HashSet<>();
            permissionsArray.forEach(item -> {
                JsonObject permissionJson = (JsonObject) item;
                permissions.add(jsonToPermission(permissionJson));
            });
            role.addPermissions(permissions);
        }
        
        return role;
    }
}
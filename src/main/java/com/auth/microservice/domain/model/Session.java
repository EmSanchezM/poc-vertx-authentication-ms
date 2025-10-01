package com.auth.microservice.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Session domain entity representing a user session with JWT tokens
 */
public class Session {
    private UUID id;
    private UUID userId;
    private String accessTokenHash;
    private String refreshTokenHash;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
    private String ipAddress;
    private String userAgent;
    private boolean isActive;
    
    // Constructor for new sessions
    public Session(UUID userId, String accessTokenHash, String refreshTokenHash, 
                   LocalDateTime expiresAt, String ipAddress, String userAgent) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "User ID cannot be null");
        this.accessTokenHash = validateTokenHash(accessTokenHash, "Access token hash");
        this.refreshTokenHash = validateTokenHash(refreshTokenHash, "Refresh token hash");
        this.expiresAt = Objects.requireNonNull(expiresAt, "Expiration time cannot be null");
        this.createdAt = LocalDateTime.now();
        this.lastUsedAt = LocalDateTime.now();
        this.ipAddress = validateIpAddress(ipAddress);
        this.userAgent = userAgent != null ? userAgent.trim() : "";
        this.isActive = true;
        
        if (expiresAt.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Session cannot be created with past expiration time");
        }
    }
    
    // Constructor for existing sessions (from database)
    public Session(UUID id, UUID userId, String accessTokenHash, String refreshTokenHash,
                   LocalDateTime expiresAt, LocalDateTime createdAt, LocalDateTime lastUsedAt,
                   String ipAddress, String userAgent, boolean isActive) {
        this.id = Objects.requireNonNull(id, "Session ID cannot be null");
        this.userId = Objects.requireNonNull(userId, "User ID cannot be null");
        this.accessTokenHash = validateTokenHash(accessTokenHash, "Access token hash");
        this.refreshTokenHash = validateTokenHash(refreshTokenHash, "Refresh token hash");
        this.expiresAt = Objects.requireNonNull(expiresAt, "Expiration time cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "Created at cannot be null");
        this.lastUsedAt = Objects.requireNonNull(lastUsedAt, "Last used at cannot be null");
        this.ipAddress = validateIpAddress(ipAddress);
        this.userAgent = userAgent != null ? userAgent.trim() : "";
        this.isActive = isActive;
    }
    
    private String validateTokenHash(String tokenHash, String fieldName) {
        if (tokenHash == null || tokenHash.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
        }
        return tokenHash.trim();
    }
    
    private String validateIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            return "unknown";
        }
        
        String trimmed = ipAddress.trim();
        // Basic IP validation (IPv4 and IPv6)
        if (trimmed.matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$") || 
            trimmed.matches("^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$") ||
            trimmed.equals("unknown")) {
            return trimmed;
        }
        
        throw new IllegalArgumentException("Invalid IP address format: " + ipAddress);
    }
    
    public void updateLastUsed() {
        this.lastUsedAt = LocalDateTime.now();
    }
    
    public void invalidate() {
        this.isActive = false;
    }
    
    public void activate() {
        this.isActive = true;
    }
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
    
    public boolean isValid() {
        return isActive && !isExpired();
    }
    
    public void updateTokens(String newAccessTokenHash, String newRefreshTokenHash, LocalDateTime newExpiresAt) {
        this.accessTokenHash = validateTokenHash(newAccessTokenHash, "New access token hash");
        this.refreshTokenHash = validateTokenHash(newRefreshTokenHash, "New refresh token hash");
        this.expiresAt = Objects.requireNonNull(newExpiresAt, "New expiration time cannot be null");
        updateLastUsed();
        
        if (newExpiresAt.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Cannot update session with past expiration time");
        }
    }
    
    // Getters
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getAccessTokenHash() { return accessTokenHash; }
    public String getRefreshTokenHash() { return refreshTokenHash; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public boolean isActive() { return isActive; }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Session session = (Session) obj;
        return Objects.equals(id, session.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "Session{id=" + id + ", userId=" + userId + ", expiresAt=" + expiresAt + 
               ", active=" + isActive + ", expired=" + isExpired() + ", ipAddress='" + ipAddress + "'}";
    }
}
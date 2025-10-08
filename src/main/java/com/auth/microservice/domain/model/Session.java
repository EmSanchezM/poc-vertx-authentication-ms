package com.auth.microservice.domain.model;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
    private OffsetDateTime expiresAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime lastUsedAt;
    private String ipAddress;
    private String userAgent;
    private String countryCode;
    private boolean isActive;
    
    // Constructor for new sessions
    public Session(UUID userId, String accessTokenHash, String refreshTokenHash, 
                   OffsetDateTime expiresAt, String ipAddress, String userAgent, String countryCode) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "User ID cannot be null");
        this.accessTokenHash = validateTokenHash(accessTokenHash, "Access token hash");
        this.refreshTokenHash = validateTokenHash(refreshTokenHash, "Refresh token hash");
        this.expiresAt = Objects.requireNonNull(expiresAt, "Expiration time cannot be null");
        this.createdAt = OffsetDateTime.now();
        this.lastUsedAt = OffsetDateTime.now();
        this.ipAddress = validateIpAddress(ipAddress);
        this.userAgent = userAgent != null ? userAgent.trim() : "";
        this.countryCode = countryCode != null ? countryCode : "";
        this.isActive = true;
        
        if (expiresAt.isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Session cannot be created with past expiration time");
        }
    }
    
    // Constructor for existing sessions (from database)
    public Session(UUID id, UUID userId, String accessTokenHash, String refreshTokenHash,
                   OffsetDateTime expiresAt2, OffsetDateTime createdAt2, OffsetDateTime lastUsedAt2,
                   String ipAddress, String userAgent, String countryCode, boolean isActive) {
        this.id = Objects.requireNonNull(id, "Session ID cannot be null");
        this.userId = Objects.requireNonNull(userId, "User ID cannot be null");
        this.accessTokenHash = validateTokenHash(accessTokenHash, "Access token hash");
        this.refreshTokenHash = validateTokenHash(refreshTokenHash, "Refresh token hash");
        this.expiresAt = Objects.requireNonNull(expiresAt2, "Expiration time cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt2, "Created at cannot be null");
        this.lastUsedAt = Objects.requireNonNull(lastUsedAt2, "Last used at cannot be null");
        this.ipAddress = validateIpAddress(ipAddress);
        this.userAgent = userAgent != null ? userAgent.trim() : "";
        this.countryCode = countryCode != null ? countryCode : "";
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
        this.lastUsedAt = OffsetDateTime.now();
    }
    
    public void invalidate() {
        this.isActive = false;
    }
    
    public void activate() {
        this.isActive = true;
    }
    
    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(expiresAt);
    }
    
    public boolean isValid() {
        return isActive && !isExpired();
    }
    
    public void updateTokens(String newAccessTokenHash, String newRefreshTokenHash, OffsetDateTime newExpiresAt) {
        this.accessTokenHash = validateTokenHash(newAccessTokenHash, "New access token hash");
        this.refreshTokenHash = validateTokenHash(newRefreshTokenHash, "New refresh token hash");
        this.expiresAt = Objects.requireNonNull(newExpiresAt, "New expiration time cannot be null");
        updateLastUsed();
        
        if (newExpiresAt.isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Cannot update session with past expiration time");
        }
    }
    
    // Getters
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getAccessTokenHash() { return accessTokenHash; }
    public String getRefreshTokenHash() { return refreshTokenHash; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getLastUsedAt() { return lastUsedAt; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public String getCountryCode() { return countryCode; }
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
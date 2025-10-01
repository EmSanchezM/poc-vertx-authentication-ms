package com.auth.microservice.application.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * DTO for authentication response
 */
public class AuthResponse {
    @JsonProperty("accessToken")
    private String accessToken;
    
    @JsonProperty("refreshToken")
    private String refreshToken;
    
    @JsonProperty("tokenType")
    private String tokenType;
    
    @JsonProperty("expiresIn")
    private long expiresIn; // seconds
    
    @JsonProperty("expiresAt")
    private LocalDateTime expiresAt;
    
    @JsonProperty("user")
    private UserResponse user;
    
    // Default constructor for JSON serialization
    public AuthResponse() {}
    
    public AuthResponse(String accessToken, String refreshToken, String tokenType, 
                       long expiresIn, LocalDateTime expiresAt, UserResponse user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.expiresAt = expiresAt;
        this.user = user;
    }
    
    public String getAccessToken() {
        return accessToken;
    }
    
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
    
    public String getRefreshToken() {
        return refreshToken;
    }
    
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
    
    public String getTokenType() {
        return tokenType;
    }
    
    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }
    
    public long getExpiresIn() {
        return expiresIn;
    }
    
    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }
    
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public UserResponse getUser() {
        return user;
    }
    
    public void setUser(UserResponse user) {
        this.user = user;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        AuthResponse that = (AuthResponse) obj;
        return expiresIn == that.expiresIn &&
               Objects.equals(accessToken, that.accessToken) &&
               Objects.equals(refreshToken, that.refreshToken) &&
               Objects.equals(tokenType, that.tokenType) &&
               Objects.equals(expiresAt, that.expiresAt) &&
               Objects.equals(user, that.user);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(accessToken, refreshToken, tokenType, expiresIn, expiresAt, user);
    }
    
    @Override
    public String toString() {
        return "AuthResponse{tokenType='" + tokenType + "', expiresIn=" + expiresIn + 
               ", expiresAt=" + expiresAt + ", user=" + user + "}";
    }
}
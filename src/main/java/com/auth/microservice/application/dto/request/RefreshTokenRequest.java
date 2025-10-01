package com.auth.microservice.application.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * DTO for refresh token request
 */
public class RefreshTokenRequest {
    @JsonProperty("refreshToken")
    private String refreshToken;
    
    // Default constructor for JSON deserialization
    public RefreshTokenRequest() {}
    
    public RefreshTokenRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }
    
    public String getRefreshToken() {
        return refreshToken;
    }
    
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        RefreshTokenRequest that = (RefreshTokenRequest) obj;
        return Objects.equals(refreshToken, that.refreshToken);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(refreshToken);
    }
    
    @Override
    public String toString() {
        return "RefreshTokenRequest{refreshToken='***'}";
    }
}
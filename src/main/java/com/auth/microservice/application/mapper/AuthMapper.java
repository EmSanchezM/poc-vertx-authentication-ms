package com.auth.microservice.application.mapper;

import com.auth.microservice.application.dto.response.AuthResponse;
import com.auth.microservice.domain.model.Token;
import com.auth.microservice.domain.model.User;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Mapper for authentication-related DTOs
 */
public class AuthMapper {
    
    private AuthMapper() {
        // Utility class
    }
    
    /**
     * Maps authentication data to AuthResponse DTO
     */
    public static AuthResponse toAuthResponse(Token accessToken, Token refreshToken, User user) {
        if (accessToken == null || refreshToken == null || user == null) {
            return null;
        }
        
        LocalDateTime now = LocalDateTime.now();
        long expiresInSeconds = Duration.between(now, accessToken.getExpiresAt()).getSeconds();
        
        return new AuthResponse(
            accessToken.getValue(),
            refreshToken.getValue(),
            "Bearer",
            expiresInSeconds,
            accessToken.getExpiresAt(),
            UserMapper.toResponseWithoutRoles(user)
        );
    }
    
    /**
     * Maps token refresh data to AuthResponse DTO
     */
    public static AuthResponse toRefreshResponse(Token newAccessToken, Token newRefreshToken) {
        if (newAccessToken == null || newRefreshToken == null) {
            return null;
        }
        
        LocalDateTime now = LocalDateTime.now();
        long expiresInSeconds = Duration.between(now, newAccessToken.getExpiresAt()).getSeconds();
        
        return new AuthResponse(
            newAccessToken.getValue(),
            newRefreshToken.getValue(),
            "Bearer",
            expiresInSeconds,
            newAccessToken.getExpiresAt(),
            null // User info not included in refresh response
        );
    }
}
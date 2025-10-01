package com.auth.microservice.application.mapper;

import com.auth.microservice.application.dto.response.AuthResponse;
import com.auth.microservice.domain.model.Email;
import com.auth.microservice.domain.model.Token;
import com.auth.microservice.domain.model.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AuthMapperTest {
    
    @Test
    void shouldMapToAuthResponse() {
        // Create tokens
        LocalDateTime accessExpiry = LocalDateTime.now().plusMinutes(15);
        LocalDateTime refreshExpiry = LocalDateTime.now().plusDays(7);
        Token accessToken = new Token("access.jwt.token", accessExpiry, Token.TokenType.ACCESS);
        Token refreshToken = new Token("refresh.jwt.token", refreshExpiry, Token.TokenType.REFRESH);
        
        // Create user
        Email email = new Email("test@example.com");
        User user = new User("testuser", email, "hashedPassword", "John", "Doe");
        
        AuthResponse response = AuthMapper.toAuthResponse(accessToken, refreshToken, user);
        
        assertNotNull(response);
        assertEquals("access.jwt.token", response.getAccessToken());
        assertEquals("refresh.jwt.token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertTrue(response.getExpiresIn() > 0); // Should be positive seconds
        assertEquals(accessExpiry, response.getExpiresAt());
        
        assertNotNull(response.getUser());
        assertEquals(user.getId().toString(), response.getUser().getId());
        assertEquals("testuser", response.getUser().getUsername());
        assertEquals("test@example.com", response.getUser().getEmail());
        assertTrue(response.getUser().getRoles().isEmpty()); // Without roles for performance
    }
    
    @Test
    void shouldMapToRefreshResponse() {
        LocalDateTime accessExpiry = LocalDateTime.now().plusMinutes(15);
        LocalDateTime refreshExpiry = LocalDateTime.now().plusDays(7);
        Token newAccessToken = new Token("new.access.jwt.token", accessExpiry, Token.TokenType.ACCESS);
        Token newRefreshToken = new Token("new.refresh.jwt.token", refreshExpiry, Token.TokenType.REFRESH);
        
        AuthResponse response = AuthMapper.toRefreshResponse(newAccessToken, newRefreshToken);
        
        assertNotNull(response);
        assertEquals("new.access.jwt.token", response.getAccessToken());
        assertEquals("new.refresh.jwt.token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertTrue(response.getExpiresIn() > 0);
        assertEquals(accessExpiry, response.getExpiresAt());
        assertNull(response.getUser()); // User not included in refresh response
    }
    
    @Test
    void shouldReturnNullWhenParametersAreNull() {
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(15);
        Token accessToken = new Token("access.token", expiry, Token.TokenType.ACCESS);
        Token refreshToken = new Token("refresh.token", expiry, Token.TokenType.REFRESH);
        Email email = new Email("test@example.com");
        User user = new User("testuser", email, "hash", "John", "Doe");
        
        // Test null access token
        AuthResponse response1 = AuthMapper.toAuthResponse(null, refreshToken, user);
        assertNull(response1);
        
        // Test null refresh token
        AuthResponse response2 = AuthMapper.toAuthResponse(accessToken, null, user);
        assertNull(response2);
        
        // Test null user
        AuthResponse response3 = AuthMapper.toAuthResponse(accessToken, refreshToken, null);
        assertNull(response3);
        
        // Test null tokens in refresh
        AuthResponse response4 = AuthMapper.toRefreshResponse(null, refreshToken);
        assertNull(response4);
        
        AuthResponse response5 = AuthMapper.toRefreshResponse(accessToken, null);
        assertNull(response5);
    }
    
    @Test
    void shouldCalculateCorrectExpiresIn() {
        // Create token that expires in exactly 900 seconds (15 minutes)
        LocalDateTime expiry = LocalDateTime.now().plusSeconds(900);
        Token accessToken = new Token("access.token", expiry, Token.TokenType.ACCESS);
        Token refreshToken = new Token("refresh.token", expiry.plusDays(7), Token.TokenType.REFRESH);
        
        Email email = new Email("test@example.com");
        User user = new User("testuser", email, "hash", "John", "Doe");
        
        AuthResponse response = AuthMapper.toAuthResponse(accessToken, refreshToken, user);
        
        assertNotNull(response);
        // Should be approximately 900 seconds (allowing for small timing differences)
        assertTrue(response.getExpiresIn() >= 895 && response.getExpiresIn() <= 900);
    }
}
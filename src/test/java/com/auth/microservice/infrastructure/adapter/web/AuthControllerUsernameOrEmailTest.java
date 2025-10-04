package com.auth.microservice.infrastructure.adapter.web;

import com.auth.microservice.infrastructure.adapter.web.validation.RequestValidator;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test to verify usernameOrEmail functionality in AuthController
 */
class AuthControllerUsernameOrEmailTest {
    
    @Test
    void testValidateLoginRequest_WithEmail_ShouldSucceed() {
        // Arrange
        JsonObject loginRequest = new JsonObject()
            .put("usernameOrEmail", "test@example.com")
            .put("password", "TestPassword123!");
        
        // Act
        RequestValidator.ValidationResult result = RequestValidator.validateLoginRequest(loginRequest);
        
        // Assert
        assertTrue(result.isValid(), "Login request with email should be valid");
    }
    
    @Test
    void testValidateLoginRequest_WithUsername_ShouldSucceed() {
        // Arrange
        JsonObject loginRequest = new JsonObject()
            .put("usernameOrEmail", "testuser")
            .put("password", "TestPassword123!");
        
        // Act
        RequestValidator.ValidationResult result = RequestValidator.validateLoginRequest(loginRequest);
        
        // Assert
        assertTrue(result.isValid(), "Login request with username should be valid");
    }
    
    @Test
    void testValidateLoginRequest_WithInvalidEmail_ShouldFail() {
        // Arrange
        JsonObject loginRequest = new JsonObject()
            .put("usernameOrEmail", "invalid-email")
            .put("password", "TestPassword123!");
        
        // Act
        RequestValidator.ValidationResult result = RequestValidator.validateLoginRequest(loginRequest);
        
        // Assert
        assertFalse(result.isValid(), "Login request with invalid email should fail");
        assertTrue(result.getErrorMessage().contains("Email format is invalid"));
    }
    
    @Test
    void testValidateLoginRequest_WithEmptyUsernameOrEmail_ShouldFail() {
        // Arrange
        JsonObject loginRequest = new JsonObject()
            .put("usernameOrEmail", "")
            .put("password", "TestPassword123!");
        
        // Act
        RequestValidator.ValidationResult result = RequestValidator.validateLoginRequest(loginRequest);
        
        // Assert
        assertFalse(result.isValid(), "Login request with empty usernameOrEmail should fail");
        assertTrue(result.getErrorMessage().contains("Username or email is required"));
    }
    
    @Test
    void testValidateLoginRequest_WithMissingUsernameOrEmail_ShouldFail() {
        // Arrange
        JsonObject loginRequest = new JsonObject()
            .put("password", "TestPassword123!");
        
        // Act
        RequestValidator.ValidationResult result = RequestValidator.validateLoginRequest(loginRequest);
        
        // Assert
        assertFalse(result.isValid(), "Login request without usernameOrEmail should fail");
        assertTrue(result.getErrorMessage().contains("Username or email is required"));
    }
    
    @Test
    void testValidateLoginRequest_WithMissingPassword_ShouldFail() {
        // Arrange
        JsonObject loginRequest = new JsonObject()
            .put("usernameOrEmail", "test@example.com");
        
        // Act
        RequestValidator.ValidationResult result = RequestValidator.validateLoginRequest(loginRequest);
        
        // Assert
        assertFalse(result.isValid(), "Login request without password should fail");
        assertTrue(result.getErrorMessage().contains("Password is required"));
    }
}
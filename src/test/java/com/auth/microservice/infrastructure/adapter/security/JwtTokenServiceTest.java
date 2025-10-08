package com.auth.microservice.infrastructure.adapter.security;

import com.auth.microservice.domain.service.JWTService;
import io.jsonwebtoken.security.Keys;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtTokenService Tests")
class JwtTokenServiceTest {
    
    private JwtTokenService jwtService;
    private SecretKey testKey;
    
    @BeforeEach
    void setUp() {
        testKey = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256);
        jwtService = new JwtTokenService(testKey, "test-issuer", 900, 604800); // 15 min access, 7 days refresh
    }
    
    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {
        
        @Test
        @DisplayName("Should create service with valid configuration")
        void shouldCreateServiceWithValidConfiguration() {
            // Given
            JsonObject config = new JsonObject()
                .put("jwt.secret", "test-secret-key-for-jwt-signing-must-be-long-enough")
                .put("jwt.issuer", "test-issuer")
                .put("jwt.accessTokenExpiry", 900)
                .put("jwt.refreshTokenExpiry", 604800);
            
            // When & Then
            assertThatCode(() -> new JwtTokenService(config))
                .doesNotThrowAnyException();
        }
        
        @Test
        @DisplayName("Should fail with default secret")
        void shouldFailWithDefaultSecret() {
            // Given
            JsonObject config = new JsonObject()
                .put("jwt.secret", "default-jwt-secret-change-in-production")
                .put("jwt.issuer", "test-issuer");
            
            // When & Then
            assertThatThrownBy(() -> new JwtTokenService(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT secret must be configured and not use default value in production");
        }
        
        @Test
        @DisplayName("Should fail with empty secret")
        void shouldFailWithEmptySecret() {
            // Given
            JsonObject config = new JsonObject()
                .put("jwt.secret", "")
                .put("jwt.issuer", "test-issuer");
            
            // When & Then
            assertThatThrownBy(() -> new JwtTokenService(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT secret must be configured and not use default value in production");
        }
        
        @Test
        @DisplayName("Should use default values when not configured")
        void shouldUseDefaultValuesWhenNotConfigured() {
            // Given
            JsonObject config = new JsonObject()
                .put("jwt.secret", "test-secret-key-for-jwt-signing-must-be-long-enough");
            
            // When
            JwtTokenService service = new JwtTokenService(config);
            String token = service.generateAccessToken("user123", "user@example.com", Set.of());
            
            // Then - Should work with defaults
            assertThat(token).isNotNull();
            assertThat(service.validateToken(token).isValid()).isTrue();
        }
    }
    
    @Nested
    @DisplayName("Access Token Generation Tests")
    class AccessTokenGenerationTests {
        
        @Test
        @DisplayName("Should generate valid access token")
        void shouldGenerateValidAccessToken() {
            // Given
            String userId = "user123";
            String email = "user@example.com";
            Set<String> permissions = Set.of("READ_USERS", "WRITE_USERS");
            
            // When
            String token = jwtService.generateAccessToken(userId, email, permissions);
            
            // Then
            assertThat(token).isNotNull();
            assertThat(token).isNotEmpty();
            assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts
            
            // Validate the token
            JWTService.TokenValidationResult result = jwtService.validateToken(token);
            assertThat(result.isValid()).isTrue();
            assertThat(result.message()).isEqualTo("Token is valid");
        }
        
        @Test
        @DisplayName("Should include correct claims in access token")
        void shouldIncludeCorrectClaimsInAccessToken() {
            // Given
            String userId = "user123";
            String email = "user@example.com";
            Set<String> permissions = Set.of("READ_USERS", "WRITE_USERS");
            
            // When
            String token = jwtService.generateAccessToken(userId, email, permissions);
            
            // Then
            assertThat(jwtService.extractUserId(token)).contains(userId);
            assertThat(jwtService.extractUserEmail(token)).contains(email);
            assertThat(jwtService.extractPermissions(token)).containsExactlyInAnyOrderElementsOf(permissions);
            assertThat(jwtService.isAccessToken(token)).isTrue();
            assertThat(jwtService.isRefreshToken(token)).isFalse();
        }
        
        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   "})
        @DisplayName("Should throw exception for empty user ID")
        void shouldThrowExceptionForEmptyUserId(String userId) {
            // Given
            String email = "user@example.com";
            Set<String> permissions = Set.of("READ_USERS");
            
            // When & Then
            assertThatThrownBy(() -> jwtService.generateAccessToken(userId, email, permissions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
        }
        
        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   "})
        @DisplayName("Should throw exception for empty email")
        void shouldThrowExceptionForEmptyEmail(String email) {
            // Given
            String userId = "user123";
            Set<String> permissions = Set.of("READ_USERS");
            
            // When & Then
            assertThatThrownBy(() -> jwtService.generateAccessToken(userId, email, permissions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email cannot be null or empty");
        }
        
        @Test
        @DisplayName("Should handle empty permissions set")
        void shouldHandleEmptyPermissionsSet() {
            // Given
            String userId = "user123";
            String email = "user@example.com";
            Set<String> permissions = Set.of();
            
            // When
            String token = jwtService.generateAccessToken(userId, email, permissions);
            
            // Then
            assertThat(token).isNotNull();
            assertThat(jwtService.extractPermissions(token)).isEmpty();
        }
    }
    
    @Nested
    @DisplayName("Refresh Token Generation Tests")
    class RefreshTokenGenerationTests {
        
        @Test
        @DisplayName("Should generate valid refresh token")
        void shouldGenerateValidRefreshToken() {
            // Given
            String userId = "user123";
            String email = "user@example.com";
            
            // When
            String token = jwtService.generateRefreshToken(userId, email);
            
            // Then
            assertThat(token).isNotNull();
            assertThat(token).isNotEmpty();
            assertThat(token.split("\\.")).hasSize(3);
            
            // Validate the token
            JWTService.TokenValidationResult result = jwtService.validateToken(token);
            assertThat(result.isValid()).isTrue();
        }
        
        @Test
        @DisplayName("Should include correct claims in refresh token")
        void shouldIncludeCorrectClaimsInRefreshToken() {
            // Given
            String userId = "user123";
            String email = "user@example.com";
            
            // When
            String token = jwtService.generateRefreshToken(userId, email);
            
            // Then
            assertThat(jwtService.extractUserId(token)).contains(userId);
            assertThat(jwtService.extractUserEmail(token)).contains(email);
            assertThat(jwtService.extractPermissions(token)).isEmpty(); // Refresh tokens don't have permissions
            assertThat(jwtService.isRefreshToken(token)).isTrue();
            assertThat(jwtService.isAccessToken(token)).isFalse();
        }
        
        @Test
        @DisplayName("Should have longer expiration than access token")
        void shouldHaveLongerExpirationThanAccessToken() {
            // Given
            String userId = "user123";
            String email = "user@example.com";
            Set<String> permissions = Set.of("READ_USERS");
            
            // When
            String accessToken = jwtService.generateAccessToken(userId, email, permissions);
            String refreshToken = jwtService.generateRefreshToken(userId, email);
            
            // Then
            Optional<LocalDateTime> accessExpiration = jwtService.getTokenExpiration(accessToken);
            Optional<LocalDateTime> refreshExpiration = jwtService.getTokenExpiration(refreshToken);
            
            assertThat(accessExpiration).isPresent();
            assertThat(refreshExpiration).isPresent();
            assertThat(refreshExpiration.get()).isAfter(accessExpiration.get());
        }
    }
    
    @Nested
    @DisplayName("Token Pair Generation Tests")
    class TokenPairGenerationTests {
        
        @Test
        @DisplayName("Should generate valid token pair")
        void shouldGenerateValidTokenPair() {
            // Given
            String userId = "user123";
            String email = "user@example.com";
            Set<String> permissions = Set.of("READ_USERS", "WRITE_USERS");
            
            // When
            JWTService.TokenPair tokenPair = jwtService.generateTokenPair(userId, email, permissions);
            
            // Then
            assertThat(tokenPair).isNotNull();
            assertThat(tokenPair.accessToken()).isNotNull();
            assertThat(tokenPair.refreshToken()).isNotNull();
            assertThat(tokenPair.accessTokenExpiration()).isNotNull();
            assertThat(tokenPair.refreshTokenExpiration()).isNotNull();
            
            // Verify both tokens are valid
            assertThat(jwtService.validateToken(tokenPair.accessToken()).isValid()).isTrue();
            assertThat(jwtService.validateToken(tokenPair.refreshToken()).isValid()).isTrue();
            
            // Verify token types
            assertThat(jwtService.isAccessToken(tokenPair.accessToken())).isTrue();
            assertThat(jwtService.isRefreshToken(tokenPair.refreshToken())).isTrue();
        }
        
        @Test
        @DisplayName("Should have correct expiration times in token pair")
        void shouldHaveCorrectExpirationTimesInTokenPair() {
            // Given
            String userId = "user123";
            String email = "user@example.com";
            Set<String> permissions = Set.of("READ_USERS");
            OffsetDateTime beforeGeneration = OffsetDateTime.now();
            
            // When
            JWTService.TokenPair tokenPair = jwtService.generateTokenPair(userId, email, permissions);
            OffsetDateTime afterGeneration = OffsetDateTime.now();

            // Then - Access token expires in ~15 minutes (900 seconds)
            assertThat(tokenPair.accessTokenExpiration()).isAfter(beforeGeneration.plusSeconds(890));
            assertThat(tokenPair.accessTokenExpiration()).isBefore(afterGeneration.plusSeconds(910));
            
            // Refresh token expires in ~7 days (604800 seconds)
            assertThat(tokenPair.refreshTokenExpiration()).isAfter(beforeGeneration.plusSeconds(604790));
            assertThat(tokenPair.refreshTokenExpiration()).isBefore(afterGeneration.plusSeconds(604810));
        }
    }
    
    @Nested
    @DisplayName("Token Validation Tests")
    class TokenValidationTests {
        
        @Test
        @DisplayName("Should validate correct token successfully")
        void shouldValidateCorrectTokenSuccessfully() {
            // Given
            String userId = "user123";
            String email = "user@example.com";
            Set<String> permissions = Set.of("READ_USERS");
            String token = jwtService.generateAccessToken(userId, email, permissions);
            
            // When
            JWTService.TokenValidationResult result = jwtService.validateToken(token);
            
            // Then
            assertThat(result.isValid()).isTrue();
            assertThat(result.message()).isEqualTo("Token is valid");
            assertThat(result.claims()).isNotEmpty();
            assertThat(result.claims()).containsKey("sub");
            assertThat(result.claims()).containsKey("email");
        }
        
        @Test
        @DisplayName("Should reject malformed token")
        void shouldRejectMalformedToken() {
            // Given
            String malformedToken = "not.a.valid.jwt.token";
            
            // When
            JWTService.TokenValidationResult result = jwtService.validateToken(malformedToken);
            
            // Then
            assertThat(result.isValid()).isFalse();
            assertThat(result.message()).isEqualTo("Token is malformed");
            assertThat(result.claims()).isEmpty();
        }
        
        @Test
        @DisplayName("Should reject token with invalid signature")
        void shouldRejectTokenWithInvalidSignature() {
            // Given - Generate token with different key
            SecretKey differentKey = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256);
            JwtTokenService differentService = new JwtTokenService(differentKey, "test-issuer", 900, 604800);
            String token = differentService.generateAccessToken("user123", "user@example.com", Set.of());
            
            // When - Validate with original service (different key)
            JWTService.TokenValidationResult result = jwtService.validateToken(token);
            
            // Then
            assertThat(result.isValid()).isFalse();
            assertThat(result.message()).isEqualTo("Token signature is invalid");
        }
        
        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   "})
        @DisplayName("Should throw exception for empty token")
        void shouldThrowExceptionForEmptyToken(String token) {
            // When & Then
            assertThatThrownBy(() -> jwtService.validateToken(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token cannot be null or empty");
        }
        
        @Test
        @DisplayName("Should throw exception for null token")
        void shouldThrowExceptionForNullToken() {
            // When & Then
            assertThatThrownBy(() -> jwtService.validateToken(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token cannot be null or empty");
        }
    }
    
    @Nested
    @DisplayName("Token Extraction Tests")
    class TokenExtractionTests {
        
        @Test
        @DisplayName("Should extract user ID correctly")
        void shouldExtractUserIdCorrectly() {
            // Given
            String userId = "user123";
            String email = "user@example.com";
            String token = jwtService.generateAccessToken(userId, email, Set.of());
            
            // When
            Optional<String> extractedUserId = jwtService.extractUserId(token);
            
            // Then
            assertThat(extractedUserId).isPresent();
            assertThat(extractedUserId.get()).isEqualTo(userId);
        }
        
        @Test
        @DisplayName("Should extract email correctly")
        void shouldExtractEmailCorrectly() {
            // Given
            String userId = "user123";
            String email = "user@example.com";
            String token = jwtService.generateAccessToken(userId, email, Set.of());
            
            // When
            Optional<String> extractedEmail = jwtService.extractUserEmail(token);
            
            // Then
            assertThat(extractedEmail).isPresent();
            assertThat(extractedEmail.get()).isEqualTo(email);
        }
        
        @Test
        @DisplayName("Should extract permissions correctly")
        void shouldExtractPermissionsCorrectly() {
            // Given
            String userId = "user123";
            String email = "user@example.com";
            Set<String> permissions = Set.of("READ_USERS", "WRITE_USERS", "DELETE_USERS");
            String token = jwtService.generateAccessToken(userId, email, permissions);
            
            // When
            Set<String> extractedPermissions = jwtService.extractPermissions(token);
            
            // Then
            assertThat(extractedPermissions).containsExactlyInAnyOrderElementsOf(permissions);
        }
        
        @Test
        @DisplayName("Should return empty for invalid token extraction")
        void shouldReturnEmptyForInvalidTokenExtraction() {
            // Given
            String invalidToken = "invalid.token.here";
            
            // When & Then
            assertThat(jwtService.extractUserId(invalidToken)).isEmpty();
            assertThat(jwtService.extractUserEmail(invalidToken)).isEmpty();
            assertThat(jwtService.extractPermissions(invalidToken)).isEmpty();
            assertThat(jwtService.getTokenExpiration(invalidToken)).isEmpty();
        }
    }
    
    @Nested
    @DisplayName("Token Expiration Tests")
    class TokenExpirationTests {
        
        @Test
        @DisplayName("Should correctly identify non-expired token")
        void shouldCorrectlyIdentifyNonExpiredToken() {
            // Given
            String userId = "user123";
            String email = "user@example.com";
            String token = jwtService.generateAccessToken(userId, email, Set.of());
            
            // When
            boolean isExpired = jwtService.isTokenExpired(token);
            
            // Then
            assertThat(isExpired).isFalse();
        }
        
        @Test
        @DisplayName("Should get token expiration time")
        void shouldGetTokenExpirationTime() {
            // Given
            String userId = "user123";
            String email = "user@example.com";
            OffsetDateTime beforeGeneration = OffsetDateTime.now();
            String token = jwtService.generateAccessToken(userId, email, Set.of());
            OffsetDateTime afterGeneration = OffsetDateTime.now();
            
            // When
            Optional<LocalDateTime> expiration = jwtService.getTokenExpiration(token);
            
            // Then
            assertThat(expiration).isPresent();
            assertThat(expiration.get()).isAfter(beforeGeneration.plusSeconds(890).toLocalDateTime());
            assertThat(expiration.get()).isBefore(afterGeneration.plusSeconds(910).toLocalDateTime());
        }
        
        @Test
        @DisplayName("Should handle invalid token as expired")
        void shouldHandleInvalidTokenAsExpired() {
            // Given
            String invalidToken = "invalid.token.here";
            
            // When
            boolean isExpired = jwtService.isTokenExpired(invalidToken);
            
            // Then
            assertThat(isExpired).isTrue();
        }
    }
    
    @Nested
    @DisplayName("Security Tests")
    class SecurityTests {
        
        @Test
        @DisplayName("Should generate different tokens for same user")
        void shouldGenerateDifferentTokensForSameUser() {
            // Given
            String userId = "user123";
            String email = "user@example.com";
            Set<String> permissions = Set.of("READ_USERS");
            
            // When
            String token1 = jwtService.generateAccessToken(userId, email, permissions);
            String token2 = jwtService.generateAccessToken(userId, email, permissions);
            
            // Then
            assertThat(token1).isNotEqualTo(token2);
        }
        
        @Test
        @DisplayName("Should include unique JTI in tokens")
        void shouldIncludeUniqueJtiInTokens() {
            // Given
            String userId = "user123";
            String email = "user@example.com";
            Set<String> permissions = Set.of("READ_USERS");
            
            // When
            String token1 = jwtService.generateAccessToken(userId, email, permissions);
            String token2 = jwtService.generateAccessToken(userId, email, permissions);
            
            JWTService.TokenValidationResult result1 = jwtService.validateToken(token1);
            JWTService.TokenValidationResult result2 = jwtService.validateToken(token2);
            
            // Then
            assertThat(result1.claims().get("jti")).isNotNull();
            assertThat(result2.claims().get("jti")).isNotNull();
            assertThat(result1.claims().get("jti")).isNotEqualTo(result2.claims().get("jti"));
        }
        
        @Test
        @DisplayName("Should handle special characters in email")
        void shouldHandleSpecialCharactersInEmail() {
            // Given
            String userId = "user123";
            String email = "user+test@example-domain.com";
            Set<String> permissions = Set.of("READ_USERS");
            
            // When
            String token = jwtService.generateAccessToken(userId, email, permissions);
            
            // Then
            assertThat(token).isNotNull();
            assertThat(jwtService.extractUserEmail(token)).contains(email);
            assertThat(jwtService.validateToken(token).isValid()).isTrue();
        }
    }
}
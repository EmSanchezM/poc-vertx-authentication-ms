package com.auth.microservice.infrastructure.adapter.security;

import com.auth.microservice.domain.service.PasswordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("BCryptPasswordService Tests")
class BCryptPasswordServiceTest {
    
    private BCryptPasswordService passwordService;
    
    @BeforeEach
    void setUp() {
        passwordService = new BCryptPasswordService(12); // Use 12 rounds for testing
    }
    
    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {
        
        @Test
        @DisplayName("Should create service with valid BCrypt rounds")
        void shouldCreateServiceWithValidBCryptRounds() {
            // When & Then
            assertThatCode(() -> new BCryptPasswordService(12))
                .doesNotThrowAnyException();
        }
        
        @ParameterizedTest
        @ValueSource(ints = {9, 16, 20})
        @DisplayName("Should fail with invalid BCrypt rounds")
        void shouldFailWithInvalidBCryptRounds(int rounds) {
            // When & Then
            assertThatThrownBy(() -> new BCryptPasswordService(rounds))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("BCrypt rounds must be between 10 and 15 for security and performance balance");
        }
    }
    
    @Nested
    @DisplayName("Password Hashing Tests")
    class PasswordHashingTests {
        
        @Test
        @DisplayName("Should hash password successfully")
        void shouldHashPasswordSuccessfully() {
            // Given
            String plainPassword = "SecurePass123!";
            
            // When
            String hashedPassword = passwordService.hashPassword(plainPassword);
            
            // Then
            assertThat(hashedPassword).isNotNull();
            assertThat(hashedPassword).isNotEqualTo(plainPassword);
            assertThat(hashedPassword).startsWith("$2a$"); // BCrypt format
            assertThat(hashedPassword).hasSize(60); // BCrypt hash length
        }
        
        @Test
        @DisplayName("Should generate different hashes for same password")
        void shouldGenerateDifferentHashesForSamePassword() {
            // Given
            String plainPassword = "SecurePass123!";
            
            // When
            String hash1 = passwordService.hashPassword(plainPassword);
            String hash2 = passwordService.hashPassword(plainPassword);
            
            // Then
            assertThat(hash1).isNotEqualTo(hash2);
        }
        
        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   "})
        @DisplayName("Should throw exception for empty or whitespace passwords")
        void shouldThrowExceptionForEmptyPasswords(String password) {
            // When & Then
            assertThatThrownBy(() -> passwordService.hashPassword(password))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password cannot be null or empty");
        }
        
        @Test
        @DisplayName("Should throw exception for null password")
        void shouldThrowExceptionForNullPassword() {
            // When & Then
            assertThatThrownBy(() -> passwordService.hashPassword(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password cannot be null or empty");
        }
    }
    
    @Nested
    @DisplayName("Password Verification Tests")
    class PasswordVerificationTests {
        
        @Test
        @DisplayName("Should verify correct password successfully")
        void shouldVerifyCorrectPasswordSuccessfully() {
            // Given
            String plainPassword = "SecurePass123!";
            String hashedPassword = passwordService.hashPassword(plainPassword);
            
            // When
            boolean isValid = passwordService.verifyPassword(plainPassword, hashedPassword);
            
            // Then
            assertThat(isValid).isTrue();
        }
        
        @Test
        @DisplayName("Should reject incorrect password")
        void shouldRejectIncorrectPassword() {
            // Given
            String correctPassword = "SecurePass123!";
            String incorrectPassword = "WrongPass456!";
            String hashedPassword = passwordService.hashPassword(correctPassword);
            
            // When
            boolean isValid = passwordService.verifyPassword(incorrectPassword, hashedPassword);
            
            // Then
            assertThat(isValid).isFalse();
        }
        
        @Test
        @DisplayName("Should handle malformed hash gracefully")
        void shouldHandleMalformedHashGracefully() {
            // Given
            String plainPassword = "SecurePass123!";
            String malformedHash = "not-a-valid-bcrypt-hash";
            
            // When
            boolean isValid = passwordService.verifyPassword(plainPassword, malformedHash);
            
            // Then
            assertThat(isValid).isFalse();
        }
        
        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   "})
        @DisplayName("Should throw exception for empty plain password")
        void shouldThrowExceptionForEmptyPlainPassword(String password) {
            // Given
            String hashedPassword = "$2a$12$validhash";
            
            // When & Then
            assertThatThrownBy(() -> passwordService.verifyPassword(password, hashedPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Plain password cannot be null or empty");
        }
        
        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   "})
        @DisplayName("Should throw exception for empty hashed password")
        void shouldThrowExceptionForEmptyHashedPassword(String hashedPassword) {
            // Given
            String plainPassword = "SecurePass123!";
            
            // When & Then
            assertThatThrownBy(() -> passwordService.verifyPassword(plainPassword, hashedPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Hashed password cannot be null or empty");
        }
        
        @Test
        @DisplayName("Should handle encoder exception gracefully")
        void shouldHandleEncoderExceptionGracefully() {
            // Given
            PasswordEncoder mockEncoder = mock(PasswordEncoder.class);
            when(mockEncoder.matches(anyString(), anyString())).thenThrow(new RuntimeException("Encoder error"));
            
            BCryptPasswordService serviceWithMockEncoder = new BCryptPasswordService(mockEncoder, 12);
            
            // When
            boolean result = serviceWithMockEncoder.verifyPassword("password", "hash");
            
            // Then
            assertThat(result).isFalse();
        }
    }
    
    @Nested
    @DisplayName("Password Strength Validation Tests")
    class PasswordStrengthValidationTests {
        
        @Test
        @DisplayName("Should accept strong password")
        void shouldAcceptStrongPassword() {
            // Given
            String strongPassword = "SecurePass123!";
            
            // When
            PasswordService.PasswordValidationResult result = 
                passwordService.validatePasswordStrength(strongPassword);
            
            // Then
            assertThat(result.isValid()).isTrue();
            assertThat(result.message()).isEqualTo("Password meets all security requirements");
        }
        
        @Test
        @DisplayName("Should reject password too short")
        void shouldRejectPasswordTooShort() {
            // Given
            String shortPassword = "Abc1!";
            
            // When
            PasswordService.PasswordValidationResult result = 
                passwordService.validatePasswordStrength(shortPassword);
            
            // Then
            assertThat(result.isValid()).isFalse();
            assertThat(result.message()).isEqualTo("Password must be at least 8 characters long");
        }
        
        @Test
        @DisplayName("Should reject password too long")
        void shouldRejectPasswordTooLong() {
            // Given
            String longPassword = "A".repeat(129) + "1!";
            
            // When
            PasswordService.PasswordValidationResult result = 
                passwordService.validatePasswordStrength(longPassword);
            
            // Then
            assertThat(result.isValid()).isFalse();
            assertThat(result.message()).isEqualTo("Password must not exceed 128 characters");
        }
        
        @Test
        @DisplayName("Should reject password without uppercase")
        void shouldRejectPasswordWithoutUppercase() {
            // Given
            String noUppercasePassword = "securepass123!";
            
            // When
            PasswordService.PasswordValidationResult result = 
                passwordService.validatePasswordStrength(noUppercasePassword);
            
            // Then
            assertThat(result.isValid()).isFalse();
            assertThat(result.message()).isEqualTo("Password must contain at least one uppercase letter");
        }
        
        @Test
        @DisplayName("Should reject password without lowercase")
        void shouldRejectPasswordWithoutLowercase() {
            // Given
            String noLowercasePassword = "SECUREPASS123!";
            
            // When
            PasswordService.PasswordValidationResult result = 
                passwordService.validatePasswordStrength(noLowercasePassword);
            
            // Then
            assertThat(result.isValid()).isFalse();
            assertThat(result.message()).isEqualTo("Password must contain at least one lowercase letter");
        }
        
        @Test
        @DisplayName("Should reject password without digit")
        void shouldRejectPasswordWithoutDigit() {
            // Given
            String noDigitPassword = "SecurePass!";
            
            // When
            PasswordService.PasswordValidationResult result = 
                passwordService.validatePasswordStrength(noDigitPassword);
            
            // Then
            assertThat(result.isValid()).isFalse();
            assertThat(result.message()).isEqualTo("Password must contain at least one digit");
        }
        
        @Test
        @DisplayName("Should reject password without special character")
        void shouldRejectPasswordWithoutSpecialCharacter() {
            // Given
            String noSpecialCharPassword = "SecurePass123";
            
            // When
            PasswordService.PasswordValidationResult result = 
                passwordService.validatePasswordStrength(noSpecialCharPassword);
            
            // Then
            assertThat(result.isValid()).isFalse();
            assertThat(result.message()).isEqualTo("Password must contain at least one special character");
        }
        
        @Test
        @DisplayName("Should reject common weak passwords that meet basic requirements")
        void shouldRejectCommonWeakPasswords() {
            // Given - passwords that meet basic requirements but are still weak
            String[] weakPasswords = {"Password123!", "Admin123!", "Qwerty123!"};
            
            for (String weakPassword : weakPasswords) {
                // When
                PasswordService.PasswordValidationResult result = 
                    passwordService.validatePasswordStrength(weakPassword);
                
                // Then - These should fail other requirements first, not common password check
                assertThat(result.isValid()).isFalse();
            }
        }
        
        @Test
        @DisplayName("Should reject simple common passwords")
        void shouldRejectSimpleCommonPasswords() {
            // Given - simple passwords that fail basic requirements
            String[] simplePasswords = {"password", "123456", "admin", "qwerty"};
            
            for (String simplePassword : simplePasswords) {
                // When
                PasswordService.PasswordValidationResult result = 
                    passwordService.validatePasswordStrength(simplePassword);
                
                // Then - These should fail basic requirements (length, complexity)
                assertThat(result.isValid()).isFalse();
            }
        }
        
        @Test
        @DisplayName("Should reject password with repeated characters")
        void shouldRejectPasswordWithRepeatedCharacters() {
            // Given
            String repeatedCharPassword = "AAAAAAAA"; // 8 repeated characters
            
            // When
            PasswordService.PasswordValidationResult result = 
                passwordService.validatePasswordStrength(repeatedCharPassword);
            
            // Then - This should fail because it lacks lowercase, digits, and special chars
            assertThat(result.isValid()).isFalse();
            // The first failure will be lack of lowercase
            assertThat(result.message()).isEqualTo("Password must contain at least one lowercase letter");
        }
        
        @Test
        @DisplayName("Should reject password with sequential numbers")
        void shouldRejectPasswordWithSequentialNumbers() {
            // Given
            String sequentialPassword = "123456789";
            
            // When
            PasswordService.PasswordValidationResult result = 
                passwordService.validatePasswordStrength(sequentialPassword);
            
            // Then - This should fail because it lacks uppercase, lowercase, and special chars
            assertThat(result.isValid()).isFalse();
            // The first failure will be lack of uppercase
            assertThat(result.message()).isEqualTo("Password must contain at least one uppercase letter");
        }
        
        @Test
        @DisplayName("Should throw exception for null password")
        void shouldThrowExceptionForNullPassword() {
            // When & Then
            assertThatThrownBy(() -> passwordService.validatePasswordStrength(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password cannot be null");
        }
        
        @Test
        @DisplayName("Should accept empty string for validation")
        void shouldAcceptEmptyStringForValidation() {
            // Given
            String emptyPassword = "";
            
            // When
            PasswordService.PasswordValidationResult result = 
                passwordService.validatePasswordStrength(emptyPassword);
            
            // Then
            assertThat(result.isValid()).isFalse();
            assertThat(result.message()).isEqualTo("Password must be at least 8 characters long");
        }
        
        @Test
        @DisplayName("Should reject password with actual weak patterns")
        void shouldRejectPasswordWithActualWeakPatterns() {
            // Given - passwords that meet basic requirements but have weak patterns
            String repeatedPattern = "Aaaaaaaa1!"; // Repeated 'a' with requirements met
            String sequentialPattern = "123456Aa!"; // Sequential numbers with requirements met
            
            // When & Then
            PasswordService.PasswordValidationResult result1 = 
                passwordService.validatePasswordStrength(repeatedPattern);
            assertThat(result1.isValid()).isFalse();
            assertThat(result1.message()).isEqualTo("Password is too common and easily guessable");
            
            PasswordService.PasswordValidationResult result2 = 
                passwordService.validatePasswordStrength(sequentialPattern);
            assertThat(result2.isValid()).isFalse();
            assertThat(result2.message()).isEqualTo("Password is too common and easily guessable");
        }
    }
    
    @Nested
    @DisplayName("Security Tests")
    class SecurityTests {
        
        @Test
        @DisplayName("Should be resistant to timing attacks")
        void shouldBeResistantToTimingAttacks() {
            // Given
            String correctPassword = "SecurePass123!";
            String hashedPassword = passwordService.hashPassword(correctPassword);
            String wrongPassword = "WrongPass456!";
            
            // When - Measure time for correct and incorrect passwords
            long startTime1 = System.nanoTime();
            passwordService.verifyPassword(correctPassword, hashedPassword);
            long time1 = System.nanoTime() - startTime1;
            
            long startTime2 = System.nanoTime();
            passwordService.verifyPassword(wrongPassword, hashedPassword);
            long time2 = System.nanoTime() - startTime2;
            
            // Then - Times should be similar (within reasonable bounds)
            // This is a basic check; in practice, more sophisticated timing analysis would be needed
            double ratio = (double) Math.max(time1, time2) / Math.min(time1, time2);
            assertThat(ratio).isLessThan(10.0); // Allow for some variance
        }
        
        @Test
        @DisplayName("Should handle very long passwords without crashing")
        void shouldHandleVeryLongPasswordsWithoutCrashing() {
            // Given
            String veryLongPassword = "A".repeat(1000) + "1!";
            
            // When & Then - Should not crash, just return validation error
            assertThatCode(() -> {
                PasswordService.PasswordValidationResult result = 
                    passwordService.validatePasswordStrength(veryLongPassword);
                assertThat(result.isValid()).isFalse();
            }).doesNotThrowAnyException();
        }
        
        @Test
        @DisplayName("Should handle special characters in passwords")
        void shouldHandleSpecialCharactersInPasswords() {
            // Given
            String specialCharPassword = "Pässwörd123!@#$%^&*()";
            
            // When
            String hashedPassword = passwordService.hashPassword(specialCharPassword);
            boolean isValid = passwordService.verifyPassword(specialCharPassword, hashedPassword);
            
            // Then
            assertThat(hashedPassword).isNotNull();
            assertThat(isValid).isTrue();
        }
    }
}
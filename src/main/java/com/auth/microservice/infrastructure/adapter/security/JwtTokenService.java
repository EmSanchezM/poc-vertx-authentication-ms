package com.auth.microservice.infrastructure.adapter.security;

import com.auth.microservice.domain.service.JWTService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import io.vertx.core.json.JsonObject;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * JWT service implementation using the jjwt library.
 * Uses configuration from application properties for security settings.
 */
public class JwtTokenService implements JWTService {
    
    private static final String PERMISSIONS_CLAIM = "permissions";
    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";
    
    private final String issuer;
    private final int accessTokenExpirySeconds;
    private final int refreshTokenExpirySeconds;
    private final SecretKey signingKey;
    private final JwtParser jwtParser;
    
    /**
     * Constructor that uses configuration from application properties.
     * 
     * @param config JsonObject containing JWT configuration
     */
    public JwtTokenService(JsonObject config) {
        this.issuer = config.getString("jwt.issuer", "auth-microservice");
        this.accessTokenExpirySeconds = config.getInteger("jwt.accessTokenExpiry", 900); // 15 minutes default
        this.refreshTokenExpirySeconds = config.getInteger("jwt.refreshTokenExpiry", 604800); // 7 days default
        
        String secret = config.getString("jwt.secret");
        if (secret == null || secret.trim().isEmpty() || "default-jwt-secret-change-in-production".equals(secret)) {
            throw new IllegalArgumentException("JWT secret must be configured and not use default value in production");
        }
        
        // Create signing key from the configured secret
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.jwtParser = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build();
    }
    
    /**
     * Constructor for testing with custom signing key.
     */
    JwtTokenService(SecretKey signingKey, String issuer, int accessTokenExpirySeconds, int refreshTokenExpirySeconds) {
        this.signingKey = signingKey;
        this.issuer = issuer;
        this.accessTokenExpirySeconds = accessTokenExpirySeconds;
        this.refreshTokenExpirySeconds = refreshTokenExpirySeconds;
        this.jwtParser = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build();
    }
    
    @Override
    public String generateAccessToken(String userId, String email, Set<String> permissions) {
        validateTokenParameters(userId, email);
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiration = now.plusSeconds(accessTokenExpirySeconds);
        
        return Jwts.builder()
                .setIssuer(issuer)
                .setSubject(userId)
                .setAudience("auth-service-clients")
                .setIssuedAt(Date.from(now.atZone(ZoneId.systemDefault()).toInstant()))
                .setExpiration(Date.from(expiration.atZone(ZoneId.systemDefault()).toInstant()))
                .claim("email", email)
                .claim(PERMISSIONS_CLAIM, new ArrayList<>(permissions))
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .setId(UUID.randomUUID().toString())
                .signWith(signingKey)
                .compact();
    }
    
    @Override
    public String generateRefreshToken(String userId, String email) {
        validateTokenParameters(userId, email);
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiration = now.plusSeconds(refreshTokenExpirySeconds);
        
        return Jwts.builder()
                .setIssuer(issuer)
                .setSubject(userId)
                .setAudience("auth-service-clients")
                .setIssuedAt(Date.from(now.atZone(ZoneId.systemDefault()).toInstant()))
                .setExpiration(Date.from(expiration.atZone(ZoneId.systemDefault()).toInstant()))
                .claim("email", email)
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
                .setId(UUID.randomUUID().toString())
                .signWith(signingKey)
                .compact();
    }
    
    @Override
    public TokenValidationResult validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Token cannot be null or empty");
        }
        
        try {
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();
            
            // Convert claims to map for the result
            Map<String, Object> claimsMap = new HashMap<>(claims);
            
            return new TokenValidationResult(true, "Token is valid", claimsMap);
            
        } catch (ExpiredJwtException e) {
            return new TokenValidationResult(false, "Token has expired", Collections.emptyMap());
        } catch (UnsupportedJwtException e) {
            return new TokenValidationResult(false, "Token format is not supported", Collections.emptyMap());
        } catch (MalformedJwtException e) {
            return new TokenValidationResult(false, "Token is malformed", Collections.emptyMap());
        } catch (SignatureException e) {
            return new TokenValidationResult(false, "Token signature is invalid", Collections.emptyMap());
        } catch (IllegalArgumentException e) {
            return new TokenValidationResult(false, "Token is invalid", Collections.emptyMap());
        } catch (Exception e) {
            return new TokenValidationResult(false, "Token validation failed", Collections.emptyMap());
        }
    }
    
    @Override
    public Optional<String> extractUserId(String token) {
        try {
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();
            return Optional.ofNullable(claims.getSubject());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    
    @Override
    public Optional<String> extractUserEmail(String token) {
        try {
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();
            return Optional.ofNullable(claims.get("email", String.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public Set<String> extractPermissions(String token) {
        try {
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();
            List<String> permissions = claims.get(PERMISSIONS_CLAIM, List.class);
            return permissions != null ? new HashSet<>(permissions) : Collections.emptySet();
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }
    
    @Override
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();
            Date expiration = claims.getExpiration();
            return expiration.before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            return true; // Consider invalid tokens as expired
        }
    }
    
    @Override
    public Optional<LocalDateTime> getTokenExpiration(String token) {
        try {
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();
            Date expiration = claims.getExpiration();
            return Optional.of(LocalDateTime.ofInstant(expiration.toInstant(), ZoneId.systemDefault()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    
    @Override
    public TokenPair generateTokenPair(String userId, String email, Set<String> permissions) {
        validateTokenParameters(userId, email);
        
        String accessToken = generateAccessToken(userId, email, permissions);
        String refreshToken = generateRefreshToken(userId, email);
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime accessExpiration = now.plusSeconds(accessTokenExpirySeconds);
        LocalDateTime refreshExpiration = now.plusSeconds(refreshTokenExpirySeconds);
        
        return new TokenPair(accessToken, refreshToken, accessExpiration, refreshExpiration);
    }
    
    /**
     * Validates the basic parameters required for token generation.
     */
    private void validateTokenParameters(String userId, String email) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
    }
    
    /**
     * Extracts the token type from a JWT token.
     * 
     * @param token the JWT token
     * @return the token type (access or refresh)
     */
    public Optional<String> extractTokenType(String token) {
        try {
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();
            return Optional.ofNullable(claims.get(TOKEN_TYPE_CLAIM, String.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    
    /**
     * Checks if the token is an access token.
     */
    public boolean isAccessToken(String token) {
        return extractTokenType(token)
                .map(ACCESS_TOKEN_TYPE::equals)
                .orElse(false);
    }
    
    /**
     * Checks if the token is a refresh token.
     */
    public boolean isRefreshToken(String token) {
        return extractTokenType(token)
                .map(REFRESH_TOKEN_TYPE::equals)
                .orElse(false);
    }
}
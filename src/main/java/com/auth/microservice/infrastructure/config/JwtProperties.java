package com.auth.microservice.infrastructure.config;

/**
 * Propiedades de configuración para JWT
 */
public class JwtProperties {
    
    private String secret;
    private String issuer;
    private long accessTokenExpiry;
    private long refreshTokenExpiry;
    
    public JwtProperties() {}
    
    public JwtProperties(ConfigService configService) {
        this.secret = configService.getString("jwt.secret");
        this.issuer = configService.getString("jwt.issuer");
        this.accessTokenExpiry = configService.getLong("jwt.accessTokenExpiry");
        this.refreshTokenExpiry = configService.getLong("jwt.refreshTokenExpiry");
    }
    
    // Getters y setters
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    
    public long getAccessTokenExpiry() { return accessTokenExpiry; }
    public void setAccessTokenExpiry(long accessTokenExpiry) { this.accessTokenExpiry = accessTokenExpiry; }
    
    public long getRefreshTokenExpiry() { return refreshTokenExpiry; }
    public void setRefreshTokenExpiry(long refreshTokenExpiry) { this.refreshTokenExpiry = refreshTokenExpiry; }
    
    /**
     * Obtiene la expiración del access token en milisegundos
     */
    public long getAccessTokenExpiryMillis() {
        return accessTokenExpiry * 1000;
    }
    
    /**
     * Obtiene la expiración del refresh token en milisegundos
     */
    public long getRefreshTokenExpiryMillis() {
        return refreshTokenExpiry * 1000;
    }
    
    @Override
    public String toString() {
        return String.format("JwtProperties{issuer='%s', accessTokenExpiry=%d, refreshTokenExpiry=%d}", 
                           issuer, accessTokenExpiry, refreshTokenExpiry);
    }
}
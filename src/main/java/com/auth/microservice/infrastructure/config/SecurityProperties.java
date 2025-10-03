package com.auth.microservice.infrastructure.config;

import java.util.Arrays;
import java.util.List;

/**
 * Propiedades de configuración para seguridad
 */
public class SecurityProperties {
    
    private int bcryptRounds;
    private RateLimitProperties rateLimit;
    private CorsProperties cors;
    
    public SecurityProperties() {}
    
    public SecurityProperties(ConfigService configService) {
        this.bcryptRounds = configService.getInt("security.bcrypt.rounds");
        this.rateLimit = new RateLimitProperties(configService);
        this.cors = new CorsProperties(configService);
    }
    
    // Getters y setters
    public int getBcryptRounds() { return bcryptRounds; }
    public void setBcryptRounds(int bcryptRounds) { this.bcryptRounds = bcryptRounds; }
    
    public RateLimitProperties getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimitProperties rateLimit) { this.rateLimit = rateLimit; }
    
    public CorsProperties getCors() { return cors; }
    public void setCors(CorsProperties cors) { this.cors = cors; }
    
    /**
     * Propiedades de Rate Limiting
     */
    public static class RateLimitProperties {
        private boolean enabled;
        private int loginAttempts;
        private int loginWindowMinutes;
        private int loginBlockMinutes;
        private int apiRequestsPerMinute;
        
        public RateLimitProperties() {}
        
        public RateLimitProperties(ConfigService configService) {
            this.enabled = configService.getBoolean("security.rateLimit.enabled");
            this.loginAttempts = configService.getInt("security.rateLimit.login.attempts");
            this.loginWindowMinutes = configService.getInt("security.rateLimit.login.windowMinutes");
            this.loginBlockMinutes = configService.getInt("security.rateLimit.login.blockMinutes");
            this.apiRequestsPerMinute = configService.getInt("security.rateLimit.api.requestsPerMinute");
        }
        
        // Getters y setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getLoginAttempts() { return loginAttempts; }
        public void setLoginAttempts(int loginAttempts) { this.loginAttempts = loginAttempts; }
        
        public int getLoginWindowMinutes() { return loginWindowMinutes; }
        public void setLoginWindowMinutes(int loginWindowMinutes) { this.loginWindowMinutes = loginWindowMinutes; }
        
        public int getLoginBlockMinutes() { return loginBlockMinutes; }
        public void setLoginBlockMinutes(int loginBlockMinutes) { this.loginBlockMinutes = loginBlockMinutes; }
        
        public int getApiRequestsPerMinute() { return apiRequestsPerMinute; }
        public void setApiRequestsPerMinute(int apiRequestsPerMinute) { this.apiRequestsPerMinute = apiRequestsPerMinute; }
        
        /**
         * Obtiene la ventana de tiempo en milisegundos
         */
        public long getLoginWindowMillis() {
            return loginWindowMinutes * 60L * 1000L;
        }
        
        /**
         * Obtiene el tiempo de bloqueo en milisegundos
         */
        public long getLoginBlockMillis() {
            return loginBlockMinutes * 60L * 1000L;
        }
        
        @Override
        public String toString() {
            return String.format("RateLimitProperties{enabled=%s, loginAttempts=%d, windowMinutes=%d, blockMinutes=%d, apiRequestsPerMinute=%d}", 
                               enabled, loginAttempts, loginWindowMinutes, loginBlockMinutes, apiRequestsPerMinute);
        }
    }
    
    /**
     * Propiedades de CORS
     */
    public static class CorsProperties {
        private boolean enabled;
        private List<String> allowedOrigins;
        private List<String> allowedMethods;
        private List<String> allowedHeaders;
        private int maxAge;
        
        public CorsProperties() {}
        
        public CorsProperties(ConfigService configService) {
            this.enabled = configService.getBoolean("security.cors.enabled");
            this.allowedOrigins = parseList(configService.getString("security.cors.allowedOrigins"));
            this.allowedMethods = parseList(configService.getString("security.cors.allowedMethods"));
            this.allowedHeaders = parseList(configService.getString("security.cors.allowedHeaders"));
            this.maxAge = configService.getInt("security.cors.maxAge");
        }
        
        private List<String> parseList(String value) {
            if (value == null || value.trim().isEmpty()) {
                return List.of();
            }
            return Arrays.asList(value.split(","));
        }
        
        // Getters y setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public List<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
        
        public List<String> getAllowedMethods() { return allowedMethods; }
        public void setAllowedMethods(List<String> allowedMethods) { this.allowedMethods = allowedMethods; }
        
        public List<String> getAllowedHeaders() { return allowedHeaders; }
        public void setAllowedHeaders(List<String> allowedHeaders) { this.allowedHeaders = allowedHeaders; }
        
        public int getMaxAge() { return maxAge; }
        public void setMaxAge(int maxAge) { this.maxAge = maxAge; }
        
        /**
         * Verifica si un origen está permitido
         */
        public boolean isOriginAllowed(String origin) {
            if (!enabled || allowedOrigins.isEmpty()) {
                return false;
            }
            return allowedOrigins.contains("*") || allowedOrigins.contains(origin);
        }
        
        @Override
        public String toString() {
            return String.format("CorsProperties{enabled=%s, allowedOrigins=%s, allowedMethods=%s, maxAge=%d}", 
                               enabled, allowedOrigins, allowedMethods, maxAge);
        }
    }
    
    @Override
    public String toString() {
        return String.format("SecurityProperties{bcryptRounds=%d, rateLimit=%s, cors=%s}", 
                           bcryptRounds, rateLimit, cors);
    }
}
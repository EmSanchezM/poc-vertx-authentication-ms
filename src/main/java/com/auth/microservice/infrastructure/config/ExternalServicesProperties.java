package com.auth.microservice.infrastructure.config;

/**
 * Propiedades de configuración para servicios externos
 */
public class ExternalServicesProperties {
    
    private EmailProperties email;
    private GeoLocationProperties geoLocation;
    
    public ExternalServicesProperties() {}
    
    public ExternalServicesProperties(ConfigService configService) {
        this.email = new EmailProperties(configService);
        this.geoLocation = new GeoLocationProperties(configService);
    }
    
    // Getters y setters
    public EmailProperties getEmail() { return email; }
    public void setEmail(EmailProperties email) { this.email = email; }
    
    public GeoLocationProperties getGeoLocation() { return geoLocation; }
    public void setGeoLocation(GeoLocationProperties geoLocation) { this.geoLocation = geoLocation; }
    
    /**
     * Propiedades del servicio de email
     */
    public static class EmailProperties {
        private boolean enabled;
        private SmtpProperties smtp;
        private String fromAddress;
        private String fromName;
        
        public EmailProperties() {}
        
        public EmailProperties(ConfigService configService) {
            this.enabled = configService.getBoolean("email.enabled");
            this.smtp = new SmtpProperties(configService);
            this.fromAddress = configService.getString("email.from.address");
            this.fromName = configService.getString("email.from.name");
        }
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public SmtpProperties getSmtp() { return smtp; }
        public void setSmtp(SmtpProperties smtp) { this.smtp = smtp; }
        
        public String getFromAddress() { return fromAddress; }
        public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }
        
        public String getFromName() { return fromName; }
        public void setFromName(String fromName) { this.fromName = fromName; }
        
        /**
         * Propiedades SMTP
         */
        public static class SmtpProperties {
            private String host;
            private int port;
            private String username;
            private String password;
            
            public SmtpProperties() {}
            
            public SmtpProperties(ConfigService configService) {
                this.host = configService.getString("email.smtp.host");
                this.port = configService.getInt("email.smtp.port");
                this.username = configService.getString("email.smtp.username", "");
                this.password = configService.getString("email.smtp.password", "");
            }
            
            public String getHost() { return host; }
            public void setHost(String host) { this.host = host; }
            
            public int getPort() { return port; }
            public void setPort(int port) { this.port = port; }
            
            public String getUsername() { return username; }
            public void setUsername(String username) { this.username = username; }
            
            public String getPassword() { return password; }
            public void setPassword(String password) { this.password = password; }
            
            public boolean hasAuthentication() {
                return username != null && !username.trim().isEmpty() &&
                       password != null && !password.trim().isEmpty();
            }
            
            @Override
            public String toString() {
                return String.format("SmtpProperties{host='%s', port=%d, hasAuth=%s}", 
                                   host, port, hasAuthentication());
            }
        }
        
        @Override
        public String toString() {
            return String.format("EmailProperties{enabled=%s, smtp=%s, fromAddress='%s'}", 
                               enabled, smtp, fromAddress);
        }
    }
    
    /**
     * Propiedades del servicio de geolocalización
     */
    public static class GeoLocationProperties {
        private boolean enabled;
        private String apiKey;
        private int timeout;
        private String baseUrl;
        
        public GeoLocationProperties() {}
        
        public GeoLocationProperties(ConfigService configService) {
            this.enabled = configService.getBoolean("geolocation.enabled");
            this.apiKey = configService.getString("geolocation.apiKey", "");
            this.timeout = configService.getInt("geolocation.timeout");
            this.baseUrl = configService.getString("geolocation.baseUrl", "http://ip-api.com/json/");
        }
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        
        public int getTimeout() { return timeout; }
        public void setTimeout(int timeout) { this.timeout = timeout; }
        
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        
        public boolean hasApiKey() {
            return apiKey != null && !apiKey.trim().isEmpty();
        }
        
        @Override
        public String toString() {
            return String.format("GeoLocationProperties{enabled=%s, hasApiKey=%s, timeout=%d, baseUrl='%s'}", 
                               enabled, hasApiKey(), timeout, baseUrl);
        }
    }
    
    @Override
    public String toString() {
        return String.format("ExternalServicesProperties{email=%s, geoLocation=%s}", 
                           email, geoLocation);
    }
}
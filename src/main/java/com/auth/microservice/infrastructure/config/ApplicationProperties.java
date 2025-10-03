package com.auth.microservice.infrastructure.config;

/**
 * Propiedades principales de la aplicación
 */
public class ApplicationProperties {
    
    private String environment;
    private String name;
    private String version;
    private int serverPort;
    private DevelopmentProperties development;
    
    public ApplicationProperties() {}
    
    public ApplicationProperties(ConfigService configService) {
        this.environment = configService.getEnvironment();
        this.name = configService.getString("app.name");
        this.version = configService.getString("app.version");
        this.serverPort = configService.getInt("server.port");
        this.development = new DevelopmentProperties(configService);
    }
    
    // Getters y setters
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    
    public int getServerPort() { return serverPort; }
    public void setServerPort(int serverPort) { this.serverPort = serverPort; }
    
    public DevelopmentProperties getDevelopment() { return development; }
    public void setDevelopment(DevelopmentProperties development) { this.development = development; }
    
    public boolean isDevelopment() {
        return "development".equals(environment);
    }
    
    public boolean isProduction() {
        return "production".equals(environment);
    }
    
    public boolean isQa() {
        return "qa".equals(environment);
    }
    
    /**
     * Propiedades específicas de desarrollo
     */
    public static class DevelopmentProperties {
        private boolean devMode;
        private boolean autoReload;
        private boolean mockExternalServices;
        
        public DevelopmentProperties() {}
        
        public DevelopmentProperties(ConfigService configService) {
            this.devMode = configService.getBoolean("dev.mode", false);
            this.autoReload = configService.getBoolean("dev.autoReload", false);
            this.mockExternalServices = configService.getBoolean("dev.mockExternalServices", false);
        }
        
        public boolean isDevMode() { return devMode; }
        public void setDevMode(boolean devMode) { this.devMode = devMode; }
        
        public boolean isAutoReload() { return autoReload; }
        public void setAutoReload(boolean autoReload) { this.autoReload = autoReload; }
        
        public boolean isMockExternalServices() { return mockExternalServices; }
        public void setMockExternalServices(boolean mockExternalServices) { this.mockExternalServices = mockExternalServices; }
        
        @Override
        public String toString() {
            return String.format("DevelopmentProperties{devMode=%s, autoReload=%s, mockExternalServices=%s}", 
                               devMode, autoReload, mockExternalServices);
        }
    }
    
    @Override
    public String toString() {
        return String.format("ApplicationProperties{environment='%s', name='%s', version='%s', serverPort=%d, development=%s}", 
                           environment, name, version, serverPort, development);
    }
}
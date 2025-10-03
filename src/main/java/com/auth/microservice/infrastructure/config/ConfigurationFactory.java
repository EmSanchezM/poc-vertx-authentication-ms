package com.auth.microservice.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory para crear y gestionar todas las configuraciones de la aplicación
 */
public class ConfigurationFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationFactory.class);
    
    private final ConfigService configService;
    private final ApplicationProperties applicationProperties;
    private final DatabaseProperties databaseProperties;
    private final RedisProperties redisProperties;
    private final JwtProperties jwtProperties;
    private final SecurityProperties securityProperties;
    private final MonitoringProperties monitoringProperties;
    private final ExternalServicesProperties externalServicesProperties;
    
    public ConfigurationFactory() {
        logger.info("Inicializando ConfigurationFactory");
        
        // Inicializar ConfigService primero
        this.configService = new ConfigService();
        
        // Crear todas las propiedades usando ConfigService
        this.applicationProperties = new ApplicationProperties(configService);
        this.databaseProperties = DatabaseProperties.fromConfigService(configService);
        this.redisProperties = new RedisProperties(configService);
        this.jwtProperties = new JwtProperties(configService);
        this.securityProperties = new SecurityProperties(configService);
        this.monitoringProperties = new MonitoringProperties(configService);
        this.externalServicesProperties = new ExternalServicesProperties(configService);
        
        logConfigurationSummary();
        logger.info("ConfigurationFactory inicializado exitosamente");
    }
    
    /**
     * Registra un resumen de la configuración cargada
     */
    private void logConfigurationSummary() {
        logger.info("=== RESUMEN DE CONFIGURACIÓN ===");
        logger.info("Ambiente: {}", applicationProperties.getEnvironment());
        logger.info("Aplicación: {} v{}", applicationProperties.getName(), applicationProperties.getVersion());
        logger.info("Puerto del servidor: {}", applicationProperties.getServerPort());
        logger.info("Base de datos: {}", databaseProperties);
        logger.info("Redis: {}", redisProperties);
        logger.info("JWT: {}", jwtProperties);
        logger.info("Seguridad: {}", securityProperties);
        logger.info("Monitoreo: {}", monitoringProperties);
        logger.info("Servicios externos: {}", externalServicesProperties);
        logger.info("================================");
    }
    
    // Getters para todas las configuraciones
    
    public ConfigService getConfigService() {
        return configService;
    }
    
    public ApplicationProperties getApplicationProperties() {
        return applicationProperties;
    }
    
    public DatabaseProperties getDatabaseProperties() {
        return databaseProperties;
    }
    
    public RedisProperties getRedisProperties() {
        return redisProperties;
    }
    
    public JwtProperties getJwtProperties() {
        return jwtProperties;
    }
    
    public SecurityProperties getSecurityProperties() {
        return securityProperties;
    }
    
    public MonitoringProperties getMonitoringProperties() {
        return monitoringProperties;
    }
    
    public ExternalServicesProperties getExternalServicesProperties() {
        return externalServicesProperties;
    }
    
    /**
     * Valida que todas las configuraciones críticas estén presentes
     */
    public void validateCriticalConfiguration() {
        logger.info("Validando configuración crítica");
        
        // Validaciones adicionales específicas por ambiente
        if (applicationProperties.isProduction()) {
            validateProductionConfiguration();
        } else if (applicationProperties.isDevelopment()) {
            validateDevelopmentConfiguration();
        }
        
        logger.info("Validación de configuración crítica completada");
    }
    
    /**
     * Validaciones específicas para producción
     */
    private void validateProductionConfiguration() {
        logger.info("Aplicando validaciones de producción");
        
        // JWT secret debe ser seguro en producción
        String jwtSecret = jwtProperties.getSecret();
        if (jwtSecret.contains("default") || jwtSecret.contains("dev")) {
            throw new ConfigService.ConfigurationException(
                "JWT secret no debe contener valores por defecto en producción");
        }
        
        // Rate limiting debe estar habilitado
        if (!securityProperties.getRateLimit().isEnabled()) {
            logger.warn("Rate limiting está deshabilitado en producción");
        }
        
        // CORS no debe permitir todos los orígenes
        if (securityProperties.getCors().getAllowedOrigins().contains("*")) {
            logger.warn("CORS permite todos los orígenes en producción");
        }
        
        // Logging debe estar en formato JSON
        if (!monitoringProperties.getLogging().isJsonFormat()) {
            logger.warn("Se recomienda formato JSON para logs en producción");
        }
    }
    
    /**
     * Validaciones específicas para desarrollo
     */
    private void validateDevelopmentConfiguration() {
        logger.info("Aplicando validaciones de desarrollo");
        
        // En desarrollo, advertir sobre configuraciones de producción
        if (securityProperties.getBcryptRounds() > 10) {
            logger.info("BCrypt rounds alto ({}) puede ser lento en desarrollo", 
                       securityProperties.getBcryptRounds());
        }
    }
    
    /**
     * Obtiene información de configuración para endpoints de diagnóstico
     */
    public ConfigurationInfo getConfigurationInfo() {
        return new ConfigurationInfo(
            applicationProperties.getEnvironment(),
            applicationProperties.getName(),
            applicationProperties.getVersion(),
            applicationProperties.getServerPort(),
            configService.getConfigInfo()
        );
    }
    
    /**
     * Clase para información de configuración
     */
    public static class ConfigurationInfo {
        private final String environment;
        private final String applicationName;
        private final String applicationVersion;
        private final int serverPort;
        private final Object configDetails;
        
        public ConfigurationInfo(String environment, String applicationName, 
                               String applicationVersion, int serverPort, Object configDetails) {
            this.environment = environment;
            this.applicationName = applicationName;
            this.applicationVersion = applicationVersion;
            this.serverPort = serverPort;
            this.configDetails = configDetails;
        }
        
        public String getEnvironment() { return environment; }
        public String getApplicationName() { return applicationName; }
        public String getApplicationVersion() { return applicationVersion; }
        public int getServerPort() { return serverPort; }
        public Object getConfigDetails() { return configDetails; }
    }
}
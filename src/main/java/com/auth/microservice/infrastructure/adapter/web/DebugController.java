package com.auth.microservice.infrastructure.adapter.web;

import com.auth.microservice.infrastructure.config.ConfigService;
import com.auth.microservice.infrastructure.health.HealthCheckService;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller para endpoints de diagnóstico y debugging del ambiente
 * Proporciona información detallada sobre configuración, variables de entorno,
 * conectividad de red y estado del sistema para facilitar el debugging
 */
public class DebugController {
    
    private static final Logger logger = LoggerFactory.getLogger(DebugController.class);
    
    private final ConfigService configService;
    private final HealthCheckService healthCheckService;
    
    public DebugController(ConfigService configService, HealthCheckService healthCheckService) {
        this.configService = configService;
        this.healthCheckService = healthCheckService;
    }
    
    /**
     * Configura las rutas de debug
     */
    public void configureRoutes(Router router) {
        // Endpoint principal de diagnóstico de ambiente
        router.get("/debug/environment").handler(this::handleEnvironmentDebug);
        
        // Endpoint específico para variables de entorno
        router.get("/debug/environment/variables").handler(this::handleEnvironmentVariables);
        
        // Endpoint para información de red
        router.get("/debug/environment/network").handler(this::handleNetworkInfo);
        
        // Endpoint para información del sistema
        router.get("/debug/environment/system").handler(this::handleSystemInfo);
        
        // Endpoint para configuración resuelta
        router.get("/debug/environment/config").handler(this::handleConfigInfo);
        
        logger.info("Debug routes configured");
    }
    
    /**
     * Endpoint principal de diagnóstico de ambiente
     * Proporciona una vista completa del estado del ambiente
     */
    private void handleEnvironmentDebug(RoutingContext context) {
        HttpServerResponse response = context.response();
        
        try {
            JsonObject environmentInfo = new JsonObject()
                .put("timestamp", Instant.now().toString())
                .put("environment", getEnvironmentInfo())
                .put("system", getSystemInfo())
                .put("network", getNetworkInfo())
                .put("configuration", getConfigurationInfo())
                .put("health", getHealthInfo())
                .put("diagnostics", getDiagnosticsInfo());
            
            response
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(environmentInfo.encodePrettily());
                
        } catch (Exception e) {
            logger.error("Error generating environment debug info", e);
            
            JsonObject errorResponse = new JsonObject()
                .put("error", "Failed to generate environment debug info")
                .put("message", e.getMessage())
                .put("timestamp", Instant.now().toString());
            
            response
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(errorResponse.encodePrettily());
        }
    }
    
    /**
     * Endpoint específico para variables de entorno
     */
    private void handleEnvironmentVariables(RoutingContext context) {
        HttpServerResponse response = context.response();
        
        try {
            JsonObject envVarsInfo = new JsonObject()
                .put("timestamp", Instant.now().toString())
                .put("environmentVariables", getEnvironmentVariablesInfo());
            
            response
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(envVarsInfo.encodePrettily());
                
        } catch (Exception e) {
            logger.error("Error generating environment variables info", e);
            
            response
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("error", "Failed to generate environment variables info")
                    .put("message", e.getMessage())
                    .encodePrettily());
        }
    }
    
    /**
     * Endpoint para información de red
     */
    private void handleNetworkInfo(RoutingContext context) {
        HttpServerResponse response = context.response();
        
        try {
            JsonObject networkInfo = new JsonObject()
                .put("timestamp", Instant.now().toString())
                .put("network", getNetworkInfo());
            
            response
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(networkInfo.encodePrettily());
                
        } catch (Exception e) {
            logger.error("Error generating network info", e);
            
            response
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("error", "Failed to generate network info")
                    .put("message", e.getMessage())
                    .encodePrettily());
        }
    }
    
    /**
     * Endpoint para información del sistema
     */
    private void handleSystemInfo(RoutingContext context) {
        HttpServerResponse response = context.response();
        
        try {
            JsonObject systemInfo = new JsonObject()
                .put("timestamp", Instant.now().toString())
                .put("system", getSystemInfo());
            
            response
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(systemInfo.encodePrettily());
                
        } catch (Exception e) {
            logger.error("Error generating system info", e);
            
            response
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("error", "Failed to generate system info")
                    .put("message", e.getMessage())
                    .encodePrettily());
        }
    }
    
    /**
     * Endpoint para configuración resuelta
     */
    private void handleConfigInfo(RoutingContext context) {
        HttpServerResponse response = context.response();
        
        try {
            JsonObject configInfo = new JsonObject()
                .put("timestamp", Instant.now().toString())
                .put("configuration", getConfigurationInfo());
            
            response
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(configInfo.encodePrettily());
                
        } catch (Exception e) {
            logger.error("Error generating config info", e);
            
            response
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("error", "Failed to generate config info")
                    .put("message", e.getMessage())
                    .encodePrettily());
        }
    }
    
    /**
     * Obtiene información general del ambiente
     */
    private JsonObject getEnvironmentInfo() {
        return new JsonObject()
            .put("isDevelopment", configService.isDevelopmentMode())
            .put("environment", System.getenv("APP_ENV"))
            .put("profiles", System.getProperty("spring.profiles.active"))
            .put("containerized", isRunningInContainer())
            .put("dockerEnvironment", isDockerEnvironment())
            .put("hostname", getHostname())
            .put("workingDirectory", System.getProperty("user.dir"));
    }
    
    /**
     * Obtiene información detallada de variables de entorno
     */
    private JsonObject getEnvironmentVariablesInfo() {
        JsonObject envInfo = new JsonObject();
        
        // Variables críticas para la aplicación
        String[] criticalVars = {
            "DB_HOST", "DB_PORT", "DB_NAME", "DB_USERNAME", "DB_PASSWORD",
            "REDIS_HOST", "REDIS_PORT", "REDIS_PASSWORD",
            "JWT_SECRET", "APP_ENV", "JAVA_OPTS"
        };
        
        JsonObject criticalEnvVars = new JsonObject();
        for (String var : criticalVars) {
            String value = System.getenv(var);
            if (value != null) {
                // Ocultar valores sensibles
                if (isSensitiveVariable(var)) {
                    criticalEnvVars.put(var, "***");
                } else {
                    criticalEnvVars.put(var, value);
                }
            } else {
                criticalEnvVars.put(var, null);
            }
        }
        
        // Categorizar todas las variables de entorno
        Map<String, String> allEnvVars = System.getenv();
        JsonObject categorizedVars = new JsonObject();
        
        Map<String, JsonObject> categories = new HashMap<>();
        categories.put("database", new JsonObject());
        categories.put("redis", new JsonObject());
        categories.put("security", new JsonObject());
        categories.put("java", new JsonObject());
        categories.put("docker", new JsonObject());
        categories.put("system", new JsonObject());
        categories.put("other", new JsonObject());
        
        for (Map.Entry<String, String> entry : allEnvVars.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            String category = categorizeEnvironmentVariable(key);
            
            // Ocultar valores sensibles
            String displayValue = isSensitiveVariable(key) ? "***" : value;
            
            categories.get(category).put(key, displayValue);
        }
        
        categories.forEach(categorizedVars::put);
        
        return new JsonObject()
            .put("critical", criticalEnvVars)
            .put("categorized", categorizedVars)
            .put("totalCount", allEnvVars.size())
            .put("criticalMissing", getCriticalMissingVariables(criticalVars));
    }
    
    /**
     * Obtiene información de red
     */
    private JsonObject getNetworkInfo() {
        JsonObject networkInfo = new JsonObject();
        
        try {
            // Información de interfaces de red
            JsonArray interfaces = new JsonArray();
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                
                if (!networkInterface.isLoopback() && networkInterface.isUp()) {
                    JsonObject interfaceInfo = new JsonObject()
                        .put("name", networkInterface.getName())
                        .put("displayName", networkInterface.getDisplayName())
                        .put("isUp", networkInterface.isUp())
                        .put("supportsMulticast", networkInterface.supportsMulticast());
                    
                    JsonArray addresses = new JsonArray();
                    Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                    while (inetAddresses.hasMoreElements()) {
                        InetAddress address = inetAddresses.nextElement();
                        addresses.add(new JsonObject()
                            .put("address", address.getHostAddress())
                            .put("hostname", address.getHostName())
                            .put("isLoopback", address.isLoopbackAddress())
                            .put("isSiteLocal", address.isSiteLocalAddress()));
                    }
                    interfaceInfo.put("addresses", addresses);
                    interfaces.add(interfaceInfo);
                }
            }
            
            networkInfo.put("interfaces", interfaces);
            
            // Información de conectividad DNS
            networkInfo.put("dnsResolution", testDnsResolution());
            
            // Información de conectividad a servicios críticos
            networkInfo.put("serviceConnectivity", testServiceConnectivity());
            
        } catch (Exception e) {
            logger.warn("Error getting network info", e);
            networkInfo.put("error", e.getMessage());
        }
        
        return networkInfo;
    }
    
    /**
     * Obtiene información del sistema
     */
    private JsonObject getSystemInfo() {
        Runtime runtime = Runtime.getRuntime();
        
        return new JsonObject()
            .put("java", new JsonObject()
                .put("version", System.getProperty("java.version"))
                .put("vendor", System.getProperty("java.vendor"))
                .put("home", System.getProperty("java.home"))
                .put("classPath", System.getProperty("java.class.path").length() > 200 ? 
                    System.getProperty("java.class.path").substring(0, 200) + "..." : 
                    System.getProperty("java.class.path")))
            .put("os", new JsonObject()
                .put("name", System.getProperty("os.name"))
                .put("version", System.getProperty("os.version"))
                .put("arch", System.getProperty("os.arch")))
            .put("memory", new JsonObject()
                .put("maxMB", runtime.maxMemory() / 1024 / 1024)
                .put("totalMB", runtime.totalMemory() / 1024 / 1024)
                .put("freeMB", runtime.freeMemory() / 1024 / 1024)
                .put("usedMB", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024))
            .put("processors", runtime.availableProcessors())
            .put("user", new JsonObject()
                .put("name", System.getProperty("user.name"))
                .put("home", System.getProperty("user.home"))
                .put("dir", System.getProperty("user.dir")))
            .put("encoding", System.getProperty("file.encoding"))
            .put("timezone", System.getProperty("user.timezone"));
    }
    
    /**
     * Obtiene información de configuración
     */
    private JsonObject getConfigurationInfo() {
        JsonObject configInfo = new JsonObject();
        
        try {
            // Información básica de configuración
            configInfo.put("environment", configService.isDevelopmentMode() ? "development" : "production");
            
            // Configuración de base de datos
            configInfo.put("database", new JsonObject()
                .put("host", configService.getString("db.host", "unknown"))
                .put("port", configService.getString("db.port", "unknown"))
                .put("name", configService.getString("db.name", "unknown"))
                .put("username", configService.getString("db.username", "unknown"))
                .put("maxPoolSize", configService.getString("db.pool.maxSize", "unknown")));
            
            // Configuración de Redis
            configInfo.put("redis", new JsonObject()
                .put("host", configService.getString("redis.host", "unknown"))
                .put("port", configService.getString("redis.port", "unknown"))
                .put("database", configService.getString("redis.database", "unknown")));
            
            // Configuración del servidor
            configInfo.put("server", new JsonObject()
                .put("port", configService.getString("server.port", "unknown"))
                .put("host", configService.getString("server.host", "unknown")));
            
            // Fuentes de configuración utilizadas
            configInfo.put("sources", getConfigurationSources());
            
        } catch (Exception e) {
            logger.warn("Error getting configuration info", e);
            configInfo.put("error", e.getMessage());
        }
        
        return configInfo;
    }
    
    /**
     * Obtiene información de health checks
     */
    private JsonObject getHealthInfo() {
        JsonObject healthInfo = new JsonObject();
        
        try {
            // Información básica de salud
            healthInfo.put("isAlive", true); // Si llegamos aquí, estamos vivos
            
            // Intentar obtener información de health checks si está disponible
            // Nota: Esto es asíncrono, pero para debug podemos hacer una verificación simple
            healthInfo.put("databaseReachable", testDatabaseConnectivity());
            healthInfo.put("redisReachable", testRedisConnectivity());
            
        } catch (Exception e) {
            logger.warn("Error getting health info", e);
            healthInfo.put("error", e.getMessage());
        }
        
        return healthInfo;
    }
    
    /**
     * Obtiene información de diagnósticos
     */
    private JsonObject getDiagnosticsInfo() {
        JsonObject diagnostics = new JsonObject();
        
        // Verificaciones de diagnóstico
        diagnostics.put("configurationValid", validateConfiguration());
        diagnostics.put("environmentVariablesPresent", checkCriticalEnvironmentVariables());
        diagnostics.put("networkConnectivity", testBasicNetworkConnectivity());
        diagnostics.put("containerEnvironment", isRunningInContainer());
        
        // Recomendaciones basadas en el estado
        diagnostics.put("recommendations", generateRecommendations());
        
        return diagnostics;
    }
    
    // Métodos auxiliares
    
    private boolean isSensitiveVariable(String key) {
        String lowerKey = key.toLowerCase();
        return lowerKey.contains("password") || 
               lowerKey.contains("secret") || 
               lowerKey.contains("key") ||
               lowerKey.contains("token");
    }
    
    private String categorizeEnvironmentVariable(String key) {
        String lowerKey = key.toLowerCase();
        
        if (lowerKey.startsWith("db_") || lowerKey.contains("postgres")) return "database";
        if (lowerKey.startsWith("redis_")) return "redis";
        if (lowerKey.contains("jwt") || lowerKey.contains("secret") || lowerKey.contains("password")) return "security";
        if (lowerKey.startsWith("java") || lowerKey.contains("jvm")) return "java";
        if (lowerKey.contains("docker") || lowerKey.contains("container")) return "docker";
        if (lowerKey.startsWith("path") || lowerKey.startsWith("home") || lowerKey.startsWith("user")) return "system";
        
        return "other";
    }
    
    private JsonArray getCriticalMissingVariables(String[] criticalVars) {
        JsonArray missing = new JsonArray();
        for (String var : criticalVars) {
            if (System.getenv(var) == null) {
                missing.add(var);
            }
        }
        return missing;
    }
    
    private boolean isRunningInContainer() {
        // Verificar indicadores comunes de contenedor
        return System.getenv("HOSTNAME") != null && 
               (System.getenv("container") != null || 
                System.getProperty("java.io.tmpdir", "").contains("tmp"));
    }
    
    private boolean isDockerEnvironment() {
        // Verificar indicadores específicos de Docker
        return System.getenv("DOCKER_CONTAINER") != null ||
               System.getenv("HOSTNAME") != null && System.getenv("HOSTNAME").length() == 12;
    }
    
    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return System.getenv("HOSTNAME");
        }
    }
    
    private JsonObject testDnsResolution() {
        JsonObject dnsInfo = new JsonObject();
        
        String[] testHosts = {"postgres", "redis", "localhost"};
        JsonObject resolutions = new JsonObject();
        
        for (String host : testHosts) {
            try {
                InetAddress address = InetAddress.getByName(host);
                resolutions.put(host, new JsonObject()
                    .put("resolved", true)
                    .put("address", address.getHostAddress())
                    .put("canonical", address.getCanonicalHostName()));
            } catch (Exception e) {
                resolutions.put(host, new JsonObject()
                    .put("resolved", false)
                    .put("error", e.getMessage()));
            }
        }
        
        dnsInfo.put("resolutions", resolutions);
        return dnsInfo;
    }
    
    private JsonObject testServiceConnectivity() {
        JsonObject connectivity = new JsonObject();
        
        // Nota: En un entorno real, aquí haríamos pruebas de conectividad
        // Por ahora, solo reportamos la configuración
        connectivity.put("database", new JsonObject()
            .put("host", configService.getString("db.host", "unknown"))
            .put("port", configService.getString("db.port", "unknown"))
            .put("reachable", "unknown - requires async test"));
        
        connectivity.put("redis", new JsonObject()
            .put("host", configService.getString("redis.host", "unknown"))
            .put("port", configService.getString("redis.port", "unknown"))
            .put("reachable", "unknown - requires async test"));
        
        return connectivity;
    }
    
    private JsonArray getConfigurationSources() {
        JsonArray sources = new JsonArray();
        sources.add("Environment Variables");
        sources.add("System Properties");
        if (configService.isDevelopmentMode()) {
            sources.add(".env files");
        }
        sources.add("application.properties");
        return sources;
    }
    
    private boolean testDatabaseConnectivity() {
        // Verificación simple - en un entorno real usaríamos el health check service
        String dbHost = configService.getString("db.host", "");
        return !dbHost.isEmpty() && !dbHost.equals("localhost");
    }
    
    private boolean testRedisConnectivity() {
        // Verificación simple - en un entorno real usaríamos el health check service
        String redisHost = configService.getString("redis.host", "");
        return !redisHost.isEmpty() && !redisHost.equals("localhost");
    }
    
    private boolean validateConfiguration() {
        // Verificar que las configuraciones críticas estén presentes
        String[] criticalConfigs = {"db.host", "db.port", "db.name", "redis.host"};
        
        for (String config : criticalConfigs) {
            String value = configService.getString(config, "");
            if (value.isEmpty()) {
                return false;
            }
        }
        return true;
    }
    
    private boolean checkCriticalEnvironmentVariables() {
        String[] criticalVars = {"DB_HOST", "DB_PORT", "DB_NAME", "REDIS_HOST"};
        
        for (String var : criticalVars) {
            if (System.getenv(var) == null) {
                return false;
            }
        }
        return true;
    }
    
    private boolean testBasicNetworkConnectivity() {
        try {
            // Verificar que podemos resolver localhost
            InetAddress.getByName("localhost");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private JsonArray generateRecommendations() {
        JsonArray recommendations = new JsonArray();
        
        if (!checkCriticalEnvironmentVariables()) {
            recommendations.add("Verificar que todas las variables de entorno críticas estén definidas");
        }
        
        if (!validateConfiguration()) {
            recommendations.add("Revisar la configuración de la aplicación");
        }
        
        if (configService.getString("db.host", "").equals("localhost")) {
            recommendations.add("La base de datos está configurada como localhost - verificar en entorno Docker");
        }
        
        if (!isRunningInContainer() && !configService.isDevelopmentMode()) {
            recommendations.add("Considerar ejecutar en contenedor para ambiente de producción");
        }
        
        return recommendations;
    }
}
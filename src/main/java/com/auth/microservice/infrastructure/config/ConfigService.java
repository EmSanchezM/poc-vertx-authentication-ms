package com.auth.microservice.infrastructure.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio central de configuración que maneja variables de entorno,
 * archivos properties y validación de configuración para diferentes ambientes.
 */
public class ConfigService {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigService.class);
    
    private final Map<String, String> configCache = new ConcurrentHashMap<>();
    private final String environment;
    private final Properties properties;
    private final Dotenv dotenv;
    
    public ConfigService() {
        this.environment = determineEnvironment();
        this.dotenv = loadDotenvFiles();
        this.properties = loadProperties();
        logConfigurationSources();
        initializeConfiguration();
        validateConfiguration();
        
        logger.info("ConfigService inicializado para ambiente: {}", environment);
        logger.info("Jerarquía de configuración: env vars → system props → .env → application.properties");
    }
    
    /**
     * Determina el ambiente actual basado en variables de entorno
     */
    private String determineEnvironment() {
        String env = System.getenv("APP_ENV");
        if (env == null) {
            env = System.getProperty("app.env", "development");
        }
        
        // Validar que el ambiente sea válido
        Set<String> validEnvironments = Set.of("development", "qa", "production");
        if (!validEnvironments.contains(env)) {
            logger.warn("Ambiente inválido '{}', usando 'development' por defecto", env);
            env = "development";
        }
        
        return env;
    }
    
    /**
     * Carga archivos .env basado en el ambiente
     * Prioridad: .env.{environment} → .env
     */
    private Dotenv loadDotenvFiles() {
        if (!isDevelopmentMode()) {
            logger.info("Modo no desarrollo detectado, omitiendo carga de archivos .env");
            return null;
        }
        
        try {
            // Intentar cargar archivo específico del ambiente primero
            String environmentFile = ".env." + environment;
            Dotenv envSpecific = null;
            
            try {
                envSpecific = Dotenv.configure()
                    .filename(environmentFile)
                    .ignoreIfMissing()
                    .load();
                
                if (envSpecific.entries().size() > 0) {
                    logger.info("Cargado archivo .env específico del ambiente: {}", environmentFile);
                }
            } catch (Exception e) {
                logger.debug("No se pudo cargar {}: {}", environmentFile, e.getMessage());
            }
            
            // Cargar archivo .env base
            Dotenv envBase = Dotenv.configure()
                .ignoreIfMissing()
                .load();
            
            if (envBase.entries().size() > 0) {
                logger.info("Cargado archivo .env base con {} variables", envBase.entries().size());
            }
            
            // Si tenemos archivo específico, lo usamos; sino el base
            return envSpecific != null && envSpecific.entries().size() > 0 ? envSpecific : envBase;
            
        } catch (Exception e) {
            logger.warn("Error cargando archivos .env: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Detecta si estamos en modo desarrollo
     */
    public boolean isDevelopmentMode() {
        return "development".equals(environment);
    }
    
    /**
     * Registra información sobre las fuentes de configuración disponibles
     */
    private void logConfigurationSources() {
        logger.info("=== CONFIGURACIÓN DE FUENTES ===");
        logger.info("Ambiente detectado: {}", environment);
        logger.info("Modo desarrollo: {}", isDevelopmentMode());
        
        // Logging de TODAS las variables de entorno disponibles al inicio
        logAllEnvironmentVariables();
        
        // Información sobre variables de entorno críticas
        String[] criticalEnvVars = {"DB_HOST", "DB_PORT", "DB_NAME", "DB_USERNAME", "REDIS_HOST", "JWT_SECRET"};
        logger.info("Variables de entorno críticas:");
        for (String envVar : criticalEnvVars) {
            String value = System.getenv(envVar);
            if (value != null) {
                String logValue = isSensitiveKey(envVar.toLowerCase()) ? "***" : value;
                logger.info("  {} = {} (desde env var)", envVar, logValue);
            } else {
                logger.warn("  {} = NO DEFINIDA", envVar);
            }
        }
        
        // Información sobre archivos .env
        if (dotenv != null) {
            logger.info("Archivo .env cargado con {} variables", dotenv.entries().size());
            if (logger.isDebugEnabled()) {
                logger.debug("Variables desde .env:");
                dotenv.entries().forEach(entry -> {
                    String logValue = isSensitiveKey(entry.getKey().toLowerCase()) ? "***" : entry.getValue();
                    logger.debug("  {} = {}", entry.getKey(), logValue);
                });
            }
        } else {
            logger.info("No se cargó archivo .env (normal en producción)");
        }
        
        // Información sobre archivos properties
        logger.info("Propiedades cargadas desde archivos: {}", properties.size());
        logger.info("=== FIN CONFIGURACIÓN DE FUENTES ===");
    }
    
    /**
     * Registra todas las variables de entorno disponibles al inicio
     * para facilitar el debugging de problemas de configuración
     */
    private void logAllEnvironmentVariables() {
        logger.info("=== 🌍 INVENTARIO COMPLETO DE VARIABLES DE ENTORNO ===");
        Map<String, String> envVars = System.getenv();
        
        if (envVars.isEmpty()) {
            logger.error("❌ PROBLEMA CRÍTICO: No se encontraron variables de entorno del sistema");
            logger.error("   Esto podría indicar un problema con el contenedor o el entorno de ejecución");
        } else {
            logger.info("📊 Total de variables de entorno disponibles: {}", envVars.size());
            
            // Categorizar variables para mejor organización
            Map<String, List<Map.Entry<String, String>>> categorizedVars = new HashMap<>();
            categorizedVars.put("CRÍTICAS", new ArrayList<>());
            categorizedVars.put("DOCKER", new ArrayList<>());
            categorizedVars.put("JAVA", new ArrayList<>());
            categorizedVars.put("SISTEMA", new ArrayList<>());
            categorizedVars.put("OTRAS", new ArrayList<>());
            
            // Clasificar variables
            envVars.entrySet().forEach(entry -> {
                String key = entry.getKey();
                String lowerKey = key.toLowerCase();
                
                if (isCriticalVariable(key)) {
                    categorizedVars.get("CRÍTICAS").add(entry);
                } else if (lowerKey.contains("docker") || lowerKey.contains("container")) {
                    categorizedVars.get("DOCKER").add(entry);
                } else if (lowerKey.startsWith("java") || lowerKey.contains("jvm")) {
                    categorizedVars.get("JAVA").add(entry);
                } else if (lowerKey.startsWith("path") || lowerKey.startsWith("home") || 
                          lowerKey.startsWith("user") || lowerKey.startsWith("os")) {
                    categorizedVars.get("SISTEMA").add(entry);
                } else {
                    categorizedVars.get("OTRAS").add(entry);
                }
            });
            
            // Registrar por categorías
            categorizedVars.entrySet().forEach(categoryEntry -> {
                String category = categoryEntry.getKey();
                List<Map.Entry<String, String>> vars = categoryEntry.getValue();
                
                if (!vars.isEmpty()) {
                    logger.info("📂 Categoría [{}] ({} variables):", category, vars.size());
                    
                    vars.stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> {
                            String key = entry.getKey();
                            String value = entry.getValue();
                            String logValue = isSensitiveKey(key.toLowerCase()) ? "***" : 
                                            (value.length() > 100 ? value.substring(0, 100) + "..." : value);
                            
                            String criticalMarker = isCriticalVariable(key) ? " 🔥" : "";
                            String lengthInfo = value.length() > 50 ? String.format(" [%d chars]", value.length()) : "";
                            
                            if ("CRÍTICAS".equals(category)) {
                                logger.info("  🔑 {} = {}{}{}", key, logValue, lengthInfo, criticalMarker);
                            } else {
                                logger.debug("  {} = {}{}{}", key, logValue, lengthInfo, criticalMarker);
                            }
                        });
                }
            });
            
            // Verificación específica de variables críticas para la base de datos
            logger.info("🔍 VERIFICACIÓN ESPECÍFICA DE VARIABLES CRÍTICAS:");
            String[] dbCriticalVars = {"DB_HOST", "DB_PORT", "DB_NAME", "DB_USERNAME", "DB_PASSWORD"};
            for (String varName : dbCriticalVars) {
                String value = System.getenv(varName);
                if (value != null && !value.trim().isEmpty()) {
                    String logValue = isSensitiveKey(varName.toLowerCase()) ? "***" : value;
                    logger.info("  ✅ {} = {} [DISPONIBLE]", varName, logValue);
                } else {
                    logger.warn("  ❌ {} = NO DEFINIDA O VACÍA [PROBLEMA POTENCIAL]", varName);
                }
            }
        }
        
        // También registrar propiedades del sistema relevantes
        logger.info("🔧 PROPIEDADES DEL SISTEMA RELEVANTES:");
        String[] relevantSystemProps = {
            "java.version", "java.home", "java.class.path",
            "os.name", "os.version", "os.arch",
            "user.dir", "user.home", "user.name",
            "file.encoding", "file.separator",
            "spring.profiles.active", "app.env"
        };
        
        for (String prop : relevantSystemProps) {
            String value = System.getProperty(prop);
            if (value != null) {
                String logValue = value.length() > 100 ? value.substring(0, 100) + "..." : value;
                logger.info("  {} = {}", prop, logValue);
            } else {
                logger.debug("  {} = NO DEFINIDA", prop);
            }
        }
        
        // Información adicional del entorno de ejecución
        logger.info("🏃 INFORMACIÓN DEL ENTORNO DE EJECUCIÓN:");
        logger.info("  Procesadores disponibles: {}", Runtime.getRuntime().availableProcessors());
        logger.info("  Memoria máxima JVM: {} MB", Runtime.getRuntime().maxMemory() / 1024 / 1024);
        logger.info("  Memoria total JVM: {} MB", Runtime.getRuntime().totalMemory() / 1024 / 1024);
        logger.info("  Memoria libre JVM: {} MB", Runtime.getRuntime().freeMemory() / 1024 / 1024);
        
        logger.info("=== 🏁 FIN INVENTARIO DE VARIABLES DE ENTORNO ===");
    }
    
    /**
     * Determina si una variable es crítica para la aplicación
     */
    private boolean isCriticalVariable(String key) {
        String lowerKey = key.toLowerCase();
        return lowerKey.startsWith("db_") || 
               lowerKey.startsWith("redis_") ||
               lowerKey.contains("jwt") ||
               lowerKey.contains("secret") ||
               lowerKey.equals("app_env") ||
               lowerKey.equals("java_opts") ||
               lowerKey.equals("spring_profiles_active");
    }
    
    /**
     * Carga las propiedades desde archivos de configuración
     */
    private Properties loadProperties() {
        Properties props = new Properties();
        
        // Cargar propiedades base
        loadPropertiesFile(props, "application.properties");
        
        // Cargar propiedades específicas del ambiente
        String environmentFile = "application-" + environment + ".properties";
        loadPropertiesFile(props, environmentFile);
        
        return props;
    }
    
    /**
     * Carga un archivo de propiedades específico
     */
    private void loadPropertiesFile(Properties props, String filename) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename)) {
            if (is != null) {
                props.load(is);
                logger.debug("Cargado archivo de propiedades: {}", filename);
            } else {
                logger.warn("Archivo de propiedades no encontrado: {}", filename);
            }
        } catch (IOException e) {
            logger.error("Error cargando archivo de propiedades: {}", filename, e);
        }
    }
    
    /**
     * Inicializa la configuración cacheando valores resueltos
     */
    private void initializeConfiguration() {
        logger.info("=== INICIALIZANDO CONFIGURACIÓN ===");
        
        // Procesar todas las propiedades y resolver variables de entorno
        for (String key : properties.stringPropertyNames()) {
            String originalValue = properties.getProperty(key);
            String resolvedValue = resolveValue(key, originalValue);
            configCache.put(key, resolvedValue);
        }
        
        logger.info("Configuración inicializada con {} propiedades", configCache.size());
        
        // Registrar los valores finales resueltos (ocultando passwords y secretos)
        logFinalResolvedValues();
        
        logger.info("=== FIN INICIALIZACIÓN CONFIGURACIÓN ===");
    }
    
    /**
     * Registra los valores finales resueltos ocultando información sensible
     */
    private void logFinalResolvedValues() {
        logger.info("=== 📋 CONFIGURACIÓN FINAL RESUELTA ===");
        
        // Agrupar por categorías para mejor legibilidad
        Map<String, Map<String, String>> categorizedConfig = new HashMap<>();
        Map<String, Map<String, String>> sourceInfo = new HashMap<>();
        
        for (Map.Entry<String, String> entry : configCache.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            String category = getCategoryFromKey(key);
            
            // Determinar la fuente de cada valor
            String source = determineValueSource(key);
            
            categorizedConfig.computeIfAbsent(category, k -> new HashMap<>())
                .put(key, isSensitiveKey(key) ? "***" : value);
            
            sourceInfo.computeIfAbsent(category, k -> new HashMap<>())
                .put(key, source);
        }
        
        // Registrar estadísticas generales
        logger.info("📊 ESTADÍSTICAS DE CONFIGURACIÓN:");
        logger.info("  Total de propiedades resueltas: {}", configCache.size());
        logger.info("  Categorías de configuración: {}", categorizedConfig.size());
        
        // Contar fuentes utilizadas
        Map<String, Long> sourceCounts = sourceInfo.values().stream()
            .flatMap(map -> map.values().stream())
            .collect(java.util.stream.Collectors.groupingBy(
                java.util.function.Function.identity(),
                java.util.stream.Collectors.counting()
            ));
        
        logger.info("  Distribución por fuentes:");
        sourceCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(entry -> {
                String sourceIcon = getSourceIcon(entry.getKey());
                logger.info("    {} {}: {} propiedades", sourceIcon, entry.getKey(), entry.getValue());
            });
        
        // Registrar por categorías con información de fuente
        categorizedConfig.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(categoryEntry -> {
                String category = categoryEntry.getKey();
                Map<String, String> configs = categoryEntry.getValue();
                Map<String, String> sources = sourceInfo.get(category);
                
                String categoryIcon = getCategoryIcon(category);
                logger.info("{} Categoría [{}] ({} propiedades):", categoryIcon, category.toUpperCase(), configs.size());
                
                configs.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(configEntry -> {
                        String key = configEntry.getKey();
                        String value = configEntry.getValue();
                        String source = sources.get(key);
                        String sourceIcon = getSourceIcon(source);
                        
                        // Información adicional sobre el valor
                        String valueInfo = "";
                        if (!isSensitiveKey(key) && value != null) {
                            if (value.length() > 50) {
                                valueInfo = String.format(" [%d chars]", value.length());
                            }
                            if (value.contains("${")) {
                                valueInfo += " [contiene placeholders sin resolver]";
                            }
                        }
                        
                        logger.info("  {} {} = {} {}{}", sourceIcon, key, value, 
                            source.equals("default") ? "[DEFAULT]" : "[" + source.toUpperCase() + "]", 
                            valueInfo);
                    });
            });
        
        // Verificación de configuración crítica
        logger.info("🔍 VERIFICACIÓN DE CONFIGURACIÓN CRÍTICA:");
        String[] criticalConfigs = {"db.host", "db.port", "db.name", "db.username", "jwt.secret"};
        for (String config : criticalConfigs) {
            String value = configCache.get(config);
            String source = determineValueSource(config);
            String sourceIcon = getSourceIcon(source);
            
            if (value != null && !value.trim().isEmpty()) {
                String logValue = isSensitiveKey(config) ? "***" : value;
                logger.info("  ✅ {} {} = {} [{}]", sourceIcon, config, logValue, source.toUpperCase());
            } else {
                logger.error("  ❌ {} = NO CONFIGURADA O VACÍA [PROBLEMA CRÍTICO]", config);
            }
        }
        
        logger.info("=== 🏁 FIN CONFIGURACIÓN FINAL RESUELTA ===");
    }
    
    /**
     * Determina la fuente de un valor de configuración
     */
    private String determineValueSource(String key) {
        // Verificar en orden de prioridad
        if (System.getenv(key) != null) {
            return "env-var";
        }
        if (System.getProperty(key) != null) {
            return "system-prop";
        }
        if (dotenv != null && isDevelopmentMode() && dotenv.get(key) != null) {
            return "dotenv";
        }
        if (properties.getProperty(key) != null) {
            return "properties";
        }
        return "default";
    }
    
    /**
     * Obtiene el icono para una fuente de configuración
     */
    private String getSourceIcon(String source) {
        switch (source) {
            case "env-var": return "🌍";
            case "system-prop": return "⚙️";
            case "dotenv": return "📄";
            case "properties": return "📝";
            case "default": return "🔧";
            default: return "❓";
        }
    }
    
    /**
     * Obtiene el icono para una categoría de configuración
     */
    private String getCategoryIcon(String category) {
        switch (category) {
            case "database": return "🗄️";
            case "redis": return "🔴";
            case "jwt": return "🔐";
            case "security": return "🛡️";
            case "server": return "🖥️";
            case "logging": return "📊";
            case "management": return "⚡";
            default: return "📦";
        }
    }
    
    /**
     * Obtiene la categoría de una clave de configuración para organizar el logging
     */
    private String getCategoryFromKey(String key) {
        String lowerKey = key.toLowerCase();
        
        if (lowerKey.startsWith("db.")) return "database";
        if (lowerKey.startsWith("redis.")) return "redis";
        if (lowerKey.startsWith("jwt.")) return "jwt";
        if (lowerKey.startsWith("security.")) return "security";
        if (lowerKey.startsWith("server.")) return "server";
        if (lowerKey.startsWith("logging.")) return "logging";
        if (lowerKey.startsWith("management.")) return "management";
        
        return "general";
    }
    
    /**
     * Resuelve variables de entorno en valores de propiedades con logging detallado
     * Jerarquía: env vars → system props → .env → default_value
     * Formato: ${ENV_VAR:default_value}
     */
    private String resolveValue(String configKey, String value) {
        if (value == null || !value.contains("${")) {
            if (logger.isDebugEnabled()) {
                logger.debug("Resolviendo '{}': valor literal sin variables = '{}'", 
                    configKey, isSensitiveKey(configKey) ? "***" : value);
            }
            return value;
        }
        
        logger.info("=== INICIANDO RESOLUCIÓN DE CONFIGURACIÓN: {} ===", configKey);
        logger.info("Valor original con placeholders: '{}'", isSensitiveKey(configKey) ? "***" : value);
        
        String resolved = value;
        int start = resolved.indexOf("${");
        int placeholderCount = 0;
        
        while (start != -1) {
            int end = resolved.indexOf("}", start);
            if (end == -1) {
                logger.error("❌ Placeholder malformado en '{}': falta '}' de cierre en posición {}", configKey, start);
                logger.error("   Contenido problemático: '{}'", value.substring(start));
                break;
            }
            
            placeholderCount++;
            String placeholder = resolved.substring(start + 2, end);
            String[] parts = placeholder.split(":", 2);
            String envVar = parts[0].trim();
            String defaultValue = parts.length > 1 ? parts[1] : "";
            
            logger.info("📋 Procesando placeholder #{} en '{}': variable='{}', default='{}'", 
                placeholderCount, configKey, envVar, isSensitiveKey(envVar) ? "***" : defaultValue);
            
            // Registrar el estado antes de la resolución
            logger.debug("   Estado antes de resolución: '{}'", resolved.substring(start, end + 1));
            
            // Implementar jerarquía de configuración con logging detallado
            String resolvedValue = resolveValueFromHierarchyWithLogging(envVar, defaultValue, configKey);
            
            String beforeReplacement = resolved;
            resolved = resolved.substring(0, start) + resolvedValue + resolved.substring(end + 1);
            
            logger.info("🔄 Reemplazo completado: '{}' → '{}'", 
                beforeReplacement.substring(start, end + 1),
                isSensitiveKey(envVar) ? "***" : resolvedValue);
            
            logger.debug("   Cadena después del reemplazo: '{}'", 
                isSensitiveKey(configKey) ? "***" : resolved);
            
            start = resolved.indexOf("${", start);
        }
        
        String finalLogValue = isSensitiveKey(configKey) ? "***" : resolved;
        logger.info("✅ RESOLUCIÓN COMPLETADA para '{}': valor final = '{}'", configKey, finalLogValue);
        
        // Registrar un resumen de la resolución
        if (placeholderCount > 0) {
            logger.info("📊 Resumen: {} placeholder(s) procesados en configuración '{}'", placeholderCount, configKey);
        }
        
        logger.info("=== FIN RESOLUCIÓN: {} ===", configKey);
        
        return resolved;
    }
    
    /**
     * Método de compatibilidad para llamadas existentes sin configKey
     */
    private String resolveValue(String value) {
        return resolveValue("unknown", value);
    }
    
    /**
     * Resuelve un valor siguiendo la jerarquía de configuración con logging detallado:
     * 1. Variables de entorno del sistema
     * 2. Propiedades del sistema
     * 3. Archivo .env (solo en desarrollo)
     * 4. Valor por defecto
     */
    private String resolveValueFromHierarchyWithLogging(String key, String defaultValue, String configKey) {
        logger.info("  🔍 INICIANDO RESOLUCIÓN JERÁRQUICA para variable: '{}' (configuración: '{}')", key, configKey);
        
        // Registrar información del contexto de resolución
        logger.debug("    Contexto: ambiente='{}', desarrollo={}, dotenv disponible={}", 
            environment, isDevelopmentMode(), dotenv != null);
        
        // 1. Variables de entorno del sistema (máxima prioridad)
        logger.debug("  🔎 [NIVEL 1] Verificando variables de entorno del sistema...");
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.trim().isEmpty()) {
            String logValue = isSensitiveKey(key) ? "***" : envValue;
            logger.info("  ✅ [NIVEL 1] Variable '{}' ENCONTRADA en entorno del sistema: '{}'", key, logValue);
            logger.info("    📍 Fuente utilizada: System.getenv('{}') para configuración '{}'", key, configKey);
            logger.debug("    📏 Longitud del valor: {} caracteres", envValue.length());
            return envValue;
        } else {
            if (envValue == null) {
                logger.debug("  ❌ [NIVEL 1] Variable de entorno '{}' es NULL", key);
            } else {
                logger.debug("  ❌ [NIVEL 1] Variable de entorno '{}' está VACÍA", key);
            }
        }
        
        // 2. Propiedades del sistema
        logger.debug("  🔎 [NIVEL 2] Verificando propiedades del sistema...");
        String systemProp = System.getProperty(key);
        if (systemProp != null && !systemProp.trim().isEmpty()) {
            String logValue = isSensitiveKey(key) ? "***" : systemProp;
            logger.info("  ✅ [NIVEL 2] Variable '{}' ENCONTRADA en propiedades del sistema: '{}'", key, logValue);
            logger.info("    📍 Fuente utilizada: System.getProperty('{}') para configuración '{}'", key, configKey);
            logger.debug("    📏 Longitud del valor: {} caracteres", systemProp.length());
            return systemProp;
        } else {
            if (systemProp == null) {
                logger.debug("  ❌ [NIVEL 2] Propiedad del sistema '{}' es NULL", key);
            } else {
                logger.debug("  ❌ [NIVEL 2] Propiedad del sistema '{}' está VACÍA", key);
            }
        }
        
        // 3. Archivo .env (solo en desarrollo)
        logger.debug("  🔎 [NIVEL 3] Verificando archivo .env...");
        if (dotenv != null && isDevelopmentMode()) {
            String dotenvValue = dotenv.get(key);
            if (dotenvValue != null && !dotenvValue.trim().isEmpty()) {
                String logValue = isSensitiveKey(key) ? "***" : dotenvValue;
                logger.info("  ✅ [NIVEL 3] Variable '{}' ENCONTRADA en archivo .env: '{}'", key, logValue);
                logger.info("    📍 Fuente utilizada: archivo .env para configuración '{}'", key, configKey);
                logger.debug("    📏 Longitud del valor: {} caracteres", dotenvValue.length());
                return dotenvValue;
            } else {
                if (dotenvValue == null) {
                    logger.debug("  ❌ [NIVEL 3] Variable '{}' es NULL en archivo .env", key);
                } else {
                    logger.debug("  ❌ [NIVEL 3] Variable '{}' está VACÍA en archivo .env", key);
                }
            }
        } else {
            if (dotenv == null) {
                logger.debug("  ⏭️ [NIVEL 3] Archivo .env NO DISPONIBLE");
            } else if (!isDevelopmentMode()) {
                logger.debug("  ⏭️ [NIVEL 3] Archivo .env OMITIDO (ambiente '{}' no es desarrollo)", environment);
            }
        }
        
        // 4. Valor por defecto
        logger.debug("  🔎 [NIVEL 4] Usando valor por defecto...");
        String logValue = isSensitiveKey(key) ? "***" : defaultValue;
        if (defaultValue != null && !defaultValue.trim().isEmpty()) {
            logger.info("  ✅ [NIVEL 4] Variable '{}' usando VALOR POR DEFECTO: '{}'", key, logValue);
            logger.info("    📍 Fuente utilizada: valor por defecto para configuración '{}'", key, configKey);
            logger.debug("    📏 Longitud del valor por defecto: {} caracteres", defaultValue.length());
        } else {
            logger.warn("  ⚠️ [NIVEL 4] Variable '{}' con valor por defecto VACÍO/NULL para configuración '{}'", key, configKey);
            logger.warn("    🚨 ATENCIÓN: Esto podría causar problemas de configuración");
        }
        
        // Registrar resumen de la resolución jerárquica
        String finalSource = envValue != null ? "env-var" : 
                           systemProp != null ? "system-prop" : 
                           (dotenv != null && isDevelopmentMode() && dotenv.get(key) != null) ? "dotenv" : 
                           "default";
        
        logger.info("  📋 RESUMEN JERÁRQUICO: variable='{}', fuente_final='{}', config='{}'", 
            key, finalSource, configKey);
        
        return defaultValue;
    }
    
    /**
     * Método de compatibilidad para llamadas existentes
     */
    private String resolveValueFromHierarchy(String key, String defaultValue) {
        return resolveValueFromHierarchyWithLogging(key, defaultValue, "legacy-call");
    }
    
    /**
     * Valida la configuración crítica
     */
    private void validateConfiguration() {
        List<String> errors = new ArrayList<>();
        
        // Validar configuración de base de datos
        validateRequired("db.host", errors);
        validateRequired("db.port", errors);
        validateRequired("db.name", errors);
        validateRequired("db.username", errors);
        validateRequired("db.password", errors);
        
        // Validar configuración JWT
        validateRequired("jwt.secret", errors);
        validateJwtSecret(errors);
        
        // Validar configuración de seguridad
        validateBcryptRounds(errors);
        
        // Validar configuración de rate limiting
        validatePositiveInteger("security.rateLimit.login.attempts", errors);
        validatePositiveInteger("security.rateLimit.api.requestsPerMinute", errors);
        
        if (!errors.isEmpty()) {
            String errorMessage = "Errores de validación de configuración: " + String.join(", ", errors);
            logger.error(errorMessage);
            throw new ConfigurationException(errorMessage);
        }
        
        logger.info("Validación de configuración completada exitosamente");
    }
    
    private void validateRequired(String key, List<String> errors) {
        String value = configCache.get(key);
        if (value == null || value.trim().isEmpty()) {
            errors.add("Propiedad requerida faltante: " + key);
        }
    }
    
    private void validateJwtSecret(List<String> errors) {
        String secret = configCache.get("jwt.secret");
        if (secret != null) {
            if (secret.length() < 32) {
                errors.add("jwt.secret debe tener al menos 32 caracteres");
            }
            if ("production".equals(environment) && secret.contains("default")) {
                errors.add("jwt.secret no debe contener valores por defecto en producción");
            }
        }
    }
    
    private void validateBcryptRounds(List<String> errors) {
        try {
            int rounds = getInt("security.bcrypt.rounds");
            if (rounds < 4 || rounds > 20) {
                errors.add("security.bcrypt.rounds debe estar entre 4 y 20");
            }
        } catch (NumberFormatException e) {
            errors.add("security.bcrypt.rounds debe ser un número válido");
        }
    }
    
    private void validatePositiveInteger(String key, List<String> errors) {
        try {
            int value = getInt(key);
            if (value <= 0) {
                errors.add(key + " debe ser un número positivo");
            }
        } catch (NumberFormatException e) {
            errors.add(key + " debe ser un número válido");
        }
    }
    
    // Métodos públicos para obtener configuración
    
    public String getString(String key) {
        return configCache.get(key);
    }
    
    public String getString(String key, String defaultValue) {
        return configCache.getOrDefault(key, defaultValue);
    }
    
    public int getInt(String key) {
        String value = configCache.get(key);
        if (value == null) {
            throw new ConfigurationException("Propiedad no encontrada: " + key);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Valor inválido para " + key + ": " + value);
        }
    }
    
    public int getInt(String key, int defaultValue) {
        String value = configCache.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("Valor inválido para {}: {}, usando valor por defecto: {}", key, value, defaultValue);
            return defaultValue;
        }
    }
    
    public long getLong(String key) {
        String value = configCache.get(key);
        if (value == null) {
            throw new ConfigurationException("Propiedad no encontrada: " + key);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Valor inválido para " + key + ": " + value);
        }
    }
    
    public long getLong(String key, long defaultValue) {
        String value = configCache.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            logger.warn("Valor inválido para {}: {}, usando valor por defecto: {}", key, value, defaultValue);
            return defaultValue;
        }
    }
    
    public boolean getBoolean(String key) {
        String value = configCache.get(key);
        if (value == null) {
            throw new ConfigurationException("Propiedad no encontrada: " + key);
        }
        return Boolean.parseBoolean(value);
    }
    
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = configCache.get(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
    
    public String getEnvironment() {
        return environment;
    }
    
    public boolean isDevelopment() {
        return isDevelopmentMode();
    }
    
    public boolean isProduction() {
        return "production".equals(environment);
    }
    
    public boolean isQa() {
        return "qa".equals(environment);
    }
    
    /**
     * Obtiene todas las propiedades que comienzan con un prefijo
     */
    public Map<String, String> getPropertiesWithPrefix(String prefix) {
        Map<String, String> result = new HashMap<>();
        String prefixWithDot = prefix.endsWith(".") ? prefix : prefix + ".";
        
        for (Map.Entry<String, String> entry : configCache.entrySet()) {
            if (entry.getKey().startsWith(prefixWithDot)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        
        return result;
    }
    
    /**
     * Obtiene información de configuración para debugging
     */
    public Map<String, Object> getConfigInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("environment", environment);
        info.put("isDevelopmentMode", isDevelopmentMode());
        info.put("propertiesCount", configCache.size());
        info.put("dotenvLoaded", dotenv != null);
        info.put("dotenvEntriesCount", dotenv != null ? dotenv.entries().size() : 0);
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("osName", System.getProperty("os.name"));
        info.put("userDir", System.getProperty("user.dir"));
        
        // Información sobre la jerarquía de configuración
        info.put("configurationHierarchy", Arrays.asList(
            "1. Environment Variables (System.getenv())",
            "2. System Properties (System.getProperty())",
            "3. .env files (development only)",
            "4. application.properties defaults"
        ));
        
        // No incluir valores sensibles
        Map<String, String> safeConfig = new HashMap<>();
        for (Map.Entry<String, String> entry : configCache.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            if (isSensitiveKey(key)) {
                safeConfig.put(key, "***");
            } else {
                safeConfig.put(key, value);
            }
        }
        info.put("configuration", safeConfig);
        
        return info;
    }
    
    /**
     * Obtiene información detallada sobre cómo se resolvió una configuración específica
     */
    public Map<String, Object> getConfigResolutionInfo(String key) {
        Map<String, Object> info = new HashMap<>();
        info.put("key", key);
        info.put("finalValue", isSensitiveKey(key) ? "***" : configCache.get(key));
        
        // Verificar cada nivel de la jerarquía
        String envValue = System.getenv(key);
        String systemProp = System.getProperty(key);
        String dotenvValue = dotenv != null ? dotenv.get(key) : null;
        String propertyValue = properties.getProperty(key);
        
        Map<String, String> sources = new HashMap<>();
        sources.put("environmentVariable", envValue != null ? (isSensitiveKey(key) ? "***" : envValue) : null);
        sources.put("systemProperty", systemProp != null ? (isSensitiveKey(key) ? "***" : systemProp) : null);
        sources.put("dotenvFile", dotenvValue != null ? (isSensitiveKey(key) ? "***" : dotenvValue) : null);
        sources.put("propertiesFile", propertyValue != null ? (isSensitiveKey(key) ? "***" : propertyValue) : null);
        
        info.put("sources", sources);
        
        // Determinar qué fuente se usó
        String usedSource = "not found";
        if (envValue != null) {
            usedSource = "environmentVariable";
        } else if (systemProp != null) {
            usedSource = "systemProperty";
        } else if (dotenvValue != null && isDevelopmentMode()) {
            usedSource = "dotenvFile";
        } else if (propertyValue != null) {
            usedSource = "propertiesFile";
        }
        
        info.put("usedSource", usedSource);
        info.put("hierarchy", Arrays.asList(
            "environmentVariable", "systemProperty", "dotenvFile", "propertiesFile"
        ));
        
        return info;
    }
    
    private boolean isSensitiveKey(String key) {
        String lowerKey = key.toLowerCase();
        return lowerKey.contains("password") || 
               lowerKey.contains("secret") || 
               lowerKey.contains("key") ||
               lowerKey.contains("token");
    }
    
    /**
     * Excepción para errores de configuración
     */
    public static class ConfigurationException extends RuntimeException {
        public ConfigurationException(String message) {
            super(message);
        }
        
        public ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
package com.auth.microservice.infrastructure.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Propiedades de configuración para la generación automática de usernames.
 * Maneja parámetros configurables como longitudes, límites de intentos,
 * palabras reservadas y configuración de caché.
 */
public class UsernameGenerationProperties {
    
    private int maxLength;
    private int minLength;
    private int maxCollisionAttempts;
    private Set<String> reservedWords;
    private CacheProperties cache;
    
    public UsernameGenerationProperties() {
        // Valores por defecto
        this.maxLength = 64;
        this.minLength = 3;
        this.maxCollisionAttempts = 100;
        this.reservedWords = getDefaultReservedWords();
        this.cache = new CacheProperties();
    }
    
    public UsernameGenerationProperties(ConfigService configService) {
        this.maxLength = configService.getInt("username.generation.maxLength", 64);
        this.minLength = configService.getInt("username.generation.minLength", 3);
        this.maxCollisionAttempts = configService.getInt("username.generation.maxCollisionAttempts", 100);
        this.reservedWords = parseReservedWords(configService.getString("username.generation.reservedWords", ""));
        this.cache = new CacheProperties(configService);
        
        validateConfiguration();
    }
    
    /**
     * Valida que los valores de configuración sean válidos
     */
    private void validateConfiguration() {
        if (minLength < 1) {
            throw new IllegalArgumentException("username.generation.minLength debe ser mayor que 0, valor actual: " + minLength);
        }
        
        if (maxLength < minLength) {
            throw new IllegalArgumentException(
                String.format("username.generation.maxLength (%d) debe ser mayor o igual que minLength (%d)", 
                    maxLength, minLength));
        }
        
        if (maxLength > 255) {
            throw new IllegalArgumentException("username.generation.maxLength no puede ser mayor que 255, valor actual: " + maxLength);
        }
        
        if (maxCollisionAttempts < 1) {
            throw new IllegalArgumentException("username.generation.maxCollisionAttempts debe ser mayor que 0, valor actual: " + maxCollisionAttempts);
        }
        
        if (maxCollisionAttempts > 1000) {
            throw new IllegalArgumentException("username.generation.maxCollisionAttempts no puede ser mayor que 1000, valor actual: " + maxCollisionAttempts);
        }
        
        if (reservedWords == null) {
            this.reservedWords = getDefaultReservedWords();
        }
    }
    
    /**
     * Parsea la lista de palabras reservadas desde configuración
     */
    private Set<String> parseReservedWords(String reservedWordsConfig) {
        Set<String> words = new HashSet<>();
        
        // Agregar palabras por defecto
        words.addAll(getDefaultReservedWords());
        
        // Agregar palabras adicionales desde configuración
        if (reservedWordsConfig != null && !reservedWordsConfig.trim().isEmpty()) {
            String[] additionalWords = reservedWordsConfig.split(",");
            for (String word : additionalWords) {
                String trimmedWord = word.trim().toLowerCase();
                if (!trimmedWord.isEmpty()) {
                    words.add(trimmedWord);
                }
            }
        }
        
        return words;
    }
    
    /**
     * Obtiene el conjunto por defecto de palabras reservadas
     */
    private Set<String> getDefaultReservedWords() {
        return new HashSet<>(Arrays.asList(
            "admin", "root", "system", "api", "www", "mail", "ftp",
            "test", "demo", "guest", "anonymous", "null", "undefined",
            "user", "users", "account", "accounts", "service", "services",
            "support", "help", "info", "contact", "about", "home",
            "login", "logout", "register", "signup", "signin", "auth",
            "password", "pass", "pwd", "secret", "token", "key",
            "config", "configuration", "settings", "setup", "install",
            "update", "upgrade", "download", "upload", "backup",
            "database", "db", "sql", "mysql", "postgres", "redis",
            "cache", "session", "cookie", "temp", "tmp", "log", "logs"
        ));
    }
    
    // Getters y setters
    public int getMaxLength() { return maxLength; }
    public void setMaxLength(int maxLength) { 
        this.maxLength = maxLength;
        validateConfiguration();
    }
    
    public int getMinLength() { return minLength; }
    public void setMinLength(int minLength) { 
        this.minLength = minLength;
        validateConfiguration();
    }
    
    public int getMaxCollisionAttempts() { return maxCollisionAttempts; }
    public void setMaxCollisionAttempts(int maxCollisionAttempts) { 
        this.maxCollisionAttempts = maxCollisionAttempts;
        validateConfiguration();
    }
    
    public Set<String> getReservedWords() { return new HashSet<>(reservedWords); }
    public void setReservedWords(Set<String> reservedWords) { 
        this.reservedWords = reservedWords != null ? new HashSet<>(reservedWords) : getDefaultReservedWords();
    }
    
    public CacheProperties getCache() { return cache; }
    public void setCache(CacheProperties cache) { this.cache = cache; }
    
    /**
     * Verifica si una palabra está en la lista de palabras reservadas
     */
    public boolean isReservedWord(String word) {
        if (word == null || word.trim().isEmpty()) {
            return false;
        }
        return reservedWords.contains(word.toLowerCase().trim());
    }
    
    /**
     * Obtiene el máximo número de caracteres disponibles para sufijos numéricos
     */
    public int getMaxSuffixLength() {
        // Reservar espacio para sufijos numéricos (hasta 3 dígitos: 999)
        return Math.max(0, maxLength - 3);
    }
    
    /**
     * Verifica si la longitud está dentro de los límites permitidos
     */
    public boolean isValidLength(int length) {
        return length >= minLength && length <= maxLength;
    }
    
    /**
     * Propiedades de configuración para caché de usernames
     */
    public static class CacheProperties {
        private boolean enabled;
        private int expirationMinutes;
        private int maxSize;
        private String keyPrefix;
        
        public CacheProperties() {
            // Valores por defecto
            this.enabled = true;
            this.expirationMinutes = 60;
            this.maxSize = 10000;
            this.keyPrefix = "username:exists:";
        }
        
        public CacheProperties(ConfigService configService) {
            this.enabled = configService.getBoolean("username.generation.cache.enabled", true);
            this.expirationMinutes = configService.getInt("username.generation.cache.expirationMinutes", 60);
            this.maxSize = configService.getInt("username.generation.cache.maxSize", 10000);
            this.keyPrefix = configService.getString("username.generation.cache.keyPrefix", "username:exists:");
            
            validateCacheConfiguration();
        }
        
        /**
         * Valida la configuración del caché
         */
        private void validateCacheConfiguration() {
            if (expirationMinutes < 1) {
                throw new IllegalArgumentException("username.generation.cache.expirationMinutes debe ser mayor que 0, valor actual: " + expirationMinutes);
            }
            
            if (expirationMinutes > 1440) { // 24 horas
                throw new IllegalArgumentException("username.generation.cache.expirationMinutes no puede ser mayor que 1440 (24 horas), valor actual: " + expirationMinutes);
            }
            
            if (maxSize < 100) {
                throw new IllegalArgumentException("username.generation.cache.maxSize debe ser al menos 100, valor actual: " + maxSize);
            }
            
            if (maxSize > 100000) {
                throw new IllegalArgumentException("username.generation.cache.maxSize no puede ser mayor que 100000, valor actual: " + maxSize);
            }
            
            if (keyPrefix == null || keyPrefix.trim().isEmpty()) {
                this.keyPrefix = "username:exists:";
            }
        }
        
        // Getters y setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getExpirationMinutes() { return expirationMinutes; }
        public void setExpirationMinutes(int expirationMinutes) { 
            this.expirationMinutes = expirationMinutes;
            validateCacheConfiguration();
        }
        
        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { 
            this.maxSize = maxSize;
            validateCacheConfiguration();
        }
        
        public String getKeyPrefix() { return keyPrefix; }
        public void setKeyPrefix(String keyPrefix) { 
            this.keyPrefix = keyPrefix != null && !keyPrefix.trim().isEmpty() ? keyPrefix : "username:exists:";
        }
        
        /**
         * Obtiene la expiración del caché en segundos
         */
        public int getExpirationSeconds() {
            return expirationMinutes * 60;
        }
        
        /**
         * Obtiene la expiración del caché en milisegundos
         */
        public long getExpirationMillis() {
            return expirationMinutes * 60L * 1000L;
        }
        
        /**
         * Genera la clave de caché para un username
         */
        public String generateCacheKey(String username) {
            if (username == null || username.trim().isEmpty()) {
                throw new IllegalArgumentException("Username no puede ser null o vacío para generar clave de caché");
            }
            return keyPrefix + username.toLowerCase().trim();
        }
        
        @Override
        public String toString() {
            return String.format("CacheProperties{enabled=%s, expirationMinutes=%d, maxSize=%d, keyPrefix='%s'}", 
                               enabled, expirationMinutes, maxSize, keyPrefix);
        }
    }
    
    @Override
    public String toString() {
        return String.format("UsernameGenerationProperties{maxLength=%d, minLength=%d, maxCollisionAttempts=%d, reservedWords=%d words, cache=%s}", 
                           maxLength, minLength, maxCollisionAttempts, reservedWords.size(), cache);
    }
}
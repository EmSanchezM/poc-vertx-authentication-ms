package com.auth.microservice.infrastructure.config;

/**
 * Propiedades de configuración para la base de datos PostgreSQL
 */
public class DatabaseProperties {
    
    private String host = "localhost";
    private int port = 5432;
    private String database = "auth_microservice";
    private String username = "auth_user";
    private String password = "auth_password";
    
    // Configuración del pool de conexiones
    private int maxPoolSize = 20;
    private int maxWaitQueueSize = 50;
    private int connectionTimeout = 30000; // 30 segundos
    private int idleTimeout = 600000; // 10 minutos
    
    // Configuración de reconexión
    private int reconnectAttempts = 3;
    private long reconnectInterval = 1000; // 1 segundo
    
    // Configuración de health check
    private int healthCheckInterval = 30000; // 30 segundos
    
    public DatabaseProperties() {}
    
    public DatabaseProperties(String host, int port, String database, String username, String password) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
    }
    
    // Getters y setters
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    
    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public int getMaxPoolSize() { return maxPoolSize; }
    public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
    
    public int getMaxWaitQueueSize() { return maxWaitQueueSize; }
    public void setMaxWaitQueueSize(int maxWaitQueueSize) { this.maxWaitQueueSize = maxWaitQueueSize; }
    
    public int getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(int connectionTimeout) { this.connectionTimeout = connectionTimeout; }
    
    public int getIdleTimeout() { return idleTimeout; }
    public void setIdleTimeout(int idleTimeout) { this.idleTimeout = idleTimeout; }
    
    public int getReconnectAttempts() { return reconnectAttempts; }
    public void setReconnectAttempts(int reconnectAttempts) { this.reconnectAttempts = reconnectAttempts; }
    
    public long getReconnectInterval() { return reconnectInterval; }
    public void setReconnectInterval(long reconnectInterval) { this.reconnectInterval = reconnectInterval; }
    
    public int getHealthCheckInterval() { return healthCheckInterval; }
    public void setHealthCheckInterval(int healthCheckInterval) { this.healthCheckInterval = healthCheckInterval; }
    
    /**
     * Crea una instancia desde variables de entorno
     */
    public static DatabaseProperties fromEnvironment() {
        DatabaseProperties props = new DatabaseProperties();
        
        // Variables de entorno con valores por defecto
        props.setHost(getEnvOrDefault("DB_HOST", "localhost"));
        props.setPort(Integer.parseInt(getEnvOrDefault("DB_PORT", "5432")));
        props.setDatabase(getEnvOrDefault("DB_NAME", "auth_microservice"));
        props.setUsername(getEnvOrDefault("DB_USERNAME", "auth_user"));
        props.setPassword(getEnvOrDefault("DB_PASSWORD", "auth_password"));
        
        // Configuración del pool
        props.setMaxPoolSize(Integer.parseInt(getEnvOrDefault("DB_POOL_MAX_SIZE", "20")));
        props.setMaxWaitQueueSize(Integer.parseInt(getEnvOrDefault("DB_POOL_MAX_WAIT_QUEUE", "50")));
        props.setConnectionTimeout(Integer.parseInt(getEnvOrDefault("DB_CONNECTION_TIMEOUT", "30000")));
        props.setIdleTimeout(Integer.parseInt(getEnvOrDefault("DB_IDLE_TIMEOUT", "600000")));
        
        // Configuración de reconexión
        props.setReconnectAttempts(Integer.parseInt(getEnvOrDefault("DB_RECONNECT_ATTEMPTS", "3")));
        props.setReconnectInterval(Long.parseLong(getEnvOrDefault("DB_RECONNECT_INTERVAL", "1000")));
        
        return props;
    }
    
    private static String getEnvOrDefault(String envVar, String defaultValue) {
        String value = System.getenv(envVar);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Construye la URL JDBC para Flyway
     */
    public String getJdbcUrl() {
        return String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
    }
    
    @Override
    public String toString() {
        return String.format("DatabaseProperties{host='%s', port=%d, database='%s', username='%s', maxPoolSize=%d}", 
                           host, port, database, username, maxPoolSize);
    }
}
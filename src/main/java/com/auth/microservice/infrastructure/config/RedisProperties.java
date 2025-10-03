package com.auth.microservice.infrastructure.config;

/**
 * Propiedades de configuración para Redis
 */
public class RedisProperties {
    
    private String host;
    private int port;
    private String password;
    private int database;
    private int connectionTimeout;
    private int commandTimeout;
    
    public RedisProperties() {}
    
    public RedisProperties(ConfigService configService) {
        this.host = configService.getString("redis.host");
        this.port = configService.getInt("redis.port");
        this.password = configService.getString("redis.password", "");
        this.database = configService.getInt("redis.database", 0);
        this.connectionTimeout = configService.getInt("redis.connectionTimeout", 5000);
        this.commandTimeout = configService.getInt("redis.commandTimeout", 3000);
    }
    
    // Getters y setters
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public int getDatabase() { return database; }
    public void setDatabase(int database) { this.database = database; }
    
    public int getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(int connectionTimeout) { this.connectionTimeout = connectionTimeout; }
    
    public int getCommandTimeout() { return commandTimeout; }
    public void setCommandTimeout(int commandTimeout) { this.commandTimeout = commandTimeout; }
    
    public boolean hasPassword() {
        return password != null && !password.trim().isEmpty();
    }
    
    @Override
    public String toString() {
        return String.format("RedisProperties{host='%s', port=%d, database=%d, hasPassword=%s}", 
                           host, port, database, hasPassword());
    }
}
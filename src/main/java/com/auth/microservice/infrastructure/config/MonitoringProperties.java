package com.auth.microservice.infrastructure.config;

/**
 * Propiedades de configuración para monitoreo y observabilidad
 */
public class MonitoringProperties {
    
    private MetricsProperties metrics;
    private HealthCheckProperties healthCheck;
    private LoggingProperties logging;
    
    public MonitoringProperties() {}
    
    public MonitoringProperties(ConfigService configService) {
        this.metrics = new MetricsProperties(configService);
        this.healthCheck = new HealthCheckProperties(configService);
        this.logging = new LoggingProperties(configService);
    }
    
    // Getters y setters
    public MetricsProperties getMetrics() { return metrics; }
    public void setMetrics(MetricsProperties metrics) { this.metrics = metrics; }
    
    public HealthCheckProperties getHealthCheck() { return healthCheck; }
    public void setHealthCheck(HealthCheckProperties healthCheck) { this.healthCheck = healthCheck; }
    
    public LoggingProperties getLogging() { return logging; }
    public void setLogging(LoggingProperties logging) { this.logging = logging; }
    
    /**
     * Propiedades de métricas
     */
    public static class MetricsProperties {
        private boolean enabled;
        private int port;
        
        public MetricsProperties() {}
        
        public MetricsProperties(ConfigService configService) {
            this.enabled = configService.getBoolean("monitoring.metrics.enabled");
            this.port = configService.getInt("monitoring.metrics.port");
        }
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        
        @Override
        public String toString() {
            return String.format("MetricsProperties{enabled=%s, port=%d}", enabled, port);
        }
    }
    
    /**
     * Propiedades de health check
     */
    public static class HealthCheckProperties {
        private boolean enabled;
        private int port;
        
        public HealthCheckProperties() {}
        
        public HealthCheckProperties(ConfigService configService) {
            this.enabled = configService.getBoolean("monitoring.healthCheck.enabled");
            this.port = configService.getInt("monitoring.healthCheck.port");
        }
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        
        @Override
        public String toString() {
            return String.format("HealthCheckProperties{enabled=%s, port=%d}", enabled, port);
        }
    }
    
    /**
     * Propiedades de logging
     */
    public static class LoggingProperties {
        private String level;
        private String format;
        private FileProperties file;
        
        public LoggingProperties() {}
        
        public LoggingProperties(ConfigService configService) {
            this.level = configService.getString("logging.level");
            this.format = configService.getString("logging.format");
            this.file = new FileProperties(configService);
        }
        
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        
        public FileProperties getFile() { return file; }
        public void setFile(FileProperties file) { this.file = file; }
        
        public boolean isJsonFormat() {
            return "json".equalsIgnoreCase(format);
        }
        
        public boolean isPrettyFormat() {
            return "pretty".equalsIgnoreCase(format);
        }
        
        /**
         * Propiedades de archivo de log
         */
        public static class FileProperties {
            private boolean enabled;
            private String path;
            private String maxSize;
            private int maxFiles;
            
            public FileProperties() {}
            
            public FileProperties(ConfigService configService) {
                this.enabled = configService.getBoolean("logging.file.enabled");
                this.path = configService.getString("logging.file.path");
                this.maxSize = configService.getString("logging.file.maxSize");
                this.maxFiles = configService.getInt("logging.file.maxFiles");
            }
            
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            
            public String getPath() { return path; }
            public void setPath(String path) { this.path = path; }
            
            public String getMaxSize() { return maxSize; }
            public void setMaxSize(String maxSize) { this.maxSize = maxSize; }
            
            public int getMaxFiles() { return maxFiles; }
            public void setMaxFiles(int maxFiles) { this.maxFiles = maxFiles; }
            
            @Override
            public String toString() {
                return String.format("FileProperties{enabled=%s, path='%s', maxSize='%s', maxFiles=%d}", 
                                   enabled, path, maxSize, maxFiles);
            }
        }
        
        @Override
        public String toString() {
            return String.format("LoggingProperties{level='%s', format='%s', file=%s}", 
                               level, format, file);
        }
    }
    
    @Override
    public String toString() {
        return String.format("MonitoringProperties{metrics=%s, healthCheck=%s, logging=%s}", 
                           metrics, healthCheck, logging);
    }
}
package com.auth.microservice.infrastructure.config;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuración de la base de datos PostgreSQL con Vert.x SQL Client
 */
public class DatabaseConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);
    
    private final Pool pool;
    
    public DatabaseConfig(Vertx vertx, DatabaseProperties properties) {
        this.pool = createConnectionPool(vertx, properties);
    }
    
    /**
     * Crea el pool de conexiones a PostgreSQL
     */
    private Pool createConnectionPool(Vertx vertx, DatabaseProperties properties) {
        PgConnectOptions connectOptions = new PgConnectOptions()
            .setPort(properties.getPort())
            .setHost(properties.getHost())
            .setDatabase(properties.getDatabase())
            .setUser(properties.getUsername())
            .setPassword(properties.getPassword())
            .setReconnectAttempts(properties.getReconnectAttempts())
            .setReconnectInterval(properties.getReconnectInterval());
        
        PoolOptions poolOptions = new PoolOptions()
            .setMaxSize(properties.getMaxPoolSize())
            .setMaxWaitQueueSize(properties.getMaxWaitQueueSize())
            .setConnectionTimeout(properties.getConnectionTimeout())
            .setIdleTimeout(properties.getIdleTimeout());
        
        logger.info("Configurando pool de conexiones PostgreSQL - Host: {}, Puerto: {}, Base de datos: {}", 
                   properties.getHost(), properties.getPort(), properties.getDatabase());
        
        return PgPool.pool(vertx, connectOptions, poolOptions);
    }
    
    /**
     * Obtiene el pool de conexiones
     */
    public Pool getPool() {
        return pool;
    }
    
    /**
     * Verifica la conectividad con la base de datos
     */
    public Future<Boolean> healthCheck() {
        return pool.query("SELECT 1")
            .execute()
            .map(rowSet -> {
                logger.debug("Health check de base de datos exitoso");
                return true;
            })
            .recover(throwable -> {
                logger.error("Health check de base de datos falló", throwable);
                return Future.succeededFuture(false);
            });
    }
    
    /**
     * Cierra el pool de conexiones
     */
    public Future<Void> close() {
        logger.info("Cerrando pool de conexiones de base de datos");
        return pool.close();
    }
    
    /**
     * Obtiene información del estado del pool
     */
    public PoolStatus getPoolStatus() {
        return new PoolStatus(
            pool.size(),
            // Nota: Vert.x no expone directamente estas métricas, 
            // se pueden implementar con métricas personalizadas
            0, // active connections - requiere implementación personalizada
            0  // idle connections - requiere implementación personalizada
        );
    }
    
    /**
     * Clase para representar el estado del pool de conexiones
     */
    public static class PoolStatus {
        private final int totalConnections;
        private final int activeConnections;
        private final int idleConnections;
        
        public PoolStatus(int totalConnections, int activeConnections, int idleConnections) {
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
            this.idleConnections = idleConnections;
        }
        
        public int getTotalConnections() { return totalConnections; }
        public int getActiveConnections() { return activeConnections; }
        public int getIdleConnections() { return idleConnections; }
        
        @Override
        public String toString() {
            return String.format("PoolStatus{total=%d, active=%d, idle=%d}", 
                               totalConnections, activeConnections, idleConnections);
        }
    }
}
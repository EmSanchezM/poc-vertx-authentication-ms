package com.auth.microservice.infrastructure.migration;

import com.auth.microservice.infrastructure.config.DatabaseProperties;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio para ejecutar migraciones de base de datos con Flyway
 */
public class FlywayMigrationService {
    
    private static final Logger logger = LoggerFactory.getLogger(FlywayMigrationService.class);
    
    private final Flyway flyway;
    private final Vertx vertx;
    
    public FlywayMigrationService(DatabaseProperties databaseProperties, Vertx vertx) {
        this.vertx = vertx;
        this.flyway = configureFlyway(databaseProperties);
    }
    
    /**
     * Configura Flyway con las propiedades de la base de datos
     */
    private Flyway configureFlyway(DatabaseProperties properties) {
        logger.info("Configurando Flyway para base de datos: {}", properties.getDatabase());
        
        return Flyway.configure()
            .dataSource(
                properties.getJdbcUrl(),
                properties.getUsername(),
                properties.getPassword()
            )
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .validateOnMigrate(true)
            .cleanDisabled(true) // Seguridad: deshabilitar clean en producción
            .load();
    }
    
    /**
     * Ejecuta las migraciones de forma asíncrona
     */
    public Future<MigrationResult> migrate() {
        return vertx.executeBlocking(promise -> {
            try {
                logger.info("Iniciando migraciones de base de datos...");
                
                // Verificar el estado actual
                MigrationInfoService infoService = flyway.info();
                MigrationInfo[] pending = infoService.pending();
                
                if (pending.length > 0) {
                    logger.info("Encontradas {} migraciones pendientes", pending.length);
                    for (MigrationInfo migration : pending) {
                        logger.info("  - {}: {}", migration.getVersion(), migration.getDescription());
                    }
                } else {
                    logger.info("No hay migraciones pendientes");
                }
                
                // Ejecutar migraciones
                int migrationsExecuted = flyway.migrate().migrationsExecuted;
                
                logger.info("Migraciones completadas exitosamente. Migraciones ejecutadas: {}", 
                           migrationsExecuted);
                
                MigrationResult result = new MigrationResult(true, migrationsExecuted, null);
                promise.complete(result);
                
            } catch (Exception e) {
                logger.error("Error ejecutando migraciones de base de datos", e);
                MigrationResult result = new MigrationResult(false, 0, e.getMessage());
                promise.complete(result);
            }
        }, false);
    }
    
    /**
     * Obtiene información sobre el estado de las migraciones
     */
    public Future<MigrationStatus> getStatus() {
        return vertx.executeBlocking(promise -> {
            try {
                MigrationInfoService infoService = flyway.info();
                MigrationInfo[] all = infoService.all();
                MigrationInfo[] pending = infoService.pending();
                MigrationInfo current = infoService.current();
                
                List<MigrationSummary> migrations = Arrays.stream(all)
                    .map(info -> new MigrationSummary(
                        info.getVersion() != null ? info.getVersion().toString() : "baseline",
                        info.getDescription(),
                        info.getState().getDisplayName(),
                        info.getInstalledOn()
                    ))
                    .collect(Collectors.toList());
                
                MigrationStatus status = new MigrationStatus(
                    migrations,
                    pending.length,
                    current != null ? current.getVersion().toString() : "none",
                    infoService.applied().length
                );
                
                promise.complete(status);
                
            } catch (Exception e) {
                logger.error("Error obteniendo estado de migraciones", e);
                promise.fail(e);
            }
        }, false);
    }
    
    /**
     * Valida las migraciones sin ejecutarlas
     */
    public Future<Boolean> validate() {
        return vertx.executeBlocking(promise -> {
            try {
                flyway.validate();
                logger.info("Validación de migraciones exitosa");
                promise.complete(true);
            } catch (Exception e) {
                logger.error("Error en validación de migraciones", e);
                promise.complete(false);
            }
        }, false);
    }
    
    /**
     * Información sobre una migración específica
     */
    public static class MigrationSummary {
        private final String version;
        private final String description;
        private final String state;
        private final java.util.Date installedOn;
        
        public MigrationSummary(String version, String description, String state, java.util.Date installedOn) {
            this.version = version;
            this.description = description;
            this.state = state;
            this.installedOn = installedOn;
        }
        
        public String getVersion() { return version; }
        public String getDescription() { return description; }
        public String getState() { return state; }
        public java.util.Date getInstalledOn() { return installedOn; }
    }
    
    /**
     * Resultado de la ejecución de migraciones
     */
    public static class MigrationResult {
        private final boolean success;
        private final int migrationsExecuted;
        private final String errorMessage;
        
        public MigrationResult(boolean success, int migrationsExecuted, String errorMessage) {
            this.success = success;
            this.migrationsExecuted = migrationsExecuted;
            this.errorMessage = errorMessage;
        }
        
        public boolean isSuccess() { return success; }
        public int getMigrationsExecuted() { return migrationsExecuted; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * Estado general de las migraciones
     */
    public static class MigrationStatus {
        private final List<MigrationSummary> migrations;
        private final int pendingCount;
        private final String currentVersion;
        private final int appliedCount;
        
        public MigrationStatus(List<MigrationSummary> migrations, int pendingCount, 
                             String currentVersion, int appliedCount) {
            this.migrations = migrations;
            this.pendingCount = pendingCount;
            this.currentVersion = currentVersion;
            this.appliedCount = appliedCount;
        }
        
        public List<MigrationSummary> getMigrations() { return migrations; }
        public int getPendingCount() { return pendingCount; }
        public String getCurrentVersion() { return currentVersion; }
        public int getAppliedCount() { return appliedCount; }
        
        public boolean hasPendingMigrations() {
            return pendingCount > 0;
        }
    }
}
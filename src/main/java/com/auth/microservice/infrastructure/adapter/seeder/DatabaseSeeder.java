package com.auth.microservice.infrastructure.adapter.seeder;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.sqlclient.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Database seeder for inserting initial data
 */
public class DatabaseSeeder {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseSeeder.class);
    
    private final Pool pool;
    private final Vertx vertx;
    
    // Orden de ejecución de los seeds
    private static final List<String> SEED_FILES = Arrays.asList(
        "01_permissions.sql",
        "02_roles.sql", 
        "03_role_permissions.sql",
        "04_admin_user.sql",
        "05_test_users.sql"
    );
    
    // Seeds básicos (sin usuarios)
    private static final List<String> BASIC_SEED_FILES = Arrays.asList(
        "01_permissions.sql",
        "02_roles.sql",
        "03_role_permissions.sql"
    );
    
    public DatabaseSeeder(Pool pool, Vertx vertx) {
        this.pool = pool;
        this.vertx = vertx;
    }
    
    /**
     * Ejecuta todos los archivos de seed en orden
     */
    public Future<Void> seedDatabase() {
        logger.info("Iniciando proceso de seed de la base de datos...");
        
        return executeSeedFiles()
            .onSuccess(v -> logger.info("Proceso de seed completado exitosamente"))
            .onFailure(error -> logger.error("Error durante el proceso de seed", error));
    }
    
    /**
     * Ejecuta solo los seeds básicos (permisos y roles, sin usuarios)
     */
    public Future<Void> seedBasicData() {
        logger.info("Iniciando seed de datos básicos (permisos y roles)...");
        
        return executeSeedFiles(BASIC_SEED_FILES)
            .onSuccess(v -> logger.info("Seed de datos básicos completado"))
            .onFailure(error -> logger.error("Error durante seed de datos básicos", error));
    }
    
    /**
     * Ejecuta solo los seeds de usuarios de prueba
     */
    public Future<Void> seedTestUsers() {
        logger.info("Iniciando seed de usuarios de prueba...");
        
        List<String> testSeeds = Arrays.asList("05_test_users.sql");
        
        return executeSeedFiles(testSeeds)
            .onSuccess(v -> logger.info("Seed de usuarios de prueba completado"))
            .onFailure(error -> logger.error("Error durante seed de usuarios de prueba", error));
    }
    
    private Future<Void> executeSeedFiles() {
        return executeSeedFiles(SEED_FILES);
    }
    
    private Future<Void> executeSeedFiles(List<String> seedFiles) {
        Future<Void> future = Future.succeededFuture();
        
        for (String seedFile : seedFiles) {
            future = future.compose(v -> executeSeedFile(seedFile));
        }
        
        return future;
    }
    
    private Future<Void> executeSeedFile(String fileName) {
        logger.info("Ejecutando seed file: {}", fileName);
        
        String filePath = "db/seeds/" + fileName;
        
        return vertx.fileSystem().readFile(filePath)
            .compose(buffer -> {
                String sql = buffer.toString();
                return pool.query(sql).execute();
            })
            .map(rows -> {
                logger.info("Seed file {} ejecutado exitosamente. Filas afectadas: {}", 
                    fileName, rows.rowCount());
                return (Void) null;
            })
            .recover(error -> {
                logger.error("Error ejecutando seed file {}: {}", fileName, error.getMessage());
                return Future.failedFuture(error);
            });
    }
    
    /**
     * Verifica si los datos básicos ya existen (permisos y roles)
     */
    public Future<Boolean> hasBasicData() {
        String checkSql = """
            SELECT 
                (SELECT COUNT(*) FROM permissions) as permission_count,
                (SELECT COUNT(*) FROM roles) as role_count
            """;
        
        return pool.query(checkSql).execute()
            .map(rows -> {
                if (rows.size() > 0) {
                    var row = rows.iterator().next();
                    int permissionCount = row.getInteger("permission_count");
                    int roleCount = row.getInteger("role_count");
                    
                    boolean hasData = permissionCount > 0 && roleCount > 0;
                    logger.info("Verificación de datos básicos - Permisos: {}, Roles: {}, Tiene datos: {}", 
                        permissionCount, roleCount, hasData);
                    
                    return hasData;
                }
                return false;
            })
            .recover(error -> {
                logger.error("Error verificando datos básicos", error);
                return Future.succeededFuture(false);
            });
    }
    
    /**
     * Ejecuta seed automático si no existen datos básicos
     */
    public Future<Void> seedIfNeeded() {
        return hasBasicData()
            .compose(hasData -> {
                if (!hasData) {
                    logger.info("No se encontraron datos básicos, ejecutando seed automático...");
                    return seedBasicData();
                } else {
                    logger.info("Los datos básicos ya existen, omitiendo seed automático");
                    return Future.succeededFuture();
                }
            });
    }
    
    /**
     * Ejecuta un seed "fresh" - limpia datos existentes y recrea todo
     * Similar a 'php artisan db:seed --fresh' de Laravel
     */
    public Future<Void> seedFresh() {
        logger.info("Iniciando seed fresh (limpiando y recreando datos)...");
        
        return cleanExistingData()
            .compose(v -> seedDatabase())
            .onSuccess(v -> logger.info("Seed fresh completado exitosamente"))
            .onFailure(error -> logger.error("Error durante seed fresh", error));
    }
    
    /**
     * Limpia los datos existentes en orden inverso para respetar las foreign keys
     */
    private Future<Void> cleanExistingData() {
        logger.info("Limpiando datos existentes...");
        
        String cleanSql = """
            -- Limpiar en orden inverso para respetar foreign keys
            DELETE FROM user_sessions;
            DELETE FROM rate_limits;
            DELETE FROM role_permissions;
            DELETE FROM user_roles;
            DELETE FROM users;
            DELETE FROM roles;
            DELETE FROM permissions;
            
            -- Reiniciar secuencias
            ALTER SEQUENCE IF EXISTS users_id_seq RESTART WITH 1;
            ALTER SEQUENCE IF EXISTS roles_id_seq RESTART WITH 1;
            ALTER SEQUENCE IF EXISTS permissions_id_seq RESTART WITH 1;
            """;
        
        return pool.query(cleanSql).execute()
            .map(rows -> {
                logger.info("Datos existentes limpiados exitosamente");
                return (Void) null;
            })
            .recover(error -> {
                logger.warn("Advertencia limpiando datos (puede ser normal si las tablas están vacías): {}", 
                    error.getMessage());
                return Future.succeededFuture();
            });
    }
    
    /**
     * Verifica si existen usuarios de prueba
     */
    public Future<Boolean> hasTestUsers() {
        String checkSql = """
            SELECT COUNT(*) as test_user_count 
            FROM users 
            WHERE email IN ('test@example.com', 'user@example.com', 'moderator@example.com')
            """;
        
        return pool.query(checkSql).execute()
            .map(rows -> {
                if (rows.size() > 0) {
                    var row = rows.iterator().next();
                    int testUserCount = row.getInteger("test_user_count");
                    
                    boolean hasUsers = testUserCount > 0;
                    logger.info("Verificación de usuarios de prueba - Encontrados: {}, Tiene usuarios: {}", 
                        testUserCount, hasUsers);
                    
                    return hasUsers;
                }
                return false;
            })
            .recover(error -> {
                logger.error("Error verificando usuarios de prueba", error);
                return Future.succeededFuture(false);
            });
    }
}
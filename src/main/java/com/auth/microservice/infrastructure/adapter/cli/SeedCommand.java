package com.auth.microservice.infrastructure.adapter.cli;

import com.auth.microservice.infrastructure.adapter.database.DatabaseSeeder;
import com.auth.microservice.infrastructure.config.DatabaseConfig;
import com.auth.microservice.infrastructure.config.DatabaseProperties;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Comando CLI para ejecutar seeds de la base de datos
 * 
 * Uso:
 * java -jar app.jar seed [all|basic|test]
 */
public class SeedCommand {
    
    private static final Logger logger = LoggerFactory.getLogger(SeedCommand.class);
    
    public static void main(String[] args) {
        if (args.length < 2 || !"seed".equals(args[0])) {
            printUsage();
            System.exit(1);
        }
        
        String seedType = args[1];
        
        Vertx vertx = Vertx.vertx();
        
        // Configurar pool de base de datos
        DatabaseProperties dbProperties = DatabaseProperties.fromEnvironment();
        DatabaseConfig dbConfig = new DatabaseConfig(vertx, dbProperties);
        Pool pool = dbConfig.getPool();
        
        DatabaseSeeder seeder = new DatabaseSeeder(pool, vertx);
        
        switch (seedType.toLowerCase()) {
            case "all":
                logger.info("Ejecutando seed completo...");
                seeder.seedDatabase()
                    .onComplete(result -> {
                        if (result.succeeded()) {
                            logger.info("Seed completo ejecutado exitosamente");
                            System.exit(0);
                        } else {
                            logger.error("Error ejecutando seed completo", result.cause());
                            System.exit(1);
                        }
                    });
                break;
                
            case "basic":
                logger.info("Ejecutando seed básico...");
                seeder.seedBasicData()
                    .onComplete(result -> {
                        if (result.succeeded()) {
                            logger.info("Seed básico ejecutado exitosamente");
                            System.exit(0);
                        } else {
                            logger.error("Error ejecutando seed básico", result.cause());
                            System.exit(1);
                        }
                    });
                break;
                
            case "test":
                logger.info("Ejecutando seed de usuarios de prueba...");
                seeder.seedTestUsers()
                    .onComplete(result -> {
                        if (result.succeeded()) {
                            logger.info("Seed de usuarios de prueba ejecutado exitosamente");
                            System.exit(0);
                        } else {
                            logger.error("Error ejecutando seed de usuarios de prueba", result.cause());
                            System.exit(1);
                        }
                    });
                break;
                
            case "check":
                logger.info("Verificando datos existentes...");
                seeder.hasBasicData()
                    .onComplete(result -> {
                        if (result.succeeded()) {
                            boolean hasData = result.result();
                            logger.info("La base de datos {} datos básicos", 
                                hasData ? "TIENE" : "NO TIENE");
                            System.exit(0);
                        } else {
                            logger.error("Error verificando datos", result.cause());
                            System.exit(1);
                        }
                    });
                break;
                
            default:
                logger.error("Tipo de seed no válido: {}", seedType);
                printUsage();
                System.exit(1);
        }
    }
    
    private static void printUsage() {
        System.out.println("Uso: java -jar app.jar seed [TIPO]");
        System.out.println();
        System.out.println("Tipos de seed disponibles:");
        System.out.println("  all    - Ejecuta todos los seeds (permisos, roles, usuarios)");
        System.out.println("  basic  - Ejecuta solo datos básicos (permisos, roles, admin)");
        System.out.println("  test   - Ejecuta solo usuarios de prueba");
        System.out.println("  check  - Verifica si existen datos básicos");
        System.out.println();
        System.out.println("Ejemplos:");
        System.out.println("  java -jar app.jar seed all");
        System.out.println("  java -jar app.jar seed basic");
        System.out.println("  java -jar app.jar seed test");
        System.out.println("  java -jar app.jar seed check");
    }
}
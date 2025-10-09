package com.auth.microservice.infrastructure.adapter.cli;

import com.auth.microservice.infrastructure.adapter.seeder.DatabaseSeeder;
import com.auth.microservice.infrastructure.config.ConfigFactory;
import com.auth.microservice.infrastructure.config.DatabaseConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Comando CLI para ejecutar seeds de la base de datos
 * Comando independiente que no inicia el servidor principal
 * 
 * Uso:
 * java -jar app.jar seed [all|basic|test|check|fresh]
 * java -cp app.jar com.auth.microservice.infrastructure.adapter.cli.SeedCommand [tipo]
 */
public class SeedCommand {
    
    private static final Logger logger = LoggerFactory.getLogger(SeedCommand.class);
    
    private final Vertx vertx;
    private final ConfigFactory configFactory;
    private Pool databasePool;
    
    public SeedCommand() {
        this.vertx = Vertx.vertx();
        this.configFactory = new ConfigFactory();
    }
    
    /**
     * Punto de entrada principal para el comando
     */
    public static void main(String[] args) {
        // Permitir llamada directa o como subcomando
        String seedType;
        
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        } else if (args.length == 1) {
            // Llamada directa: java SeedCommand basic
            seedType = args[0];
        } else if (args.length == 2 && "seed".equals(args[0])) {
            // Como subcomando: java -jar app.jar seed basic
            seedType = args[1];
        } else {
            printUsage();
            System.exit(1);
            return;
        }
        
        logger.info("=== EJECUTANDO SEED COMMAND ===");
        logger.info("Tipo de seed: {}", seedType);
        
        SeedCommand command = new SeedCommand();
        
        command.execute(seedType)
            .onComplete(result -> {
                command.cleanup();
                
                if (result.succeeded()) {
                    logger.info("=== SEED COMPLETADO EXITOSAMENTE ===");
                    System.exit(0);
                } else {
                    logger.error("=== ERROR EN SEED ===", result.cause());
                    System.exit(1);
                }
            });
    }
    
    /**
     * Ejecuta el seed del tipo especificado
     */
    public Future<Void> execute(String seedType) {
        return initializeDatabase()
            .compose(v -> executeSeedByType(seedType))
            .onFailure(error -> logger.error("Error durante la ejecución del seed", error));
    }
    
    private Future<Void> initializeDatabase() {
        logger.info("Inicializando conexión a base de datos...");
        
        return configFactory.initialize()
            .compose(v -> {
                var dbConfig = configFactory.getDatabaseConfig();
                this.databasePool = DatabaseConfig.createPool(vertx, dbConfig);
                logger.info("Conexión a base de datos establecida");
                return Future.succeededFuture();
            });
    }
    
    private Future<Void> executeSeedByType(String seedType) {
        DatabaseSeeder seeder = new DatabaseSeeder(databasePool, vertx);
        
        return switch (seedType.toLowerCase()) {
            case "all" -> {
                logger.info("Ejecutando seed completo...");
                yield seeder.seedDatabase();
            }
            case "basic" -> {
                logger.info("Ejecutando seed básico...");
                yield seeder.seedBasicData();
            }
            case "test" -> {
                logger.info("Ejecutando seed de usuarios de prueba...");
                yield seeder.seedTestUsers();
            }
            case "check" -> {
                logger.info("Verificando datos existentes...");
                yield seeder.hasBasicData()
                    .map(hasData -> {
                        if (hasData) {
                            logger.info("✓ La base de datos TIENE datos básicos");
                        } else {
                            logger.info("✗ La base de datos NO TIENE datos básicos");
                        }
                        return (Void) null;
                    });
            }
            case "fresh" -> {
                logger.info("Ejecutando seed fresh (limpia y recrea datos)...");
                yield seeder.seedFresh();
            }
            default -> {
                String error = "Tipo de seed no válido: " + seedType;
                logger.error(error);
                yield Future.failedFuture(new IllegalArgumentException(error));
            }
        };
    }
    
    private void cleanup() {
        logger.info("Cerrando recursos...");
        
        if (databasePool != null) {
            databasePool.close();
        }
        
        if (vertx != null) {
            vertx.close();
        }
    }
    
    private static void printUsage() {
        System.out.println("=== AUTH MICROSERVICE - SEED COMMAND ===");
        System.out.println();
        System.out.println("Uso: java -jar app.jar seed [TIPO]");
        System.out.println("  o: java -cp app.jar com.auth.microservice.infrastructure.adapter.cli.SeedCommand [TIPO]");
        System.out.println();
        System.out.println("Tipos de seed disponibles:");
        System.out.println("  all    - Ejecuta todos los seeds (permisos, roles, usuarios)");
        System.out.println("  basic  - Ejecuta solo datos básicos (permisos, roles, admin)");
        System.out.println("  test   - Ejecuta solo usuarios de prueba");
        System.out.println("  check  - Verifica si existen datos básicos");
        System.out.println("  fresh  - Limpia y recrea todos los datos (¡CUIDADO!)");
        System.out.println();
        System.out.println("Ejemplos:");
        System.out.println("  java -jar app.jar seed all");
        System.out.println("  java -jar app.jar seed basic");
        System.out.println("  java -jar app.jar seed test");
        System.out.println("  java -jar app.jar seed check");
        System.out.println("  java -jar app.jar seed fresh");
        System.out.println();
        System.out.println("Nota: Este comando NO inicia el servidor, solo ejecuta los seeds.");
    }
}
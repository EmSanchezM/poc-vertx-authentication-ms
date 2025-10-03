package com.auth.microservice;

import com.auth.microservice.infrastructure.config.ApplicationBootstrap;
import com.auth.microservice.infrastructure.config.ApplicationProperties;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clase principal de la aplicación Auth Microservice.
 * Utiliza el patrón de bootstrap para inicializar todos los componentes
 * de forma ordenada y configurar la infraestructura CQRS completa.
 */
public class Main {
    
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    private static ApplicationBootstrap bootstrap;
    private static HttpServer server;
    
    public static void main(String[] args) {
        logger.info("=== INICIANDO AUTH MICROSERVICE ===");
        
        Vertx vertx = Vertx.vertx();
        
        // Configurar shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Recibida señal de shutdown...");
            shutdown();
        }));
        
        try {
            // Inicializar bootstrap
            bootstrap = new ApplicationBootstrap(vertx);
            
            // Inicializar todos los componentes
            bootstrap.initialize()
                .compose(v -> startHttpServer(vertx))
                .onSuccess(v -> logStartupSuccess())
                .onFailure(throwable -> {
                    logger.error("Error durante el inicio de la aplicación", throwable);
                    shutdown();
                    System.exit(1);
                });
                
        } catch (Exception e) {
            logger.error("Error crítico durante la inicialización", e);
            shutdown();
            System.exit(1);
        }
    }
    
    /**
     * Inicia el servidor HTTP con el router configurado
     */
    private static io.vertx.core.Future<Void> startHttpServer(Vertx vertx) {
        logger.info("Iniciando servidor HTTP...");
        
        ApplicationProperties appProps = bootstrap.getConfigFactory().getApplicationProperties();
        int port = appProps.getServerPort();
        
        // Configurar router con todos los middlewares y controladores
        Router router = bootstrap.configureRouter();
        
        // Crear y configurar servidor HTTP
        server = vertx.createHttpServer();
        
        return server.requestHandler(router)
            .listen(port)
            .map(httpServer -> {
                logger.info("Servidor HTTP iniciado en puerto {}", port);
                return (Void) null;
            })
            .onFailure(throwable -> {
                logger.error("Error iniciando servidor HTTP en puerto {}", port, throwable);
            });
    }
    
    /**
     * Registra información de inicio exitoso
     */
    private static void logStartupSuccess() {
        ApplicationProperties appProps = bootstrap.getConfigFactory().getApplicationProperties();
        int port = appProps.getServerPort();
        
        logger.info("=== AUTH MICROSERVICE INICIADO EXITOSAMENTE ===");
        logger.info("Aplicación: {} v{}", appProps.getName(), appProps.getVersion());
        logger.info("Ambiente: {}", appProps.getEnvironment());
        logger.info("Puerto: {}", port);
        logger.info("");
        logger.info("Endpoints principales:");
        logger.info("  - Aplicación: http://localhost:{}/", port);
        logger.info("  - Health Check: http://localhost:{}/health", port);
        logger.info("  - Métricas: http://localhost:{}/metrics", port);
        logger.info("  - Información: http://localhost:{}/info", port);
        logger.info("");
        logger.info("API Endpoints:");
        logger.info("  - POST /api/auth/login - Autenticación de usuarios");
        logger.info("  - POST /api/auth/register - Registro de usuarios");
        logger.info("  - POST /api/auth/refresh - Renovar token");
        logger.info("  - POST /api/auth/logout - Cerrar sesión");
        logger.info("  - GET /api/users/profile - Perfil de usuario");
        logger.info("  - PUT /api/users/profile - Actualizar perfil");
        logger.info("");
        logger.info("Admin Endpoints:");
        logger.info("  - POST /api/admin/roles - Crear rol (admin:roles:create)");
        logger.info("  - PUT /api/admin/roles/{id} - Actualizar rol (admin:roles:update)");
        logger.info("  - GET /api/admin/roles - Listar roles (admin:roles:read)");
        logger.info("  - POST /api/admin/users/{id}/roles - Asignar rol (admin:users:assign-roles)");
        logger.info("  - GET /api/admin/reports - Reportes administrativos (admin:reports:read)");
        logger.info("");
        
        if (appProps.isDevelopment()) {
            logger.info("Modo desarrollo activo:");
            logger.info("  - Auto-reload: {}", appProps.getDevelopment().isAutoReload());
            logger.info("  - Mock services: {}", appProps.getDevelopment().isMockExternalServices());
        }
        
        logger.info("Monitoreo externo:");
        logger.info("  - Prometheus: http://localhost:9090");
        logger.info("  - Grafana: http://localhost:3000 (admin/admin)");
        logger.info("  - AlertManager: http://localhost:9093");
        logger.info("===============================================");
    }
    
    /**
     * Realiza el shutdown ordenado de la aplicación
     */
    private static void shutdown() {
        logger.info("Iniciando shutdown de la aplicación...");
        
        try {
            // Cerrar servidor HTTP
            if (server != null) {
                server.close()
                    .onSuccess(v -> logger.info("Servidor HTTP cerrado"))
                    .onFailure(throwable -> logger.error("Error cerrando servidor HTTP", throwable));
            }
            
            // Cerrar bootstrap y recursos
            if (bootstrap != null) {
                bootstrap.shutdown()
                    .onSuccess(v -> logger.info("Bootstrap cerrado exitosamente"))
                    .onFailure(throwable -> logger.error("Error cerrando bootstrap", throwable));
            }
            
            logger.info("Shutdown completado");
            
        } catch (Exception e) {
            logger.error("Error durante shutdown", e);
        }
    }
}
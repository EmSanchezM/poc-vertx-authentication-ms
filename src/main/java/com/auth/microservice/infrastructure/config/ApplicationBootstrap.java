package com.auth.microservice.infrastructure.config;

import com.auth.microservice.application.handler.*;
import com.auth.microservice.application.handler.GetAdminReportsQueryHandler;
import com.auth.microservice.common.cqrs.CommandHandler;
import com.auth.microservice.common.cqrs.CqrsConfiguration;
import com.auth.microservice.common.cqrs.QueryHandler;
import com.auth.microservice.domain.port.PermissionRepository;
import com.auth.microservice.domain.port.RoleRepository;
import com.auth.microservice.domain.port.SessionRepository;
import com.auth.microservice.domain.port.UserRepository;
import com.auth.microservice.domain.service.GeoLocationService;
import com.auth.microservice.domain.service.PasswordService;
import com.auth.microservice.domain.service.RateLimitService;
import com.auth.microservice.domain.service.JWTService;
import com.auth.microservice.domain.service.UsernameGenerationService;
import com.auth.microservice.domain.service.UsernameNormalizationService;
import com.auth.microservice.domain.service.UsernameCollisionService;
import com.auth.microservice.domain.service.UsernameValidationService;
import com.auth.microservice.infrastructure.adapter.cache.RedisAuthCacheService;
import com.auth.microservice.infrastructure.adapter.external.IpApiGeoLocationService;
import com.auth.microservice.infrastructure.adapter.logging.StructuredLogger;
import com.auth.microservice.infrastructure.adapter.repository.*;
import com.auth.microservice.infrastructure.adapter.rest.MonitoringController;
import com.auth.microservice.infrastructure.adapter.security.BCryptPasswordService;
import com.auth.microservice.infrastructure.adapter.security.JwtTokenService;
import com.auth.microservice.infrastructure.adapter.security.RedisRateLimitService;
import com.auth.microservice.infrastructure.adapter.security.UsernameGenerationServiceImpl;
import com.auth.microservice.infrastructure.adapter.security.UsernameNormalizationServiceImpl;
import com.auth.microservice.infrastructure.adapter.security.UsernameCollisionServiceImpl;
import com.auth.microservice.infrastructure.adapter.security.UsernameValidationServiceImpl;
import com.auth.microservice.infrastructure.adapter.logging.UsernameGenerationAuditLogger;
import com.auth.microservice.infrastructure.adapter.logging.UsernameGenerationErrorHandler;
import com.auth.microservice.infrastructure.adapter.web.AuthController;
import com.auth.microservice.infrastructure.adapter.web.UserController;
import com.auth.microservice.infrastructure.adapter.web.config.WebRouterConfiguration;
import com.auth.microservice.infrastructure.adapter.web.middleware.*;
import com.auth.microservice.infrastructure.health.HealthCheckService;
import com.auth.microservice.infrastructure.metrics.MetricsService;
import com.auth.microservice.infrastructure.migration.FlywayMigrationService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;
import io.vertx.sqlclient.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Servicio de bootstrap que inicializa todos los componentes de la aplicación
 * en el orden correcto y configura la infraestructura CQRS completa.
 */
public class ApplicationBootstrap {
    
    private static final Logger logger = LoggerFactory.getLogger(ApplicationBootstrap.class);
    
    private final Vertx vertx;
    private final ConfigurationFactory configFactory;
    
    // Infraestructura
    private DatabaseConfig databaseConfig;
    private Redis redisClient;
    private RedisAPI redisAPI;
    private FlywayMigrationService migrationService;
    private CqrsConfiguration cqrsConfiguration;
    
    // Servicios de dominio
    private PasswordService passwordService;
    private JWTService tokenService;
    private RateLimitService rateLimitService;
    private GeoLocationService geoLocationService;
    private StructuredLogger structuredLogger;
    private RedisAuthCacheService cacheService;
    
    // Servicios de generación de usernames
    private UsernameGenerationProperties usernameGenerationProperties;
    private UsernameNormalizationService usernameNormalizationService;
    private UsernameValidationService usernameValidationService;
    private UsernameCollisionService usernameCollisionService;
    private UsernameGenerationService usernameGenerationService;
    private UsernameGenerationAuditLogger usernameGenerationAuditLogger;
    private UsernameGenerationErrorHandler usernameGenerationErrorHandler;
    
    // Repositorios
    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private PermissionRepository permissionRepository;
    private SessionRepository sessionRepository;
    
    // Controladores
    private AuthController authController;
    private UserController userController;
    private com.auth.microservice.infrastructure.adapter.web.AdminController adminController;
    private MonitoringController monitoringController;
    private com.auth.microservice.infrastructure.adapter.web.DebugController debugController;
    
    // Monitoreo
    private MetricsService metricsService;
    private HealthCheckService healthCheckService;
    
    // Configuración web
    private WebRouterConfiguration webRouterConfiguration;
    
    public ApplicationBootstrap(Vertx vertx) {
        this.vertx = vertx;
        this.configFactory = new ConfigurationFactory();
        logger.info("ApplicationBootstrap inicializado");
    }
    
    /**
     * Inicializa todos los componentes de la aplicación
     */
    public Future<Void> initialize() {
        logger.info("=== INICIANDO BOOTSTRAP DE APLICACIÓN ===");
        
        return initializeInfrastructure()
            .compose(v -> runDatabaseMigrations())
            .compose(v -> initializeDomainServices())
            .compose(v -> initializeRepositories())
            .compose(v -> initializeUsernameGenerationServices())
            .compose(v -> initializeCqrsHandlers())
            .compose(v -> initializeControllers())
            .compose(v -> initializeMonitoring())
            .compose(v -> validateInitialization())
            .onSuccess(v -> logger.info("=== BOOTSTRAP COMPLETADO EXITOSAMENTE ==="))
            .onFailure(throwable -> logger.error("=== ERROR EN BOOTSTRAP ===", throwable));
    }
    
    /**
     * Inicializa la infraestructura básica (base de datos, Redis, etc.)
     */
    private Future<Void> initializeInfrastructure() {
        logger.info("Inicializando infraestructura...");
        
        return Future.succeededFuture()
            .compose(v -> initializeDatabase())
            .compose(v -> initializeRedis())
            .compose(v -> initializeCqrsInfrastructure())
            .onSuccess(v -> logger.info("Infraestructura inicializada"))
            .onFailure(throwable -> logger.error("Error inicializando infraestructura", throwable));
    }
    
    private Future<Void> initializeDatabase() {
        logger.info("Configurando base de datos...");
        
        DatabaseProperties dbProps = configFactory.getDatabaseProperties();
        this.databaseConfig = new DatabaseConfig(vertx, dbProps);
        this.migrationService = new FlywayMigrationService(dbProps, vertx);
        
        return databaseConfig.healthCheck()
            .compose(healthy -> {
                if (healthy) {
                    logger.info("Conexión a base de datos establecida");
                    return Future.succeededFuture();
                } else {
                    return Future.failedFuture("No se pudo conectar a la base de datos");
                }
            });
    }
    
    private Future<Void> initializeRedis() {
        logger.info("Configurando Redis...");
        
        RedisProperties redisProps = configFactory.getRedisProperties();
        RedisOptions options = new RedisOptions()
            .setConnectionString(String.format("redis://%s:%d/%d", 
                redisProps.getHost(), redisProps.getPort(), redisProps.getDatabase()));
        
        if (redisProps.hasPassword()) {
            options.setPassword(redisProps.getPassword());
        }
        
        this.redisClient = Redis.createClient(vertx, options);
        this.redisAPI = RedisAPI.api(redisClient);
        
        return redisAPI.ping(java.util.List.of())
            .map(response -> {
                logger.info("Conexión a Redis establecida");
                // Inicializar servicio de cache
                this.cacheService = new RedisAuthCacheService(redisAPI);
                logger.info("Servicio de cache Redis inicializado");
                return (Void) null;
            })
            .onFailure(throwable -> {
                logger.error("Error conectando a Redis", throwable);
            });
    }
    
    private Future<Void> initializeCqrsInfrastructure() {
        logger.info("Inicializando infraestructura CQRS...");
        this.cqrsConfiguration = new CqrsConfiguration(vertx);
        return Future.succeededFuture();
    }
    
    /**
     * Ejecuta las migraciones de base de datos
     */
    private Future<Void> runDatabaseMigrations() {
        logger.info("Ejecutando migraciones de base de datos...");
        
        return migrationService.migrate()
            .compose(result -> {
                if (result.isSuccess()) {
                    logger.info("Migraciones completadas: {} migraciones ejecutadas", 
                               result.getMigrationsExecuted());
                    return Future.succeededFuture();
                } else {
                    return Future.failedFuture("Error en migraciones: " + result.getErrorMessage());
                }
            });
    }
    
    /**
     * Inicializa los servicios de dominio
     */
    private Future<Void> initializeDomainServices() {
        logger.info("Inicializando servicios de dominio...");
        
        SecurityProperties securityProps = configFactory.getSecurityProperties();
        JwtProperties jwtProps = configFactory.getJwtProperties();
        ExternalServicesProperties externalProps = configFactory.getExternalServicesProperties();
        
        // Servicios de seguridad
        this.passwordService = new BCryptPasswordService(securityProps.getBcryptRounds());
        io.vertx.core.json.JsonObject jwtConfig = new io.vertx.core.json.JsonObject()
            .put("jwt.secret", jwtProps.getSecret())
            .put("jwt.issuer", jwtProps.getIssuer())
            .put("jwt.accessTokenExpiry", jwtProps.getAccessTokenExpiry())
            .put("jwt.refreshTokenExpiry", jwtProps.getRefreshTokenExpiry());
        this.tokenService = new JwtTokenService(jwtConfig);
        this.rateLimitService = new RedisRateLimitService(redisAPI);
        
        // Servicio de geolocalización
        if (externalProps.getGeoLocation().isEnabled()) {
            io.vertx.ext.web.client.WebClient webClient = io.vertx.ext.web.client.WebClient.create(vertx);
            this.geoLocationService = new IpApiGeoLocationService(webClient);
        } else {
            this.geoLocationService = createMockGeoLocationService();
        }
        
        // Logger estructurado
        this.structuredLogger = new StructuredLogger(geoLocationService);
        
        logger.info("Servicios de dominio inicializados");
        return Future.succeededFuture();
    }
    
    /**
     * Inicializa los servicios de generación de usernames
     */
    private Future<Void> initializeUsernameGenerationServices() {
        logger.info("Inicializando servicios de generación de usernames...");
        
        try {
            // Cargar configuración de generación de usernames
            this.usernameGenerationProperties = new UsernameGenerationProperties(configFactory.getConfigService());
            logger.info("Configuración de generación de usernames cargada: {}", usernameGenerationProperties);
            
            // Inicializar servicios auxiliares primero
            this.usernameGenerationAuditLogger = new UsernameGenerationAuditLogger();
            logger.debug("UsernameGenerationAuditLogger inicializado");
            
            this.usernameGenerationErrorHandler = new UsernameGenerationErrorHandler();
            logger.debug("UsernameGenerationErrorHandler inicializado");
            
            // Inicializar servicios en orden de dependencias
            this.usernameNormalizationService = new UsernameNormalizationServiceImpl();
            logger.debug("UsernameNormalizationService inicializado");
            
            this.usernameValidationService = new UsernameValidationServiceImpl();
            logger.debug("UsernameValidationService inicializado");
            
            this.usernameCollisionService = new UsernameCollisionServiceImpl(
                userRepository,
                usernameGenerationAuditLogger
            );
            logger.debug("UsernameCollisionService inicializado");
            
            this.usernameGenerationService = new UsernameGenerationServiceImpl(
                usernameNormalizationService,
                usernameCollisionService,
                usernameValidationService,
                usernameGenerationAuditLogger,
                usernameGenerationErrorHandler
            );
            logger.debug("UsernameGenerationService inicializado");
            
            logger.info("Servicios de generación de usernames inicializados exitosamente");
            return Future.succeededFuture();
            
        } catch (Exception e) {
            logger.error("Error inicializando servicios de generación de usernames", e);
            return Future.failedFuture("Error en configuración de generación de usernames: " + e.getMessage());
        }
    }
    
    /**
     * Inicializa los repositorios
     */
    private Future<Void> initializeRepositories() {
        logger.info("Inicializando repositorios...");
        
        Pool pool = databaseConfig.getPool();
        TransactionManager transactionManager = new VertxTransactionManager(pool);
        
        this.userRepository = new UserRepositoryImpl(pool, transactionManager);
        this.roleRepository = new RoleRepositoryImpl(pool, transactionManager);
        this.permissionRepository = new PermissionRepositoryImpl(pool, transactionManager);
        this.sessionRepository = new SessionRepositoryImpl(pool, transactionManager);
        
        logger.info("Repositorios inicializados");
        return Future.succeededFuture();
    }
    
    /**
     * Inicializa y registra todos los handlers CQRS
     */
    private Future<Void> initializeCqrsHandlers() {
        logger.info("Inicializando handlers CQRS...");
        
        // Crear EventPublisher mock para desarrollo
        com.auth.microservice.domain.port.EventPublisher eventPublisher = new com.auth.microservice.domain.port.EventPublisher() {
            @Override
            public Future<Void> publish(com.auth.microservice.domain.event.DomainEvent event) {
                logger.info("Event published: {}", event.getClass().getSimpleName());
                return Future.succeededFuture();
            }
            
            @Override
            public Future<Void> publishAll(com.auth.microservice.domain.event.DomainEvent... events) {
                for (com.auth.microservice.domain.event.DomainEvent event : events) {
                    logger.info("Event published: {}", event.getClass().getSimpleName());
                }
                return Future.succeededFuture();
            }
        };
        
        // Command Handlers - inicializados con todas las dependencias necesarias
        List<CommandHandler<?, ?>> commandHandlers = List.of(
            new AuthCommandHandler(userRepository, passwordService, tokenService),
            new RegisterUserCommandHandler(userRepository, roleRepository, passwordService, usernameGenerationService),
            new RefreshTokenCommandHandler(userRepository, sessionRepository, tokenService),
            new InvalidateSessionCommandHandler(sessionRepository, geoLocationService),
            new InvalidateAllUserSessionsCommandHandler(sessionRepository, geoLocationService),
            new CreateUserCommandHandler(userRepository, roleRepository, passwordService, eventPublisher),
            new UpdateUserCommandHandler(userRepository, eventPublisher),
            new AssignRoleCommandHandler(userRepository, roleRepository, eventPublisher),
            new CreateRoleCommandHandler(roleRepository, permissionRepository, eventPublisher),
            new UpdateRoleCommandHandler(roleRepository, eventPublisher),
            new AssignPermissionCommandHandler(roleRepository, permissionRepository, eventPublisher)
        );
        
        // Query Handlers - inicializados con cache service para optimización
        List<QueryHandler<?, ?>> queryHandlers = List.of(
            new FindUserByEmailQueryHandler(userRepository, cacheService),
            new GetUserPermissionsQueryHandler(permissionRepository, cacheService),
            new CheckPermissionQueryHandler(permissionRepository, cacheService),
            new GetUserByIdQueryHandler(userRepository, cacheService),
            new GetUsersQueryHandler(userRepository, cacheService),
            new GetUserProfileQueryHandler(userRepository, cacheService),
            new GetUserRolesQueryHandler(roleRepository, cacheService),
            new GetRolesQueryHandler(roleRepository, cacheService),
            new GetRoleByIdQueryHandler(roleRepository, cacheService),
            new GetActiveSessionsQueryHandler(sessionRepository, cacheService),
            new GetSessionByTokenQueryHandler(sessionRepository, cacheService),
            new GetAdminReportsQueryHandler(userRepository, roleRepository, sessionRepository)
        );
        
        // Registrar todos los handlers
        cqrsConfiguration.registerAllHandlers(commandHandlers, queryHandlers);
        
        logger.info("Handlers CQRS registrados: {} commands, {} queries", 
                   commandHandlers.size(), queryHandlers.size());
        return Future.succeededFuture();
    }
    
    /**
     * Inicializa los controladores
     */
    private Future<Void> initializeControllers() {
        logger.info("Inicializando controladores...");
        
        this.authController = new AuthController(
            cqrsConfiguration.getCommandBus(),
            cqrsConfiguration.getQueryBus(),
            rateLimitService
        );
        
        // Crear AuthenticationMiddleware para rutas protegidas
        AuthenticationMiddleware authMiddleware = new AuthenticationMiddleware(tokenService);
        
        this.userController = new UserController(
            cqrsConfiguration.getCommandBus(),
            cqrsConfiguration.getQueryBus(),
            authMiddleware
        );
        
        this.adminController = new com.auth.microservice.infrastructure.adapter.web.AdminController(
            cqrsConfiguration.getCommandBus(),
            cqrsConfiguration.getQueryBus(),
            authMiddleware
        );
        
        this.monitoringController = new MonitoringController(
            healthCheckService,
            metricsService,
            configFactory
        );
        
        // Inicializar DebugController después de que los servicios de monitoreo estén disponibles
        // Nota: Se inicializa aquí pero se configurará en initializeMonitoring() cuando healthCheckService esté listo
        
        logger.info("Controladores inicializados: Auth, User, Admin, Monitoring");
        return Future.succeededFuture();
    }
    
    /**
     * Inicializa los servicios de monitoreo y configuración web
     */
    private Future<Void> initializeMonitoring() {
        logger.info("Inicializando servicios de monitoreo...");
        
        this.metricsService = new MetricsService(vertx);
        this.healthCheckService = new HealthCheckService(vertx, databaseConfig, redisClient);
        
        // Inicializar DebugController ahora que tenemos todos los servicios necesarios
        this.debugController = new com.auth.microservice.infrastructure.adapter.web.DebugController(
            configFactory.getConfigService(),
            healthCheckService
        );
        
        // Inicializar configuración web
        this.webRouterConfiguration = new WebRouterConfiguration(
            vertx, tokenService, cqrsConfiguration.getCommandBus(), 
            cqrsConfiguration.getQueryBus(), rateLimitService, 
            geoLocationService, metricsService
        );
        
        logger.info("Servicios de monitoreo, DebugController y configuración web inicializados");
        return Future.succeededFuture();
    }
    
    /**
     * Valida que todos los componentes estén correctamente inicializados
     */
    private Future<Void> validateInitialization() {
        logger.info("Validando inicialización...");
        
        return healthCheckService.checkAll()
            .compose(status -> {
                if (status.isHealthy()) {
                    logger.info("Validación de inicialización exitosa");
                    return Future.succeededFuture();
                } else {
                    return Future.failedFuture("Falló la validación de salud del sistema");
                }
            });
    }
    
    /**
     * Configura el router usando WebRouterConfiguration
     */
    public Router configureRouter() {
        logger.info("Configurando router usando WebRouterConfiguration...");
        
        // Configurar router principal con middleware
        Router router = webRouterConfiguration.configureMainRouter();
        
        // Configurar rutas de controladores
        webRouterConfiguration.configureControllerRoutes(router, authController, userController, adminController, monitoringController, debugController);
        
        logger.info("Router configurado exitosamente");
        return router;
    }

    
    private GeoLocationService createMockGeoLocationService() {
        return new GeoLocationService() {
            @Override
            public Future<com.auth.microservice.domain.service.GeoLocationService.LocationInfo> getLocationByIp(String ipAddress) {
                return Future.succeededFuture(null);
            }
            
            @Override
            public Future<String> getCountryByIp(String ipAddress) {
                return Future.succeededFuture("Unknown");
            }
            
            @Override
            public Future<Boolean> isServiceHealthy() {
                return Future.succeededFuture(true);
            }
            
            @Override
            public Future<Void> clearCache() {
                return Future.succeededFuture();
            }
            
            @Override
            public Future<com.auth.microservice.domain.service.GeoLocationService.CacheStats> getCacheStats() {
                return Future.succeededFuture(new com.auth.microservice.domain.service.GeoLocationService.CacheStats(0L, 0L, 0L, 0.0, 0L));
            }
        };
    }
    
    // Getters para acceso a componentes inicializados
    
    public ConfigurationFactory getConfigFactory() {
        return configFactory;
    }
    
    public DatabaseConfig getDatabaseConfig() {
        return databaseConfig;
    }
    
    public MetricsService getMetricsService() {
        return metricsService;
    }
    
    public HealthCheckService getHealthCheckService() {
        return healthCheckService;
    }
    
    public CqrsConfiguration getCqrsConfiguration() {
        return cqrsConfiguration;
    }
    
    public UsernameGenerationProperties getUsernameGenerationProperties() {
        return usernameGenerationProperties;
    }
    
    public UsernameGenerationService getUsernameGenerationService() {
        return usernameGenerationService;
    }
    
    /**
     * Cierra todos los recursos
     */
    public Future<Void> shutdown() {
        logger.info("Iniciando shutdown de aplicación...");
        
        return Future.succeededFuture()
            .compose(v -> databaseConfig != null ? databaseConfig.close() : Future.succeededFuture())
            .compose(v -> {
                if (redisClient != null) {
                    redisClient.close();
                }
                return Future.succeededFuture();
            })
            .onSuccess(v -> logger.info("Shutdown completado"))
            .onFailure(throwable -> logger.error("Error durante shutdown", throwable))
            .mapEmpty();
    }
}
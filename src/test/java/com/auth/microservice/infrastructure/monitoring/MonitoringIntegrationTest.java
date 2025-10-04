package com.auth.microservice.infrastructure.monitoring;

import com.auth.microservice.infrastructure.health.HealthCheckService;
import com.auth.microservice.infrastructure.metrics.MetricsService;
import com.auth.microservice.infrastructure.adapter.rest.MonitoringController;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests de integración para el stack de monitoreo
 */
@ExtendWith(VertxExtension.class)
class MonitoringIntegrationTest {
    
    private static final Logger logger = LoggerFactory.getLogger(MonitoringIntegrationTest.class);
    private static final int TEST_PORT = 8888;
    
    private HttpServer server;
    private HttpClient client;
    private MetricsService metricsService;
    private HealthCheckService healthCheckService;
    private MonitoringController monitoringController;
    
    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        // Inicializar servicios de monitoreo
        metricsService = new MetricsService(vertx);
        
        // Mock health check service (sin dependencias reales)
        healthCheckService = createMockHealthCheckService(vertx);
        
        // Mock configuration factory
        com.auth.microservice.infrastructure.config.ConfigurationFactory configFactory = 
            mock(com.auth.microservice.infrastructure.config.ConfigurationFactory.class);
        
        // Crear controller de monitoreo
        monitoringController = new MonitoringController(healthCheckService, metricsService, configFactory);
        
        // Configurar router
        Router router = Router.router(vertx);
        monitoringController.configureRoutes(router);
        
        // Crear servidor HTTP
        server = vertx.createHttpServer();
        client = vertx.createHttpClient();
        
        server.requestHandler(router)
            .listen(TEST_PORT)
            .onComplete(testContext.succeedingThenComplete());
    }
    
    @AfterEach
    void tearDown(VertxTestContext testContext) {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.close().onComplete(testContext.succeedingThenComplete());
        } else {
            testContext.completeNow();
        }
    }
    
    @Test
    void shouldExposeHealthCheckEndpoint(Vertx vertx, VertxTestContext testContext) {
        client.request(io.vertx.core.http.HttpMethod.GET, TEST_PORT, "localhost", "/health")
            .compose(request -> request.send())
            .compose(response -> {
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.getHeader("Content-Type")).contains("application/json");
                return response.body();
            })
            .onComplete(testContext.succeeding(body -> {
                JsonObject healthResponse = body.toJsonObject();
                assertThat(healthResponse.getString("status")).isNotNull();
                assertThat(healthResponse.getString("timestamp")).isNotNull();
                
                logger.info("Health check response: {}", healthResponse.encodePrettily());
                testContext.completeNow();
            }));
    }
    
    @Test
    void shouldExposeReadinessEndpoint(Vertx vertx, VertxTestContext testContext) {
        client.request(io.vertx.core.http.HttpMethod.GET, TEST_PORT, "localhost", "/health/ready")
            .compose(request -> request.send())
            .compose(response -> {
                assertThat(response.statusCode()).isIn(200, 503); // Puede estar UP o DOWN
                assertThat(response.getHeader("Content-Type")).contains("application/json");
                return response.body();
            })
            .onComplete(testContext.succeeding(body -> {
                JsonObject readinessResponse = body.toJsonObject();
                assertThat(readinessResponse.getString("status")).isIn("UP", "DOWN");
                assertThat(readinessResponse.getBoolean("ready")).isNotNull();
                assertThat(readinessResponse.getString("timestamp")).isNotNull();
                
                logger.info("Readiness check response: {}", readinessResponse.encodePrettily());
                testContext.completeNow();
            }));
    }
    
    @Test
    void shouldExposeLivenessEndpoint(Vertx vertx, VertxTestContext testContext) {
        client.request(io.vertx.core.http.HttpMethod.GET, TEST_PORT, "localhost", "/health/live")
            .compose(request -> request.send())
            .compose(response -> {
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.getHeader("Content-Type")).contains("application/json");
                return response.body();
            })
            .onComplete(testContext.succeeding(body -> {
                JsonObject livenessResponse = body.toJsonObject();
                assertThat(livenessResponse.getString("status")).isEqualTo("UP");
                assertThat(livenessResponse.getBoolean("alive")).isTrue();
                assertThat(livenessResponse.getString("timestamp")).isNotNull();
                
                logger.info("Liveness check response: {}", livenessResponse.encodePrettily());
                testContext.completeNow();
            }));
    }
    
    @Test
    void shouldExposePrometheusMetrics(Vertx vertx, VertxTestContext testContext) {
        // Primero generar algunas métricas
        metricsService.recordAuthenticationSuccess();
        metricsService.recordAuthenticationFailure();
        metricsService.recordSessionCreated();
        
        client.request(io.vertx.core.http.HttpMethod.GET, TEST_PORT, "localhost", "/metrics")
            .compose(request -> request.send())
            .compose(response -> {
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.getHeader("Content-Type")).contains("text/plain");
                return response.body();
            })
            .onComplete(testContext.succeeding(body -> {
                String metricsText = body.toString();
                
                // Verificar que contiene métricas esperadas
                assertThat(metricsText).contains("auth_authentication_success_total");
                assertThat(metricsText).contains("auth_authentication_failure_total");
                assertThat(metricsText).contains("auth_session_created_total");
                assertThat(metricsText).contains("auth_session_active");
                
                logger.info("Prometheus metrics exposed successfully");
                logger.debug("Metrics content: {}", metricsText);
                testContext.completeNow();
            }));
    }
    
    @Test
    void shouldExposeMetricsStats(Vertx vertx, VertxTestContext testContext) {
        // Generar métricas de prueba
        metricsService.recordAuthenticationSuccess();
        metricsService.recordAuthenticationSuccess();
        metricsService.recordAuthenticationFailure();
        metricsService.recordSessionCreated();
        metricsService.recordRateLimitExceeded();
        
        client.request(io.vertx.core.http.HttpMethod.GET, TEST_PORT, "localhost", "/metrics/stats")
            .compose(request -> request.send())
            .compose(response -> {
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.getHeader("Content-Type")).contains("application/json");
                return response.body();
            })
            .onComplete(testContext.succeeding(body -> {
                JsonObject statsResponse = body.toJsonObject();
                
                assertThat(statsResponse.getString("timestamp")).isNotNull();
                
                JsonObject authentication = statsResponse.getJsonObject("authentication");
                assertThat(authentication.getLong("successCount")).isEqualTo(2);
                assertThat(authentication.getLong("failureCount")).isEqualTo(1);
                assertThat(authentication.getLong("totalCount")).isEqualTo(3);
                
                JsonObject sessions = statsResponse.getJsonObject("sessions");
                assertThat(sessions.getLong("createdCount")).isEqualTo(1);
                assertThat(sessions.getLong("activeCount")).isEqualTo(1);
                
                JsonObject security = statsResponse.getJsonObject("security");
                assertThat(security.getLong("rateLimitExceededCount")).isEqualTo(1);
                
                logger.info("Metrics stats response: {}", statsResponse.encodePrettily());
                testContext.completeNow();
            }));
    }
    
    @Test
    void shouldExposeInfoEndpoint(Vertx vertx, VertxTestContext testContext) {
        client.request(io.vertx.core.http.HttpMethod.GET, TEST_PORT, "localhost", "/info")
            .compose(request -> request.send())
            .compose(response -> {
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.getHeader("Content-Type")).contains("application/json");
                return response.body();
            })
            .onComplete(testContext.succeeding(body -> {
                JsonObject infoResponse = body.toJsonObject();
                
                JsonObject application = infoResponse.getJsonObject("application");
                assertThat(application.getString("name")).isEqualTo("auth-microservice");
                assertThat(application.getString("version")).isNotNull();
                assertThat(application.getString("description")).isNotNull();
                
                JsonObject system = infoResponse.getJsonObject("system");
                assertThat(system.getString("javaVersion")).isNotNull();
                assertThat(system.getString("osName")).isNotNull();
                assertThat(system.getInteger("availableProcessors")).isGreaterThan(0);
                
                JsonObject features = infoResponse.getJsonObject("features");
                assertThat(features.getBoolean("authentication")).isTrue();
                assertThat(features.getBoolean("authorization")).isTrue();
                assertThat(features.getBoolean("rbac")).isTrue();
                assertThat(features.getBoolean("metrics")).isTrue();
                assertThat(features.getBoolean("healthChecks")).isTrue();
                
                logger.info("Info response: {}", infoResponse.encodePrettily());
                testContext.completeNow();
            }));
    }
    
    @Test
    void shouldRecordMetricsCorrectly(Vertx vertx, VertxTestContext testContext) {
        // Test que las métricas se registran correctamente
        String country = "US";
        
        // Registrar métricas de autenticación
        metricsService.recordAuthenticationSuccess(country);
        metricsService.recordAuthenticationFailure(country);
        
        // Registrar métricas de sesiones
        metricsService.recordSessionCreated();
        metricsService.recordSessionInvalidated();
        
        // Registrar métricas de seguridad
        metricsService.recordRateLimitExceeded();
        metricsService.recordSuspiciousActivity(country);
        
        // Registrar métricas de cache
        metricsService.recordCacheHit();
        metricsService.recordCacheMiss();
        
        // Verificar estadísticas
        MetricsService.MetricsStats stats = metricsService.getStats();
        
        assertThat(stats.getAuthSuccessCount()).isEqualTo(1);
        assertThat(stats.getAuthFailureCount()).isEqualTo(1);
        assertThat(stats.getSessionCreatedCount()).isEqualTo(1);
        assertThat(stats.getRateLimitExceededCount()).isEqualTo(1);
        
        logger.info("Metrics recorded correctly: {}", stats);
        testContext.completeNow();
    }
    
    /**
     * Crea un mock del HealthCheckService para testing
     */
    private HealthCheckService createMockHealthCheckService(Vertx vertx) {
        return new HealthCheckService(vertx, null, null) {
            @Override
            public io.vertx.core.Future<OverallHealthStatus> checkAll() {
                JsonObject details = new JsonObject()
                    .put("status", "UP")
                    .put("timestamp", java.time.Instant.now().toString())
                    .put("components", new JsonObject()
                        .put("application", new JsonObject().put("status", "UP")));
                
                return io.vertx.core.Future.succeededFuture(
                    new OverallHealthStatus("UP", true, details, 10));
            }
            
            @Override
            public io.vertx.core.Future<Boolean> isReady() {
                return io.vertx.core.Future.succeededFuture(true);
            }
            
            @Override
            public io.vertx.core.Future<Boolean> isAlive() {
                return io.vertx.core.Future.succeededFuture(true);
            }
        };
    }
}
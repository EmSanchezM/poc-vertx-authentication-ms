package com.auth.microservice.infrastructure.adapter.rest.middleware;

import com.auth.microservice.infrastructure.metrics.MetricsService;
import io.micrometer.core.instrument.Timer;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Middleware para recolectar métricas automáticamente de todas las requests HTTP
 */
public class MetricsMiddleware implements Handler<RoutingContext> {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsMiddleware.class);
    
    private final MetricsService metricsService;
    
    public MetricsMiddleware(MetricsService metricsService) {
        this.metricsService = metricsService;
    }
    
    @Override
    public void handle(RoutingContext context) {
        String method = context.request().method().name();
        String path = context.normalizedPath();
        String endpoint = normalizeEndpoint(path);
        
        // Iniciar timer para medir duración
        Timer.Sample sample = metricsService.startApiTimer();
        
        // Registrar la request
        metricsService.recordApiRequest(method, endpoint);
        
        // Agregar handler para cuando la response termine
        context.response().endHandler(v -> {
            int statusCode = context.response().getStatusCode();
            
            // Registrar la response con su duración
            metricsService.recordApiResponse(sample, method, endpoint, statusCode);
            
            // Si es un error, registrarlo
            if (statusCode >= 400) {
                String errorType = getErrorType(statusCode);
                metricsService.recordApiError(method, endpoint, errorType);
            }
            
            logger.debug("API metrics recorded: {} {} -> {}", method, endpoint, statusCode);
        });
        
        // Manejar excepciones
        context.response().exceptionHandler(throwable -> {
            metricsService.recordApiResponse(sample, method, endpoint, 500);
            metricsService.recordApiError(method, endpoint, "internal_error");
            logger.error("API error recorded: {} {}", method, endpoint, throwable);
        });
        
        // Continuar con el siguiente handler
        context.next();
    }
    
    /**
     * Normaliza el endpoint para agrupar rutas similares
     */
    private String normalizeEndpoint(String path) {
        if (path == null) return "unknown";
        
        // Reemplazar IDs con placeholder para agrupar métricas
        String normalized = path
            .replaceAll("/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "/{id}") // UUIDs
            .replaceAll("/\\d+", "/{id}") // Números
            .replaceAll("/[a-zA-Z0-9]{20,}", "/{token}"); // Tokens largos
        
        return normalized;
    }
    
    /**
     * Determina el tipo de error basado en el código de estado
     */
    private String getErrorType(int statusCode) {
        return switch (statusCode) {
            case 400 -> "bad_request";
            case 401 -> "unauthorized";
            case 403 -> "forbidden";
            case 404 -> "not_found";
            case 409 -> "conflict";
            case 429 -> "rate_limited";
            case 500 -> "internal_error";
            case 502 -> "bad_gateway";
            case 503 -> "service_unavailable";
            case 504 -> "gateway_timeout";
            default -> statusCode >= 400 && statusCode < 500 ? "client_error" : "server_error";
        };
    }
}
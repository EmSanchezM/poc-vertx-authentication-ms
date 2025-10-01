package com.auth.microservice;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class Main {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        
        // Create HTTP server
        HttpServer server = vertx.createHttpServer();
        
        // Create router
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        
        // Health check endpoint
        router.get("/health").handler(ctx -> {
            ctx.response()
                .putHeader("content-type", "application/json")
                .end("{\"status\":\"UP\",\"service\":\"auth-microservice\"}");
        });
        
        // Root endpoint
        router.get("/").handler(ctx -> {
            ctx.response()
                .putHeader("content-type", "application/json")
                .end("{\"message\":\"Auth Microservice is running\",\"version\":\"1.0.0\"}");
        });
        
        // Start server
        int port = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));
        
        server.requestHandler(router).listen(port, result -> {
            if (result.succeeded()) {
                System.out.println("Auth Microservice started on port " + port);
            } else {
                System.err.println("Failed to start server: " + result.cause().getMessage());
                vertx.close();
            }
        });
    }
}
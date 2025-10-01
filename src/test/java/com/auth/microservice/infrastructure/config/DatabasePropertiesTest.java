package com.auth.microservice.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios para DatabaseProperties
 */
class DatabasePropertiesTest {

    private DatabaseProperties properties;

    @BeforeEach
    void setUp() {
        properties = new DatabaseProperties();
        // Limpiar variables de entorno para tests aislados
        clearEnvironmentVariables();
    }

    @AfterEach
    void tearDown() {
        clearEnvironmentVariables();
    }

    @Test
    void shouldHaveDefaultValues() {
        assertThat(properties.getHost()).isEqualTo("localhost");
        assertThat(properties.getPort()).isEqualTo(5432);
        assertThat(properties.getDatabase()).isEqualTo("auth_microservice");
        assertThat(properties.getUsername()).isEqualTo("auth_user");
        assertThat(properties.getPassword()).isEqualTo("auth_password");
        assertThat(properties.getMaxPoolSize()).isEqualTo(20);
        assertThat(properties.getMaxWaitQueueSize()).isEqualTo(50);
        assertThat(properties.getConnectionTimeout()).isEqualTo(30000);
        assertThat(properties.getIdleTimeout()).isEqualTo(600000);
        assertThat(properties.getReconnectAttempts()).isEqualTo(3);
        assertThat(properties.getReconnectInterval()).isEqualTo(1000);
        assertThat(properties.getHealthCheckInterval()).isEqualTo(30000);
    }

    @Test
    void shouldCreateWithCustomValues() {
        DatabaseProperties customProperties = new DatabaseProperties(
            "custom-host", 5433, "custom-db", "custom-user", "custom-pass"
        );

        assertThat(customProperties.getHost()).isEqualTo("custom-host");
        assertThat(customProperties.getPort()).isEqualTo(5433);
        assertThat(customProperties.getDatabase()).isEqualTo("custom-db");
        assertThat(customProperties.getUsername()).isEqualTo("custom-user");
        assertThat(customProperties.getPassword()).isEqualTo("custom-pass");
    }

    @Test
    void shouldSetAndGetAllProperties() {
        properties.setHost("test-host");
        properties.setPort(9999);
        properties.setDatabase("test-db");
        properties.setUsername("test-user");
        properties.setPassword("test-pass");
        properties.setMaxPoolSize(50);
        properties.setMaxWaitQueueSize(100);
        properties.setConnectionTimeout(60000);
        properties.setIdleTimeout(1200000);
        properties.setReconnectAttempts(5);
        properties.setReconnectInterval(2000);
        properties.setHealthCheckInterval(60000);

        assertThat(properties.getHost()).isEqualTo("test-host");
        assertThat(properties.getPort()).isEqualTo(9999);
        assertThat(properties.getDatabase()).isEqualTo("test-db");
        assertThat(properties.getUsername()).isEqualTo("test-user");
        assertThat(properties.getPassword()).isEqualTo("test-pass");
        assertThat(properties.getMaxPoolSize()).isEqualTo(50);
        assertThat(properties.getMaxWaitQueueSize()).isEqualTo(100);
        assertThat(properties.getConnectionTimeout()).isEqualTo(60000);
        assertThat(properties.getIdleTimeout()).isEqualTo(1200000);
        assertThat(properties.getReconnectAttempts()).isEqualTo(5);
        assertThat(properties.getReconnectInterval()).isEqualTo(2000);
        assertThat(properties.getHealthCheckInterval()).isEqualTo(60000);
    }

    @Test
    void shouldGenerateCorrectJdbcUrl() {
        properties.setHost("localhost");
        properties.setPort(5432);
        properties.setDatabase("test_db");

        String jdbcUrl = properties.getJdbcUrl();
        assertThat(jdbcUrl).isEqualTo("jdbc:postgresql://localhost:5432/test_db");
    }

    @Test
    void shouldGenerateCorrectJdbcUrlWithCustomValues() {
        properties.setHost("db.example.com");
        properties.setPort(5433);
        properties.setDatabase("production_db");

        String jdbcUrl = properties.getJdbcUrl();
        assertThat(jdbcUrl).isEqualTo("jdbc:postgresql://db.example.com:5433/production_db");
    }

    @Test
    void shouldCreateFromEnvironmentWithDefaults() {
        // Sin variables de entorno, debería usar valores por defecto
        DatabaseProperties envProperties = DatabaseProperties.fromEnvironment();

        assertThat(envProperties.getHost()).isEqualTo("localhost");
        assertThat(envProperties.getPort()).isEqualTo(5432);
        assertThat(envProperties.getDatabase()).isEqualTo("auth_microservice");
        assertThat(envProperties.getUsername()).isEqualTo("auth_user");
        assertThat(envProperties.getPassword()).isEqualTo("auth_password");
    }

    @Test
    void shouldHaveToStringMethod() {
        properties.setHost("test-host");
        properties.setPort(5433);
        properties.setDatabase("test-db");
        properties.setUsername("test-user");
        properties.setMaxPoolSize(25);

        String toString = properties.toString();
        
        assertThat(toString).contains("test-host");
        assertThat(toString).contains("5433");
        assertThat(toString).contains("test-db");
        assertThat(toString).contains("test-user");
        assertThat(toString).contains("25");
        // No debería contener la contraseña por seguridad
        assertThat(toString).doesNotContain("password");
    }

    @Test
    void shouldValidatePoolConfiguration() {
        // Configuración válida
        properties.setMaxPoolSize(20);
        properties.setMaxWaitQueueSize(50);
        properties.setConnectionTimeout(30000);
        properties.setIdleTimeout(600000);

        assertThat(properties.getMaxPoolSize()).isPositive();
        assertThat(properties.getMaxWaitQueueSize()).isPositive();
        assertThat(properties.getConnectionTimeout()).isPositive();
        assertThat(properties.getIdleTimeout()).isPositive();
        assertThat(properties.getMaxWaitQueueSize()).isGreaterThanOrEqualTo(properties.getMaxPoolSize());
    }

    @Test
    void shouldValidateReconnectionConfiguration() {
        properties.setReconnectAttempts(3);
        properties.setReconnectInterval(1000);

        assertThat(properties.getReconnectAttempts()).isPositive();
        assertThat(properties.getReconnectInterval()).isPositive();
    }

    private void clearEnvironmentVariables() {
        // Nota: En un entorno real, se podrían usar bibliotecas como System Rules
        // o configurar el entorno de test de manera diferente.
        // Para estos tests unitarios, asumimos que no hay variables de entorno configuradas.
    }
}
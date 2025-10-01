package com.auth.microservice.infrastructure.migration;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests para verificar que los archivos de migración existen y son válidos
 */
class MigrationFilesTest {

    @Test
    void shouldHaveAllMigrationFiles() {
        String[] expectedMigrations = {
            "V1__Create_users_table.sql",
            "V2__Create_roles_and_permissions_tables.sql", 
            "V3__Create_user_roles_and_role_permissions_tables.sql",
            "V4__Create_sessions_table.sql",
            "V5__Create_rate_limits_table.sql",
            "V6__Insert_initial_roles_and_permissions.sql"
        };
        
        for (String migrationFile : expectedMigrations) {
            InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("db/migration/" + migrationFile);
            assertThat(stream)
                .as("Migration file %s should exist", migrationFile)
                .isNotNull();
        }
    }

    @Test
    void shouldHaveValidSqlContent() throws Exception {
        String[] migrationFiles = {
            "V1__Create_users_table.sql",
            "V2__Create_roles_and_permissions_tables.sql",
            "V3__Create_user_roles_and_role_permissions_tables.sql",
            "V4__Create_sessions_table.sql",
            "V5__Create_rate_limits_table.sql",
            "V6__Insert_initial_roles_and_permissions.sql"
        };
        
        for (String migrationFile : migrationFiles) {
            InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("db/migration/" + migrationFile);
            
            assertThat(stream).isNotNull();
            
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            
            // Verificar que el contenido no está vacío
            assertThat(content.trim())
                .as("Migration file %s should not be empty", migrationFile)
                .isNotEmpty();
            
            // Verificar que contiene SQL válido básico
            assertThat(content.toUpperCase())
                .as("Migration file %s should contain SQL commands", migrationFile)
                .containsAnyOf("CREATE", "INSERT", "ALTER", "DROP");
        }
    }

    @Test
    void shouldCreateUsersTableWithRequiredColumns() throws Exception {
        InputStream stream = getClass().getClassLoader()
            .getResourceAsStream("db/migration/V1__Create_users_table.sql");
        
        String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        
        // Verificar que la tabla users tiene las columnas requeridas
        assertThat(content).contains("CREATE TABLE users");
        assertThat(content).contains("id UUID PRIMARY KEY");
        assertThat(content).contains("email VARCHAR(255) UNIQUE NOT NULL");
        assertThat(content).contains("password_hash VARCHAR(255) NOT NULL");
        assertThat(content).contains("first_name VARCHAR(100) NOT NULL");
        assertThat(content).contains("last_name VARCHAR(100) NOT NULL");
        assertThat(content).contains("is_active BOOLEAN DEFAULT true");
        assertThat(content).contains("created_at TIMESTAMP");
        assertThat(content).contains("updated_at TIMESTAMP");
    }

    @Test
    void shouldCreateRolesAndPermissionsTables() throws Exception {
        InputStream stream = getClass().getClassLoader()
            .getResourceAsStream("db/migration/V2__Create_roles_and_permissions_tables.sql");
        
        String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        
        // Verificar que se crean las tablas de roles y permisos
        assertThat(content).contains("CREATE TABLE roles");
        assertThat(content).contains("CREATE TABLE permissions");
        assertThat(content).contains("name VARCHAR(100) UNIQUE NOT NULL");
        assertThat(content).contains("resource VARCHAR(100) NOT NULL");
        assertThat(content).contains("action VARCHAR(50) NOT NULL");
    }

    @Test
    void shouldCreateRelationshipTables() throws Exception {
        InputStream stream = getClass().getClassLoader()
            .getResourceAsStream("db/migration/V3__Create_user_roles_and_role_permissions_tables.sql");
        
        String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        
        // Verificar que se crean las tablas de relación
        assertThat(content).contains("CREATE TABLE user_roles");
        assertThat(content).contains("CREATE TABLE role_permissions");
        assertThat(content).contains("CREATE VIEW user_permissions");
        assertThat(content).contains("REFERENCES users(id) ON DELETE CASCADE");
        assertThat(content).contains("REFERENCES roles(id) ON DELETE CASCADE");
    }

    @Test
    void shouldCreateSessionsTable() throws Exception {
        InputStream stream = getClass().getClassLoader()
            .getResourceAsStream("db/migration/V4__Create_sessions_table.sql");
        
        String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        
        // Verificar que se crea la tabla de sesiones
        assertThat(content).contains("CREATE TABLE sessions");
        assertThat(content).contains("access_token_hash VARCHAR(255) NOT NULL");
        assertThat(content).contains("refresh_token_hash VARCHAR(255) NOT NULL");
        assertThat(content).contains("expires_at TIMESTAMP");
        assertThat(content).contains("ip_address INET");
        assertThat(content).contains("country_code VARCHAR(2)");
    }

    @Test
    void shouldCreateRateLimitsTable() throws Exception {
        InputStream stream = getClass().getClassLoader()
            .getResourceAsStream("db/migration/V5__Create_rate_limits_table.sql");
        
        String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        
        // Verificar que se crea la tabla de rate limits
        assertThat(content).contains("CREATE TABLE rate_limits");
        assertThat(content).contains("identifier VARCHAR(255) NOT NULL");
        assertThat(content).contains("endpoint VARCHAR(100) NOT NULL");
        assertThat(content).contains("attempts INTEGER DEFAULT 1");
        assertThat(content).contains("blocked_until TIMESTAMP");
        
        // Verificar que se crean las funciones de rate limiting
        assertThat(content).contains("CREATE OR REPLACE FUNCTION is_rate_limited");
        assertThat(content).contains("CREATE OR REPLACE FUNCTION record_rate_limit_attempt");
    }

    @Test
    void shouldInsertInitialData() throws Exception {
        InputStream stream = getClass().getClassLoader()
            .getResourceAsStream("db/migration/V6__Insert_initial_roles_and_permissions.sql");
        
        String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        
        // Verificar que se insertan datos iniciales
        assertThat(content).contains("INSERT INTO permissions");
        assertThat(content).contains("INSERT INTO roles");
        assertThat(content).contains("INSERT INTO users");
        assertThat(content).contains("INSERT INTO role_permissions");
        assertThat(content).contains("INSERT INTO user_roles");
        
        // Verificar roles específicos
        assertThat(content).contains("SUPER_ADMIN");
        assertThat(content).contains("ADMIN");
        assertThat(content).contains("USER_MANAGER");
        assertThat(content).contains("USER");
        assertThat(content).contains("GUEST");
        
        // Verificar usuario administrador
        assertThat(content).contains("admin@auth-microservice.com");
    }
}
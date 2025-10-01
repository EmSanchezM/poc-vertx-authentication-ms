package com.auth.microservice.infrastructure.migration;

import com.auth.microservice.infrastructure.config.DatabaseProperties;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de integración para migraciones usando base de datos PostgreSQL directa
 * Este test se ejecuta contra la base de datos PostgreSQL del docker-compose
 */
@ExtendWith(VertxExtension.class)
class DirectMigrationTest {

    private Vertx vertx;
    private FlywayMigrationService migrationService;
    private DatabaseProperties databaseProperties;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        
        // Configuración para conectar a PostgreSQL del docker-compose
        databaseProperties = new DatabaseProperties(
            "postgres-test", // hostname del servicio en docker-compose
            5432,
            "auth_test",
            "test_user",
            "test_password"
        );
        
        migrationService = new FlywayMigrationService(databaseProperties, vertx);
    }

    @AfterEach
    void tearDown(VertxTestContext testContext) {
        if (vertx != null) {
            vertx.close().onComplete(testContext.succeedingThenComplete());
        }
    }

    @Test
    void shouldExecuteMigrationsSuccessfully(VertxTestContext testContext) {
        migrationService.migrate()
            .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                assertThat(result).isNotNull();
                assertThat(result.isSuccess()).isTrue();
                // Las migraciones pueden ser 0 si ya están aplicadas, lo cual es válido
                assertThat(result.getMigrationsExecuted()).isGreaterThanOrEqualTo(0);
                testContext.completeNow();
            })));
    }

    @Test
    void shouldCreateAllRequiredTables(VertxTestContext testContext) {
        migrationService.migrate()
            .compose(result -> vertx.executeBlocking(promise -> {
                try (Connection conn = DriverManager.getConnection(
                        databaseProperties.getJdbcUrl(),
                        databaseProperties.getUsername(),
                        databaseProperties.getPassword())) {
                    
                    Statement stmt = conn.createStatement();
                    
                    // Verificar que todas las tablas principales existen
                    String[] expectedTables = {
                        "users", "roles", "permissions", 
                        "user_roles", "role_permissions", 
                        "sessions", "rate_limits"
                    };
                    
                    for (String tableName : expectedTables) {
                        ResultSet rs = stmt.executeQuery(
                            "SELECT EXISTS (SELECT FROM information_schema.tables " +
                            "WHERE table_schema = 'public' AND table_name = '" + tableName + "')"
                        );
                        rs.next();
                        boolean tableExists = rs.getBoolean(1);
                        assertThat(tableExists)
                            .as("Table %s should exist", tableName)
                            .isTrue();
                    }
                    
                    promise.complete();
                } catch (Exception e) {
                    promise.fail(e);
                }
            }, false))
            .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    void shouldInsertInitialData(VertxTestContext testContext) {
        migrationService.migrate()
            .compose(result -> vertx.executeBlocking(promise -> {
                try (Connection conn = DriverManager.getConnection(
                        databaseProperties.getJdbcUrl(),
                        databaseProperties.getUsername(),
                        databaseProperties.getPassword())) {
                    
                    Statement stmt = conn.createStatement();
                    
                    // Verificar que se insertaron roles básicos
                    ResultSet rolesRs = stmt.executeQuery("SELECT COUNT(*) FROM roles");
                    rolesRs.next();
                    int roleCount = rolesRs.getInt(1);
                    assertThat(roleCount).isGreaterThanOrEqualTo(5); // Al menos 5 roles básicos
                    
                    // Verificar que se insertaron permisos básicos
                    ResultSet permissionsRs = stmt.executeQuery("SELECT COUNT(*) FROM permissions");
                    permissionsRs.next();
                    int permissionCount = permissionsRs.getInt(1);
                    assertThat(permissionCount).isGreaterThanOrEqualTo(20); // Al menos 20 permisos básicos
                    
                    // Verificar que se creó el usuario administrador
                    ResultSet adminRs = stmt.executeQuery(
                        "SELECT COUNT(*) FROM users WHERE email = 'admin@auth-microservice.com'"
                    );
                    adminRs.next();
                    int adminCount = adminRs.getInt(1);
                    assertThat(adminCount).isEqualTo(1);
                    
                    // Verificar que el admin tiene el rol SUPER_ADMIN
                    ResultSet adminRoleRs = stmt.executeQuery(
                        "SELECT COUNT(*) FROM user_roles ur " +
                        "JOIN users u ON ur.user_id = u.id " +
                        "JOIN roles r ON ur.role_id = r.id " +
                        "WHERE u.email = 'admin@auth-microservice.com' AND r.name = 'SUPER_ADMIN'"
                    );
                    adminRoleRs.next();
                    int adminRoleCount = adminRoleRs.getInt(1);
                    assertThat(adminRoleCount).isEqualTo(1);
                    
                    promise.complete();
                } catch (Exception e) {
                    promise.fail(e);
                }
            }, false))
            .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    void shouldValidateMigrationsSuccessfully(VertxTestContext testContext) {
        migrationService.migrate()
            .compose(result -> migrationService.validate())
            .onComplete(testContext.succeeding(isValid -> testContext.verify(() -> {
                assertThat(isValid).isTrue();
                testContext.completeNow();
            })));
    }

    @Test
    void shouldCreateUserPermissionsView(VertxTestContext testContext) {
        migrationService.migrate()
            .compose(result -> vertx.executeBlocking(promise -> {
                try (Connection conn = DriverManager.getConnection(
                        databaseProperties.getJdbcUrl(),
                        databaseProperties.getUsername(),
                        databaseProperties.getPassword())) {
                    
                    Statement stmt = conn.createStatement();
                    
                    // Verificar que la vista user_permissions existe y funciona
                    ResultSet viewRs = stmt.executeQuery(
                        "SELECT COUNT(*) FROM user_permissions " +
                        "WHERE user_id = (SELECT id FROM users WHERE email = 'admin@auth-microservice.com')"
                    );
                    viewRs.next();
                    int permissionCount = viewRs.getInt(1);
                    assertThat(permissionCount).isGreaterThan(0);
                    
                    // Verificar que la vista incluye información detallada
                    ResultSet detailRs = stmt.executeQuery(
                        "SELECT permission_name, resource, action, role_name " +
                        "FROM user_permissions " +
                        "WHERE user_id = (SELECT id FROM users WHERE email = 'admin@auth-microservice.com') " +
                        "LIMIT 1"
                    );
                    assertThat(detailRs.next()).isTrue();
                    assertThat(detailRs.getString("permission_name")).isNotNull();
                    assertThat(detailRs.getString("resource")).isNotNull();
                    assertThat(detailRs.getString("action")).isNotNull();
                    assertThat(detailRs.getString("role_name")).isNotNull();
                    
                    promise.complete();
                } catch (Exception e) {
                    promise.fail(e);
                }
            }, false))
            .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    void shouldCreateRateLimitingFunctions(VertxTestContext testContext) {
        migrationService.migrate()
            .compose(result -> vertx.executeBlocking(promise -> {
                try (Connection conn = DriverManager.getConnection(
                        databaseProperties.getJdbcUrl(),
                        databaseProperties.getUsername(),
                        databaseProperties.getPassword())) {
                    
                    Statement stmt = conn.createStatement();
                    
                    // Probar función is_rate_limited
                    ResultSet rateLimitRs = stmt.executeQuery(
                        "SELECT is_rate_limited('192.168.1.100', '/api/auth/login', 5, 15, 60)"
                    );
                    rateLimitRs.next();
                    boolean isLimited = rateLimitRs.getBoolean(1);
                    assertThat(isLimited).isFalse(); // Primera vez, no debería estar limitado
                    
                    // Probar función record_rate_limit_attempt
                    ResultSet recordRs = stmt.executeQuery(
                        "SELECT record_rate_limit_attempt('192.168.1.100', '/api/auth/login', 5, 15, 60)"
                    );
                    recordRs.next();
                    boolean shouldBlock = recordRs.getBoolean(1);
                    assertThat(shouldBlock).isFalse(); // Primer intento, no debería bloquear
                    
                    // Verificar que se creó el registro
                    ResultSet countRs = stmt.executeQuery(
                        "SELECT COUNT(*) FROM rate_limits WHERE identifier = '192.168.1.100'"
                    );
                    countRs.next();
                    int count = countRs.getInt(1);
                    assertThat(count).isEqualTo(1);
                    
                    promise.complete();
                } catch (Exception e) {
                    promise.fail(e);
                }
            }, false))
            .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    void shouldVerifySpecificRolesAndPermissions(VertxTestContext testContext) {
        migrationService.migrate()
            .compose(result -> vertx.executeBlocking(promise -> {
                try (Connection conn = DriverManager.getConnection(
                        databaseProperties.getJdbcUrl(),
                        databaseProperties.getUsername(),
                        databaseProperties.getPassword())) {
                    
                    Statement stmt = conn.createStatement();
                    
                    // Verificar roles específicos
                    String[] expectedRoles = {"SUPER_ADMIN", "ADMIN", "USER_MANAGER", "USER", "GUEST"};
                    for (String roleName : expectedRoles) {
                        ResultSet roleRs = stmt.executeQuery(
                            "SELECT COUNT(*) FROM roles WHERE name = '" + roleName + "'"
                        );
                        roleRs.next();
                        int roleCount = roleRs.getInt(1);
                        assertThat(roleCount)
                            .as("Role %s should exist", roleName)
                            .isEqualTo(1);
                    }
                    
                    // Verificar permisos específicos
                    String[] expectedPermissions = {
                        "user.read", "user.create", "user.update", "user.delete",
                        "role.read", "role.create", "auth.login", "auth.logout"
                    };
                    for (String permissionName : expectedPermissions) {
                        ResultSet permRs = stmt.executeQuery(
                            "SELECT COUNT(*) FROM permissions WHERE name = '" + permissionName + "'"
                        );
                        permRs.next();
                        int permCount = permRs.getInt(1);
                        assertThat(permCount)
                            .as("Permission %s should exist", permissionName)
                            .isEqualTo(1);
                    }
                    
                    // Verificar que SUPER_ADMIN tiene todos los permisos
                    ResultSet superAdminPermsRs = stmt.executeQuery(
                        "SELECT COUNT(*) FROM role_permissions rp " +
                        "JOIN roles r ON rp.role_id = r.id " +
                        "WHERE r.name = 'SUPER_ADMIN'"
                    );
                    superAdminPermsRs.next();
                    int superAdminPermCount = superAdminPermsRs.getInt(1);
                    
                    ResultSet totalPermsRs = stmt.executeQuery("SELECT COUNT(*) FROM permissions");
                    totalPermsRs.next();
                    int totalPermCount = totalPermsRs.getInt(1);
                    
                    assertThat(superAdminPermCount).isEqualTo(totalPermCount);
                    
                    promise.complete();
                } catch (Exception e) {
                    promise.fail(e);
                }
            }, false))
            .onComplete(testContext.succeedingThenComplete());
    }
}
# Documento de Arquitectura - Auth Microservice RBAC

## 1. Introducción

### 1.1 Propósito
Este documento describe la arquitectura del microservicio de autenticación y autorización basado en roles (RBAC), diseñado para proporcionar servicios de seguridad centralizados y escalables para aplicaciones empresariales.

### 1.2 Alcance
El sistema implementa autenticación JWT, autorización granular basada en roles, gestión de sesiones, y capacidades de monitoreo, todo desplegado en una arquitectura de contenedores.

### 1.3 Audiencia
- Arquitectos de Software
- Desarrolladores Backend
- DevOps Engineers
- Administradores de Sistemas
- Equipos de Seguridad

## 2. Visión Arquitectónica

### 2.1 Objetivos de Arquitectura

#### Objetivos de Negocio
- **Seguridad Centralizada**: Proporcionar un punto único de autenticación y autorización
- **Escalabilidad**: Soportar crecimiento horizontal y vertical
- **Flexibilidad**: Permitir configuración granular de roles y permisos
- **Integración**: Facilitar la adopción por múltiples aplicaciones cliente

#### Objetivos Técnicos
- **Alto Rendimiento**: Respuesta < 100ms para operaciones de autenticación
- **Alta Disponibilidad**: 99.9% uptime con recuperación automática
- **Observabilidad**: Monitoreo completo y trazabilidad de operaciones
- **Mantenibilidad**: Código limpio y arquitectura modular

### 2.2 Principios Arquitectónicos

1. **Separation of Concerns**: Separación clara entre capas de presentación, negocio y datos
2. **Single Responsibility**: Cada componente tiene una responsabilidad específica
3. **Dependency Inversion**: Dependencias hacia abstracciones, no implementaciones
4. **Fail Fast**: Validación temprana y manejo explícito de errores
5. **Security by Design**: Seguridad integrada desde el diseño inicial

## 3. Arquitectura del Sistema

### 3.1 Vista de Alto Nivel

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Client Apps   │    │   Load Balancer │    │   API Gateway   │
│                 │◄──►│                 │◄──►│                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                                        │
                       ┌────────────────────────────────┼────────────────────────────────┐
                       │                                │                                │
                       ▼                                ▼                                ▼
            ┌─────────────────┐              ┌─────────────────┐              ┌─────────────────┐
            │ Auth Service    │              │ Monitoring      │              │ External APIs   │
            │ (Vert.x)        │              │ (Prometheus)    │              │ (Email, Geo)    │
            └─────────────────┘              └─────────────────┘              └─────────────────┘
                       │                                │
                       ▼                                ▼
            ┌─────────────────┐              ┌─────────────────┐
            │ PostgreSQL      │              │ Redis Cache     │
            │ (Primary DB)    │              │ (Sessions)      │
            └─────────────────┘              └─────────────────┘
```

### 3.2 Patrón Arquitectónico: Hexagonal Architecture

```
                    ┌─────────────────────────────────────────┐
                    │              ADAPTERS                   │
                    │  ┌─────────┐  ┌─────────┐  ┌─────────┐ │
                    │  │   REST  │  │  JWT    │  │  Email  │ │
                    │  │   API   │  │ Handler │  │ Service │ │
                    │  └─────────┘  └─────────┘  └─────────┘ │
                    └─────────────────┬───────────────────────┘
                                      │
                    ┌─────────────────┼───────────────────────┐
                    │                 │        PORTS          │
                    │  ┌─────────┐    │    ┌─────────┐       │
                    │  │  Auth   │◄───┼───►│  User   │       │
                    │  │  Port   │    │    │  Port   │       │
                    │  └─────────┘    │    └─────────┘       │
                    └─────────────────┼───────────────────────┘
                                      │
                    ┌─────────────────┼───────────────────────┐
                    │                 │      DOMAIN           │
                    │  ┌─────────┐    │    ┌─────────┐       │
                    │  │  User   │    │    │  Role   │       │
                    │  │ Entity  │    │    │ Entity  │       │
                    │  └─────────┘    │    └─────────┘       │
                    │                 │                      │
                    │  ┌─────────┐    │    ┌─────────────┐   │
                    │  │  Auth   │    │    │Authorization│   │
                    │  │ Service │    │    │   Service   │   │
                    │  └─────────┘    │    └─────────────┘   │
                    └─────────────────┼───────────────────────┘
                                      │
                    ┌─────────────────┼───────────────────────┐
                    │              ADAPTERS                   │
                    │  ┌─────────┐  ┌─────────┐  ┌─────────┐ │
                    │  │PostgreSQL│  │  Redis  │  │ Metrics │ │
                    │  │Repository│  │ Session │  │Collector│ │
                    │  └─────────┘  └─────────┘  └─────────┘ │
                    └─────────────────────────────────────────┘
```

## 4. Decisiones Arquitectónicas

### 4.1 Tecnologías Seleccionadas

#### Framework Principal: Vert.x 4.5+
**Decisión**: Utilizar Vert.x como framework principal
**Justificación**:
- **Alto Rendimiento**: Event-loop no bloqueante, ideal para I/O intensivo
- **Escalabilidad**: Manejo eficiente de miles de conexiones concurrentes
- **Ecosistema**: Amplio conjunto de módulos (Web, Database, Redis, etc.)
- **Java 21**: Soporte completo para Virtual Threads y nuevas características

**Alternativas Consideradas**:
- Spring Boot: Más pesado, mayor overhead
- Quarkus: Menos maduro en el ecosistema
- Micronaut: Menor comunidad y documentación

#### Base de Datos: PostgreSQL 15+
**Decisión**: PostgreSQL como base de datos principal
**Justificación**:
- **ACID Compliance**: Transacciones robustas para datos críticos de seguridad
- **JSON Support**: Flexibilidad para metadatos y configuraciones
- **Performance**: Excelente rendimiento para consultas complejas RBAC
- **Extensibilidad**: Soporte para UUID, funciones personalizadas

**Alternativas Consideradas**:
- MySQL: Menor soporte para características avanzadas
- MongoDB: No relacional, complica consultas RBAC
- Oracle: Costo y complejidad innecesarios

#### Cache: Redis 7+
**Decisión**: Redis para cache y gestión de sesiones
**Justificación**:
- **Performance**: Acceso sub-milisegundo a datos de sesión
- **Persistencia**: Durabilidad configurable para sesiones críticas
- **Estructuras de Datos**: Soporte nativo para sets, hashes, expiración
- **Clustering**: Escalabilidad horizontal nativa

### 4.2 Patrones de Diseño Implementados

#### 4.2.1 Repository Pattern
```java
public interface UserRepository {
    Future<Optional<User>> findByEmail(String email);
    Future<User> save(User user);
    Future<List<User>> findByRoles(Set<String> roleNames);
}
```
**Beneficios**:
- Abstracción de la capa de datos
- Facilita testing con mocks
- Permite cambio de implementación sin afectar lógica de negocio

#### 4.2.2 Command Query Responsibility Segregation (CQRS)
```java
// Commands (Write Operations)
public class CreateUserCommand {
    private final String email;
    private final String password;
    private final Set<String> roles;
}

// Queries (Read Operations)  
public class UserQuery {
    private final String userId;
    private final boolean includeRoles;
    private final boolean includePermissions;
}
```
**Beneficios**:
- Separación clara entre operaciones de lectura y escritura
- Optimización independiente de cada tipo de operación
- Escalabilidad diferenciada

#### 4.2.3 Strategy Pattern para Autenticación
```java
public interface AuthenticationStrategy {
    Future<AuthResult> authenticate(AuthRequest request);
}

public class JWTAuthenticationStrategy implements AuthenticationStrategy
public class BasicAuthenticationStrategy implements AuthenticationStrategy
public class OAuth2AuthenticationStrategy implements AuthenticationStrategy
```

### 4.3 Decisiones de Seguridad

#### 4.3.1 JWT con RS256
**Decisión**: Utilizar JWT con algoritmo RS256
**Justificación**:
- **Asimetría**: Clave privada para firmar, pública para verificar
- **Distribución**: Servicios pueden verificar tokens sin acceso a clave privada
- **Estándar**: RFC 7519, ampliamente adoptado

#### 4.3.2 BCrypt para Passwords
**Decisión**: BCrypt con factor de trabajo 12
**Justificación**:
- **Adaptive**: Costo computacional ajustable
- **Salt Automático**: Protección contra rainbow tables
- **Probado**: Ampliamente utilizado y auditado

#### 4.3.3 Rate Limiting Multi-Nivel
```java
public class RateLimitingHandler {
    
    private final RedisAPI redisClient;
    
    public Future<Boolean> checkRateLimit(String identifier, String endpoint, int maxRequests, Duration window) {
        String key = "rate_limit:" + endpoint + ":" + identifier;
        return redisClient.incr(key)
            .compose(count -> {
                if (count == 1) {
                    return redisClient.expire(key, String.valueOf(window.getSeconds()))
                        .map(v -> count <= maxRequests);
                }
                return Future.succeededFuture(count <= maxRequests);
            });
    }
}

// Uso en el handler de login
public Future<AuthResult> login(LoginRequest request, String clientIP, String userId) {
    return rateLimitingHandler.checkRateLimit(clientIP, "login", 5, Duration.ofMinutes(1))
        .compose(allowed -> {
            if (!allowed) {
                return Future.failedFuture(new RateLimitExceededException("Too many login attempts"));
            }
            return authenticateUser(request);
        });
}
```

## 5. Arquitectura de Componentes

### 5.1 Estructura de Capas

```
src/main/java/com/auth/microservice/
├── application/                 # Capa de Aplicación
│   ├── handlers/               # HTTP Request Handlers
│   ├── dto/                    # Data Transfer Objects
│   └── mappers/                # Entity ↔ DTO Mappers
├── domain/                     # Capa de Dominio
│   ├── entities/               # Entidades de Negocio
│   ├── services/               # Servicios de Dominio
│   ├── repositories/           # Interfaces de Repositorio
│   └── exceptions/             # Excepciones de Dominio
├── infrastructure/             # Capa de Infraestructura
│   ├── persistence/            # Implementaciones de Repositorio
│   ├── security/               # JWT, Encryption, etc.
│   ├── cache/                  # Redis Implementation
│   ├── monitoring/             # Metrics, Health Checks
│   └── config/                 # Configuración
└── Main.java                   # Punto de Entrada
```

### 5.2 Componentes Principales

#### 5.2.1 Authentication Service
```java
public class AuthenticationService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTService jwtService;
    private final SessionService sessionService;
    
    public AuthenticationService(UserRepository userRepository,
                               PasswordEncoder passwordEncoder,
                               JWTService jwtService,
                               SessionService sessionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.sessionService = sessionService;
    }
    
    public Future<AuthResult> authenticate(String email, String password) {
        return userRepository.findByEmail(email)
            .compose(user -> validatePassword(user, password))
            .compose(user -> generateTokens(user))
            .compose(tokens -> createSession(user, tokens));
    }
}
```

#### 5.2.2 Authorization Service
```java
public class AuthorizationService {
    
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    
    public AuthorizationService(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }
    
    public Future<Set<Permission>> getUserPermissions(String userId) {
        return userRepository.findById(userId)
            .compose(user -> roleRepository.findByUserId(userId))
            .map(roles -> roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .collect(Collectors.toSet()));
    }
    
    public Future<Boolean> hasPermission(String userId, String resource, String action) {
        return getUserPermissions(userId)
            .map(permissions -> permissions.stream()
                .anyMatch(p -> p.getResource().equals(resource) && 
                              p.getAction().equals(action)));
    }
}
```

## 6. Requisitos No Funcionales

### 6.1 Performance
- **Latencia**: < 100ms para operaciones de autenticación
- **Throughput**: > 1000 RPS por instancia
- **Concurrencia**: Soporte para 10,000 conexiones simultáneas

### 6.2 Escalabilidad
- **Horizontal**: Stateless design permite múltiples instancias
- **Vertical**: Optimización para multi-core con Virtual Threads
- **Base de Datos**: Connection pooling y read replicas

### 6.3 Disponibilidad
- **Uptime**: 99.9% (8.76 horas downtime/año)
- **Recovery**: RTO < 5 minutos, RPO < 1 minuto
- **Health Checks**: Endpoints para verificación de estado

### 6.4 Seguridad
- **Encryption**: TLS 1.3 para datos en tránsito
- **Authentication**: Multi-factor authentication support
- **Authorization**: Granular RBAC con herencia de roles
- **Audit**: Log completo de operaciones de seguridad

### 6.5 Observabilidad
- **Logging**: Structured logging con correlación IDs
- **Metrics**: Prometheus metrics para todas las operaciones
- **Tracing**: Distributed tracing con OpenTelemetry
- **Alerting**: Alertas automáticas para anomalías

## 7. Modelo de Datos

### 7.1 Entidades Principales

```java
// Imports necesarios para todas las entidades
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.*;
```

#### User Entity
```java
public class User {
    private UUID id;
    private String email;
    private String passwordHash;
    private String firstName;
    private String lastName;
    private Boolean isActive = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Set<Role> roles = new HashSet<>();
    
    // Constructor
    public User() {
        this.id = UUID.randomUUID();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }
    
    // Business methods
    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }
    
    public boolean hasRole(String roleName) {
        return roles.stream().anyMatch(role -> role.getName().equals(roleName));
    }
}
```

#### Role Entity
```java
public class Role {
    private UUID id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private Set<Permission> permissions = new HashSet<>();
    
    // Constructor
    public Role() {
        this.id = UUID.randomUUID();
        this.createdAt = LocalDateTime.now();
    }
    
    public Role(String name, String description) {
        this();
        this.name = name;
        this.description = description;
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public Set<Permission> getPermissions() { return permissions; }
    public void setPermissions(Set<Permission> permissions) { this.permissions = permissions; }
    
    // Business methods
    public void addPermission(Permission permission) {
        this.permissions.add(permission);
    }
    
    public void removePermission(Permission permission) {
        this.permissions.remove(permission);
    }
    
    public boolean hasPermission(String resource, String action) {
        return permissions.stream()
            .anyMatch(p -> p.getResource().equals(resource) && p.getAction().equals(action));
    }
}
```

#### Permission Entity
```java
public class Permission {
    private UUID id;
    private String name;
    private String resource;
    private String action;
    private String description;
    
    // Constructor
    public Permission() {
        this.id = UUID.randomUUID();
    }
    
    public Permission(String name, String resource, String action, String description) {
        this();
        this.name = name;
        this.resource = resource;
        this.action = action;
        this.description = description;
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }
    
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    // Business methods
    public boolean matches(String resource, String action) {
        return this.resource.equals(resource) && this.action.equals(action);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Permission that = (Permission) obj;
        return Objects.equals(name, that.name) && 
               Objects.equals(resource, that.resource) && 
               Objects.equals(action, that.action);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, resource, action);
    }
}
```

### 7.2 Relaciones y Cardinalidades

```
User (1) ←→ (N) UserRole (N) ←→ (1) Role
Role (1) ←→ (N) RolePermission (N) ←→ (1) Permission
User (1) ←→ (N) Session
User (1) ←→ (N) AuditLog
```

## 8. APIs y Contratos

### 8.1 Endpoints Principales

#### Authentication Endpoints
```yaml
POST /api/v1/auth/login
POST /api/v1/auth/register  
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/forgot-password
POST /api/v1/auth/reset-password
```

#### User Management Endpoints
```yaml
GET    /api/v1/users/profile
PUT    /api/v1/users/profile
GET    /api/v1/users/{id}
PUT    /api/v1/users/{id}
DELETE /api/v1/users/{id}
GET    /api/v1/users
```

#### RBAC Endpoints
```yaml
GET    /api/v1/roles
POST   /api/v1/roles
GET    /api/v1/roles/{id}
PUT    /api/v1/roles/{id}
DELETE /api/v1/roles/{id}
POST   /api/v1/users/{id}/roles
DELETE /api/v1/users/{id}/roles/{roleId}
```

### 8.2 Formato de Respuesta Estándar

```json
{
  "success": true,
  "data": {
    "user": {
      "id": "123e4567-e89b-12d3-a456-426614174000",
      "email": "user@example.com",
      "firstName": "John",
      "lastName": "Doe",
      "roles": ["USER", "ADMIN"]
    },
    "tokens": {
      "accessToken": "eyJhbGciOiJSUzI1NiIs...",
      "refreshToken": "eyJhbGciOiJSUzI1NiIs...",
      "expiresIn": 3600
    }
  },
  "metadata": {
    "timestamp": "2024-01-15T10:30:00Z",
    "requestId": "req-123456",
    "version": "v1"
  }
}
```

## 9. Estrategia de Despliegue

### 9.1 Containerización

#### Multi-Stage Dockerfile
```dockerfile
# Build Stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN ./gradlew shadowJar --no-daemon

# Runtime Stage  
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*-fat.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]
```

### 9.2 Orquestación con Docker Compose

#### Servicios por Ambiente
- **Development**: Servicios básicos + debugging tools
- **QA**: Configuración similar a producción + monitoring
- **Production**: Configuración optimizada + alta disponibilidad

### 9.3 Variables de Entorno por Ambiente

```bash
# Development
JWT_SECRET=dev-secret-key
JWT_ACCESS_TOKEN_EXPIRY=3600
LOG_LEVEL=DEBUG
RATE_LIMIT_ENABLED=false

# Production  
JWT_SECRET=${VAULT_JWT_SECRET}
JWT_ACCESS_TOKEN_EXPIRY=900
LOG_LEVEL=WARN
RATE_LIMIT_ENABLED=true
```

## 10. Monitoreo y Observabilidad

### 10.1 Métricas Clave (KPIs)

#### Business Metrics
- **Authentication Success Rate**: > 99%
- **Average Login Time**: < 200ms
- **Active Sessions**: Trending
- **Failed Login Attempts**: < 1% of total

#### Technical Metrics
- **Response Time P95**: < 500ms
- **Error Rate**: < 0.1%
- **CPU Utilization**: < 70%
- **Memory Usage**: < 80%
- **Database Connection Pool**: < 80% utilization

### 10.2 Health Checks

```java
public class HealthCheckHandler {
    
    private final SqlClient dbClient;
    private final RedisAPI redisClient;
    
    public HealthCheckHandler(SqlClient dbClient, RedisAPI redisClient) {
        this.dbClient = dbClient;
        this.redisClient = redisClient;
    }
    
    public Future<JsonObject> healthCheck() {
        return CompositeFuture.all(
            checkDatabase(),
            checkRedis(),
            checkExternalServices()
        ).map(this::aggregateHealth);
    }
    
    private Future<HealthStatus> checkDatabase() {
        return dbClient.query("SELECT 1")
            .map(result -> HealthStatus.UP)
            .recover(error -> Future.succeededFuture(HealthStatus.DOWN));
    }
}
```

### 10.3 Alertas Críticas

```yaml
alerts:
  - name: HighErrorRate
    condition: error_rate > 1%
    duration: 5m
    severity: critical
    
  - name: HighLatency  
    condition: response_time_p95 > 1s
    duration: 2m
    severity: warning
    
  - name: DatabaseDown
    condition: database_health == "DOWN"
    duration: 30s
    severity: critical
```

## 11. Seguridad

### 11.1 Threat Model

#### Amenazas Identificadas
1. **Credential Stuffing**: Ataques automatizados con credenciales robadas
2. **JWT Token Theft**: Interceptación o robo de tokens
3. **SQL Injection**: Inyección de código malicioso
4. **Privilege Escalation**: Elevación no autorizada de permisos
5. **Session Hijacking**: Secuestro de sesiones activas

#### Mitigaciones Implementadas
1. **Rate Limiting**: Límites por IP y usuario
2. **Token Rotation**: Refresh tokens con rotación automática
3. **Prepared Statements**: Consultas parametrizadas
4. **Principle of Least Privilege**: Permisos mínimos necesarios
5. **Session Validation**: Validación continua de sesiones

### 11.2 Compliance y Auditoría

#### Audit Trail
```java
public class AuditLog {
    private UUID id;
    private String userId;
    private String action;
    private String resource;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime timestamp;
    private JsonObject details;
    
    // Constructor
    public AuditLog() {
        this.id = UUID.randomUUID();
        this.timestamp = LocalDateTime.now();
    }
    
    public AuditLog(String userId, String action, String resource, String ipAddress) {
        this();
        this.userId = userId;
        this.action = action;
        this.resource = resource;
        this.ipAddress = ipAddress;
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    
    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public JsonObject getDetails() { return details; }
    public void setDetails(JsonObject details) { this.details = details; }
}
```

#### Compliance Requirements
- **GDPR**: Right to be forgotten, data portability
- **SOX**: Audit trails, access controls
- **PCI DSS**: Secure authentication, encryption

## 12. Evolución y Roadmap

### 12.1 Versioning Strategy
- **API Versioning**: Semantic versioning (v1, v2, etc.)
- **Database Migrations**: Flyway con rollback support
- **Backward Compatibility**: Mínimo 2 versiones anteriores

### 12.2 Roadmap Técnico

#### Fase 1 (Actual): Core RBAC
- ✅ Autenticación JWT
- ✅ Autorización basada en roles
- ✅ Gestión de usuarios y roles
- ✅ Rate limiting básico

#### Fase 2 (Q2 2025): Enhanced Security
- 🔄 Multi-factor authentication
- 🔄 OAuth2/OIDC integration
- 🔄 Advanced rate limiting
- 🔄 Audit dashboard

#### Fase 3 (Q3 2025): Scalability
- 📋 Horizontal scaling
- 📋 Read replicas
- 📋 Caching optimization
- 📋 Performance tuning

#### Fase 4 (Q4 2025): Advanced Features
- 📋 Dynamic permissions
- 📋 Time-based access
- 📋 Geolocation restrictions
- 📋 AI-powered anomaly detection

### 12.3 Migration Strategy

#### Database Migrations
```sql
-- V7__add_mfa_support.sql
ALTER TABLE users ADD COLUMN mfa_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN mfa_secret VARCHAR(255);

CREATE TABLE mfa_backup_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(255) NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## 13. Conclusiones

### 13.1 Fortalezas de la Arquitectura
- **Modularidad**: Separación clara de responsabilidades
- **Escalabilidad**: Diseño stateless y horizontal scaling
- **Seguridad**: Múltiples capas de protección
- **Observabilidad**: Monitoreo completo y trazabilidad
- **Mantenibilidad**: Código limpio y bien estructurado

### 13.2 Riesgos y Mitigaciones
- **Single Point of Failure**: Mitigado con clustering y load balancing
- **Performance Bottlenecks**: Mitigado con caching y optimización de queries
- **Security Vulnerabilities**: Mitigado con auditorías regulares y updates
- **Complexity**: Mitigado con documentación y testing exhaustivo

### 13.3 Recomendaciones
1. **Implementar CI/CD**: Pipeline automatizado para despliegues
2. **Security Scanning**: Análisis automático de vulnerabilidades
3. **Load Testing**: Pruebas regulares de carga y stress
4. **Disaster Recovery**: Plan de recuperación ante desastres
5. **Team Training**: Capacitación continua del equipo

---

**Documento Versión**: 1.0  
**Fecha**: Octubre 2025 
**Autor**: Elvin Sanchez
**Revisado por**: Elvin Sanchez
**Próxima Revisión**: Diciembre 2025
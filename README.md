# Auth Microservice RBAC

Microservicio de autenticación y autorización basado en roles (RBAC) construido con Vert.x, PostgreSQL y Redis.

## 🚀 Inicio Rápido con Docker

### Prerrequisitos

- Docker 20.10+
- Docker Compose 2.0+

### Desarrollo

```bash
# Iniciar ambiente de desarrollo
make dev

# O usando docker-compose directamente
docker-compose -f docker-compose.development.yml up --build
```

### QA/Staging

```bash
# Iniciar ambiente de QA
make qa

# En segundo plano
make qa-detach
```

### Producción

```bash
# Iniciar ambiente de producción
make prod

# En segundo plano
make prod-detach
```

## 🛠️ Comandos Disponibles

### Gestión de Ambientes

```bash
make help                    # Mostrar ayuda
make dev                     # Desarrollo
make qa                      # QA/Staging
make prod                    # Producción
make status ENV=development  # Estado de servicios
make logs ENV=qa            # Ver logs
make clean ENV=production   # Limpiar ambiente
```

### Testing

```bash
make test                   # Ejecutar tests unitarios
make test-integration      # Ejecutar tests de integración
```

### Utilidades

```bash
make build ENV=development  # Construir imágenes
make health ENV=qa         # Verificar salud de servicios
make db-migrate ENV=prod   # Ejecutar migraciones
```

## 🏗️ Arquitectura

### Componentes

- **Auth Service**: Microservicio principal (Puerto 8080)
- **PostgreSQL**: Base de datos principal (Puerto 5432)
- **Redis**: Cache y sesiones (Puerto 6379)
- **Prometheus**: Métricas (Puerto 9090) - Opcional
- **Grafana**: Dashboard de monitoreo (Puerto 3000) - Opcional

### Estructura del Proyecto

```
├── src/
│   ├── main/
│   │   ├── java/com/auth/microservice/
│   │   │   ├── application/          # Capa de aplicación
│   │   │   ├── domain/              # Lógica de dominio
│   │   │   └── infrastructure/      # Infraestructura
│   │   └── resources/
│   │       ├── application*.properties
│   │       └── db/migration/        # Migraciones Flyway
│   └── test/                        # Tests
├── scripts/                         # Scripts de utilidad
├── docker-compose*.yml             # Configuraciones Docker
├── .env*                           # Variables de entorno
└── Makefile                        # Comandos simplificados
```

## ⚙️ Configuración

### Variables de Entorno

Cada ambiente tiene su archivo de configuración:

- `.env.development` - Desarrollo
- `.env.qa` - QA/Staging  
- `.env.production` - Producción

### Configuración por Ambiente

#### Desarrollo
- Base de datos: `auth_microservice_dev`
- Log level: `DEBUG`
- JWT expiry: 1 hora
- Rate limiting: Permisivo
- CORS: Abierto

#### QA
- Base de datos: `auth_microservice_qa`
- Log level: `INFO`
- JWT expiry: 30 minutos
- Rate limiting: Moderado
- CORS: Restringido a dominios QA

#### Producción
- Base de datos: `auth_microservice_prod`
- Log level: `WARN`
- JWT expiry: 15 minutos
- Rate limiting: Estricto
- CORS: Restringido a dominios de producción

## 🗄️ Base de Datos

### Migraciones

Las migraciones se ejecutan automáticamente al iniciar el servicio. Incluyen:

1. **V1**: Tabla de usuarios
2. **V2**: Tablas de roles y permisos
3. **V3**: Relaciones usuario-rol y rol-permiso
4. **V4**: Tabla de sesiones JWT
5. **V5**: Tabla de rate limiting
6. **V6**: Datos iniciales (roles, permisos, usuario admin)

### Usuario Administrador por Defecto

- **Email**: `admin@auth-microservice.com`
- **Contraseña**: `admin123`
- **Rol**: `SUPER_ADMIN`

## 🔐 Seguridad

### Características de Seguridad

- Autenticación JWT con refresh tokens
- Autorización basada en roles (RBAC)
- Rate limiting por IP y usuario
- Hash de contraseñas con BCrypt
- Gestión de sesiones con Redis
- Validación de entrada
- Headers de seguridad

### Roles por Defecto

- `SUPER_ADMIN`: Acceso completo
- `ADMIN`: Gestión de usuarios y roles
- `USER_MANAGER`: Gestión limitada de usuarios
- `USER`: Permisos básicos
- `GUEST`: Solo lectura

## 📊 Monitoreo

### Health Checks

```bash
curl http://localhost:8080/health
```

### Métricas

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000` (admin/admin)

### Logs

```bash
# Ver logs en tiempo real
make logs ENV=development

# Solo logs de la aplicación
make logs-app ENV=qa
```

## 🧪 Testing

### Tests Unitarios

```bash
make test
```

### Tests de Integración

```bash
make test-integration
```

Los tests incluyen:
- Configuración de base de datos
- Validación de migraciones
- Funcionalidad de archivos de migración
- Health checks

## 🚀 Despliegue

### Desarrollo Local

```bash
make dev
```

### QA/Staging

```bash
make qa-detach
```

### Producción

1. Configurar variables de entorno en `.env.production`
2. Configurar secretos y certificados SSL
3. Ejecutar:

```bash
make prod-detach
```

## 🔧 Troubleshooting

### Problemas Comunes

1. **Puerto ocupado**: Cambiar puertos en docker-compose
2. **Permisos de Docker**: Agregar usuario al grupo docker
3. **Memoria insuficiente**: Aumentar límites en docker-compose
4. **Base de datos no conecta**: Verificar health checks

### Logs de Debug

```bash
# Ver logs detallados
docker-compose -f docker-compose.yml -f docker-compose.development.yml logs -f auth-service

# Entrar al contenedor
docker exec -it authentication-ms sh
```

## 📝 API Documentation

Una vez iniciado el servicio, la documentación de la API estará disponible en:

- Swagger UI: `http://localhost:8080/swagger-ui`
- OpenAPI Spec: `http://localhost:8080/api-docs`

## 🤝 Contribución

1. Fork el proyecto
2. Crear rama feature (`git checkout -b feature/nueva-funcionalidad`)
3. Commit cambios (`git commit -am 'Agregar nueva funcionalidad'`)
4. Push a la rama (`git push origin feature/nueva-funcionalidad`)
5. Crear Pull Request

## 📄 Licencia

Este proyecto está bajo la Licencia MIT - ver el archivo [LICENSE](LICENSE) para detalles.

## 🆘 Soporte

Para soporte y preguntas:

- Crear un issue en GitHub
- Revisar la documentación en `/docs`
- Consultar los logs con `make logs`
## 🌱 Da
tabase Seeds

Los seeds se han separado de las migraciones para mantener una clara separación entre cambios de esquema y datos iniciales.

### Estructura de Seeds

```
src/main/resources/db/seeds/
├── 01_permissions.sql      # Permisos del sistema
├── 02_roles.sql           # Roles básicos
├── 03_role_permissions.sql # Asignación de permisos a roles
├── 04_admin_user.sql      # Usuario administrador
└── 05_test_users.sql      # Usuarios de prueba
```

### Comandos de Seed

#### Usando Scripts (Recomendado)

```bash
# Linux/Mac
./scripts/seed.sh [all|basic|test|check]

# Windows
.\scripts\seed.ps1 [all|basic|test|check]

# Docker
./scripts/docker-seed.sh [all|basic|test|check]
```

#### Usando Java directamente

```bash
java -jar build/libs/auth-microservice-1.0.0-fat.jar seed [TIPO]
```

### Tipos de Seed

- **`all`** - Ejecuta todos los seeds (permisos, roles, usuarios)
- **`basic`** - Ejecuta solo datos básicos (permisos, roles, admin)
- **`test`** - Ejecuta solo usuarios de prueba
- **`check`** - Verifica si existen datos básicos

### Ejemplos

```bash
# Seed completo (primera vez)
./scripts/seed.sh all

# Solo datos básicos
./scripts/seed.sh basic

# Solo usuarios de prueba
./scripts/seed.sh test

# Verificar datos existentes
./scripts/seed.sh check

# Usando Docker
./scripts/docker-seed.sh basic
```

### Creación Manual de Usuarios

Los archivos de seed solo crean la estructura básica (permisos y roles). Los usuarios deben crearse manualmente:

#### Usuario Administrador

1. Ejecuta el seed básico: `./scripts/seed.sh basic`
2. Crea el usuario admin manualmente en la base de datos con un hash BCrypt válido
3. Asigna el rol `SUPER_ADMIN`

#### Usuarios de Prueba

Los archivos de seed incluyen ejemplos comentados para crear usuarios de prueba. Descomenta y actualiza con hashes BCrypt válidos según necesites.

### Seed Automático

El sistema ejecuta automáticamente el seed básico al iniciar si no encuentra datos existentes.
# Auth Microservice - Monitoring Stack

Este directorio contiene la configuración completa del stack de monitoreo para el microservicio de autenticación, incluyendo Prometheus, Grafana, AlertManager y exportadores para métricas del sistema.

## Componentes

### Prometheus
- **Puerto**: 9090
- **Función**: Recolección y almacenamiento de métricas
- **Configuración**: `prometheus.yml`
- **Reglas de alerta**: `alert_rules.yml`

### Grafana
- **Puerto**: 3000
- **Función**: Visualización de métricas y dashboards
- **Usuario**: admin
- **Contraseña**: admin
- **Dashboards**: 
  - Auth Microservice Overview
  - Security Dashboard

### AlertManager
- **Puerto**: 9093
- **Función**: Gestión y enrutamiento de alertas
- **Configuración**: `alertmanager.yml`

### Exportadores
- **Node Exporter** (9100): Métricas del sistema
- **PostgreSQL Exporter** (9187): Métricas de base de datos
- **Redis Exporter** (9121): Métricas de cache

## Inicio Rápido

### 1. Iniciar el stack completo de monitoreo
```bash
docker-compose --profile monitoring up -d
```

### 2. Acceder a las interfaces
- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090
- **AlertManager**: http://localhost:9093

### 3. Verificar métricas del microservicio
```bash
curl http://localhost:8080/metrics
curl http://localhost:8080/health
```

## Métricas Principales

### Autenticación
- `auth_authentication_success_total`: Autenticaciones exitosas
- `auth_authentication_failure_total`: Autenticaciones fallidas
- `auth_authentication_duration_seconds`: Tiempo de autenticación
- `auth_authentication_by_country_total`: Autenticaciones por país

### Autorización
- `auth_authorization_success_total`: Autorizaciones exitosas
- `auth_authorization_failure_total`: Autorizaciones fallidas

### Sesiones
- `auth_session_created_total`: Sesiones creadas
- `auth_session_invalidated_total`: Sesiones invalidadas
- `auth_session_active`: Sesiones activas

### Seguridad
- `auth_ratelimit_exceeded_total`: Límites de velocidad excedidos
- `auth_security_suspicious_activity_total`: Actividad sospechosa

### API
- `auth_api_request_total`: Total de requests API
- `auth_api_request_duration_seconds`: Tiempo de respuesta API

### Cache
- `auth_cache_hit_total`: Cache hits
- `auth_cache_miss_total`: Cache misses

### Base de Datos
- `auth_database_query_duration_seconds`: Tiempo de consultas DB
- `auth_database_connection_total`: Conexiones DB

## Alertas Configuradas

### Seguridad
- **HighAuthenticationFailureRate**: Tasa alta de fallos de autenticación
- **SuspiciousLoginsByCountry**: Intentos sospechosos por país
- **BruteForceAttack**: Detección de ataques de fuerza bruta
- **SuspiciousActivitySpike**: Picos de actividad sospechosa

### Rendimiento
- **HighResponseTime**: Tiempo de respuesta alto
- **HighErrorRate**: Tasa alta de errores
- **LowCacheHitRate**: Baja tasa de aciertos de cache

### Infraestructura
- **ServiceDown**: Servicio caído
- **DatabaseConnectionIssues**: Problemas de conexión DB
- **HighMemoryUsage**: Uso alto de memoria
- **HighCPUUsage**: Uso alto de CPU

## Configuración de Alertas

### Email
Editar `alertmanager.yml`:
```yaml
global:
  smtp_smarthost: 'your-smtp-server:587'
  smtp_from: 'alerts@yourcompany.com'
  smtp_auth_username: 'your-email@yourcompany.com'
  smtp_auth_password: 'your-password'
```

### Slack
Agregar webhook URL en `alertmanager.yml`:
```yaml
slack_configs:
  - api_url: 'YOUR_SLACK_WEBHOOK_URL'
    channel: '#security-alerts'
```

### PagerDuty
Configurar integration key en `alertmanager.yml`:
```yaml
pagerduty_configs:
  - routing_key: 'YOUR_PAGERDUTY_INTEGRATION_KEY'
```

## Dashboards

### Auth Microservice Overview
- Métricas generales de autenticación
- Rendimiento de API
- Estado de sesiones
- Métricas de cache y base de datos

### Security Dashboard
- Intentos de login por país
- Actividad sospechosa
- Eventos de rate limiting
- Timeline de eventos de seguridad

## Logs Estructurados

El microservicio genera logs en formato JSON con los siguientes tipos:
- **Security logs**: Eventos de seguridad con geolocalización
- **Audit logs**: Eventos de auditoría administrativa
- **Application logs**: Logs generales de la aplicación

### Ubicación de logs
- Aplicación: `logs/auth-microservice.log`
- Seguridad: `logs/auth-microservice-security.log`
- Auditoría: `logs/auth-microservice-audit.log`

## Mantenimiento

### Limpieza de datos
```bash
# Limpiar datos de Prometheus (30 días de retención configurados)
docker-compose exec prometheus rm -rf /prometheus/*

# Reiniciar servicios de monitoreo
docker-compose --profile monitoring restart
```

### Backup de configuración
```bash
# Backup de dashboards de Grafana
docker-compose exec grafana grafana-cli admin export-dashboard

# Backup de configuración de Prometheus
cp monitoring/prometheus.yml monitoring/prometheus.yml.backup
```

## Troubleshooting

### Prometheus no recolecta métricas
1. Verificar que el microservicio esté exponiendo métricas en `/metrics`
2. Revisar la configuración de `prometheus.yml`
3. Verificar conectividad de red entre contenedores

### Alertas no se envían
1. Verificar configuración de AlertManager
2. Revisar logs de AlertManager: `docker-compose logs alertmanager`
3. Verificar configuración de SMTP/Slack/PagerDuty

### Dashboards no cargan datos
1. Verificar conexión a Prometheus en Grafana
2. Revisar queries de los dashboards
3. Verificar que las métricas existan en Prometheus
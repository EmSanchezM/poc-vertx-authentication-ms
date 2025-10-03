#!/bin/bash

# =============================================================================
# AUTH MICROSERVICE - DEPLOYMENT SCRIPT
# =============================================================================
# Script para desplegar el microservicio de autenticación en diferentes ambientes

set -euo pipefail

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Variables por defecto
ENVIRONMENT=${1:-development}
COMPOSE_PROJECT_NAME="auth-microservice"
DOCKER_COMPOSE_FILES="-f docker-compose.yml"
MONITORING_ENABLED=${MONITORING_ENABLED:-false}
SKIP_TESTS=${SKIP_TESTS:-false}
FORCE_REBUILD=${FORCE_REBUILD:-false}

# Función para logging
log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')] ✓${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[$(date +'%Y-%m-%d %H:%M:%S')] ⚠${NC} $1"
}

log_error() {
    echo -e "${RED}[$(date +'%Y-%m-%d %H:%M:%S')] ✗${NC} $1"
}

# Función para mostrar ayuda
show_help() {
    cat << EOF
Auth Microservice Deployment Script

USAGE:
    $0 [ENVIRONMENT] [OPTIONS]

ENVIRONMENTS:
    development     Deploy for development (default)
    qa              Deploy for QA/staging
    production      Deploy for production

OPTIONS:
    --monitoring    Enable monitoring stack (Prometheus, Grafana, AlertManager)
    --skip-tests    Skip running tests during build
    --force-rebuild Force rebuild of Docker images
    --help          Show this help message

EXAMPLES:
    $0 development
    $0 production --monitoring
    $0 qa --skip-tests --monitoring

ENVIRONMENT VARIABLES:
    MONITORING_ENABLED  Enable monitoring stack (true/false)
    SKIP_TESTS         Skip tests during build (true/false)
    FORCE_REBUILD      Force rebuild of images (true/false)
EOF
}

# Procesar argumentos
while [[ $# -gt 0 ]]; do
    case $1 in
        --monitoring)
            MONITORING_ENABLED=true
            shift
            ;;
        --skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        --force-rebuild)
            FORCE_REBUILD=true
            shift
            ;;
        --help)
            show_help
            exit 0
            ;;
        development|qa|production)
            ENVIRONMENT=$1
            shift
            ;;
        *)
            log_error "Argumento desconocido: $1"
            show_help
            exit 1
            ;;
    esac
done

# Validar ambiente
case $ENVIRONMENT in
    development|qa|production)
        ;;
    *)
        log_error "Ambiente inválido: $ENVIRONMENT"
        log "Ambientes válidos: development, qa, production"
        exit 1
        ;;
esac

# Configurar archivos de compose según el ambiente
case $ENVIRONMENT in
    development)
        DOCKER_COMPOSE_FILES="$DOCKER_COMPOSE_FILES -f docker-compose.development.yml"
        ;;
    qa)
        DOCKER_COMPOSE_FILES="$DOCKER_COMPOSE_FILES -f docker-compose.qa.yml"
        ;;
    production)
        DOCKER_COMPOSE_FILES="$DOCKER_COMPOSE_FILES -f docker-compose.production.yml"
        ;;
esac

# Agregar monitoring si está habilitado
if [[ "$MONITORING_ENABLED" == "true" ]]; then
    DOCKER_COMPOSE_FILES="$DOCKER_COMPOSE_FILES --profile monitoring"
fi

log "=== INICIANDO DEPLOYMENT DE AUTH MICROSERVICE ==="
log "Ambiente: $ENVIRONMENT"
log "Monitoring: $MONITORING_ENABLED"
log "Skip Tests: $SKIP_TESTS"
log "Force Rebuild: $FORCE_REBUILD"
log "Compose Files: $DOCKER_COMPOSE_FILES"

# Verificar prerrequisitos
log "Verificando prerrequisitos..."

# Verificar Docker
if ! command -v docker &> /dev/null; then
    log_error "Docker no está instalado"
    exit 1
fi

# Verificar Docker Compose
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    log_error "Docker Compose no está instalado"
    exit 1
fi

# Usar docker compose o docker-compose según disponibilidad
DOCKER_COMPOSE_CMD="docker-compose"
if docker compose version &> /dev/null; then
    DOCKER_COMPOSE_CMD="docker compose"
fi

log_success "Prerrequisitos verificados"

# Verificar archivos de configuración
log "Verificando archivos de configuración..."

required_files=(
    "docker-compose.yml"
    "docker-compose.$ENVIRONMENT.yml"
    "Dockerfile"
    ".env.$ENVIRONMENT"
)

for file in "${required_files[@]}"; do
    if [[ ! -f "$file" ]]; then
        log_error "Archivo requerido no encontrado: $file"
        exit 1
    fi
done

log_success "Archivos de configuración verificados"

# Cargar variables de entorno
if [[ -f ".env.$ENVIRONMENT" ]]; then
    log "Cargando variables de entorno desde .env.$ENVIRONMENT"
    set -a
    source ".env.$ENVIRONMENT"
    set +a
fi

# Ejecutar tests si no se omiten
if [[ "$SKIP_TESTS" != "true" ]]; then
    log "Ejecutando tests..."
    if ! $DOCKER_COMPOSE_CMD -f docker-compose.test.yml run --rm test-runner; then
        log_error "Tests fallaron"
        exit 1
    fi
    log_success "Tests completados exitosamente"
fi

# Detener servicios existentes
log "Deteniendo servicios existentes..."
$DOCKER_COMPOSE_CMD $DOCKER_COMPOSE_FILES -p $COMPOSE_PROJECT_NAME down --remove-orphans

# Construir imágenes
build_args=""
if [[ "$FORCE_REBUILD" == "true" ]]; then
    build_args="--no-cache"
fi

if [[ "$SKIP_TESTS" == "true" ]]; then
    build_args="$build_args --build-arg SKIP_TESTS=true"
fi

log "Construyendo imágenes..."
$DOCKER_COMPOSE_CMD $DOCKER_COMPOSE_FILES -p $COMPOSE_PROJECT_NAME build $build_args

# Iniciar servicios
log "Iniciando servicios..."
$DOCKER_COMPOSE_CMD $DOCKER_COMPOSE_FILES -p $COMPOSE_PROJECT_NAME up -d

# Esperar a que los servicios estén listos
log "Esperando a que los servicios estén listos..."
sleep 10

# Verificar salud de los servicios
log "Verificando salud de los servicios..."

# Función para verificar salud de un servicio
check_service_health() {
    local service_name=$1
    local health_url=$2
    local max_attempts=30
    local attempt=1

    while [[ $attempt -le $max_attempts ]]; do
        if curl -f -s "$health_url" > /dev/null 2>&1; then
            log_success "$service_name está saludable"
            return 0
        fi
        
        log "Intento $attempt/$max_attempts: Esperando a que $service_name esté listo..."
        sleep 5
        ((attempt++))
    done
    
    log_error "$service_name no está respondiendo después de $max_attempts intentos"
    return 1
}

# Verificar servicios principales
services_to_check=(
    "Auth Service:http://localhost:8080/health"
)

if [[ "$MONITORING_ENABLED" == "true" ]]; then
    services_to_check+=(
        "Prometheus:http://localhost:9090/-/healthy"
        "Grafana:http://localhost:3000/api/health"
    )
fi

all_healthy=true
for service_check in "${services_to_check[@]}"; do
    IFS=':' read -r service_name health_url <<< "$service_check"
    if ! check_service_health "$service_name" "$health_url"; then
        all_healthy=false
    fi
done

if [[ "$all_healthy" != "true" ]]; then
    log_error "Algunos servicios no están saludables"
    log "Mostrando logs de los servicios..."
    $DOCKER_COMPOSE_CMD $DOCKER_COMPOSE_FILES -p $COMPOSE_PROJECT_NAME logs --tail=50
    exit 1
fi

# Mostrar información de deployment
log_success "=== DEPLOYMENT COMPLETADO EXITOSAMENTE ==="
log ""
log "Información del deployment:"
log "  Ambiente: $ENVIRONMENT"
log "  Proyecto: $COMPOSE_PROJECT_NAME"
log ""
log "Servicios disponibles:"
log "  - Auth Service: http://localhost:8080"
log "  - Health Check: http://localhost:8080/health"
log "  - Métricas: http://localhost:8080/metrics"
log "  - Info: http://localhost:8080/info"

if [[ "$MONITORING_ENABLED" == "true" ]]; then
    log ""
    log "Monitoreo disponible:"
    log "  - Prometheus: http://localhost:9090"
    log "  - Grafana: http://localhost:3000 (admin/admin)"
    log "  - AlertManager: http://localhost:9093"
fi

log ""
log "Comandos útiles:"
log "  Ver logs: $DOCKER_COMPOSE_CMD $DOCKER_COMPOSE_FILES -p $COMPOSE_PROJECT_NAME logs -f"
log "  Detener: $DOCKER_COMPOSE_CMD $DOCKER_COMPOSE_FILES -p $COMPOSE_PROJECT_NAME down"
log "  Reiniciar: $DOCKER_COMPOSE_CMD $DOCKER_COMPOSE_FILES -p $COMPOSE_PROJECT_NAME restart"
log ""
log_success "Deployment completado en ambiente: $ENVIRONMENT"
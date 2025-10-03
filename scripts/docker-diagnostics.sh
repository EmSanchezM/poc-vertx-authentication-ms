#!/bin/bash

# =============================================================================
# AUTH MICROSERVICE - DOCKER DIAGNOSTICS SCRIPT
# =============================================================================
# Script de diagnóstico que verifica variables de entorno en el contenedor,
# conectividad de red entre contenedores y resolución DNS del servicio postgres

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

# Function to print colored output
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_step() {
    echo -e "${CYAN}[STEP]${NC} $1"
}

print_debug() {
    echo -e "${PURPLE}[DEBUG]${NC} $1"
}

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to get container name for service
get_container_name() {
    local service=$1
    docker-compose ps -q "$service" 2>/dev/null | head -1
}

# Function to check if container is running
is_container_running() {
    local container=$1
    [ "$(docker inspect -f '{{.State.Running}}' "$container" 2>/dev/null)" = "true" ]
}

# Function to verify environment variables in container
verify_container_environment() {
    local container=$1
    local service_name=$2
    
    print_step "Verificando variables de entorno en contenedor $service_name..."
    
    if ! is_container_running "$container"; then
        print_error "Contenedor $service_name no está ejecutándose"
        return 1
    fi
    
    # Variables críticas para verificar
    local critical_vars=(
        "DB_HOST"
        "DB_PORT" 
        "DB_NAME"
        "DB_USERNAME"
        "DB_PASSWORD"
        "REDIS_HOST"
        "REDIS_PORT"
        "JWT_SECRET"
        "APP_ENV"
    )
    
    print_info "Variables de entorno críticas en $service_name:"
    local missing_vars=0
    
    for var in "${critical_vars[@]}"; do
        local value=$(docker exec "$container" printenv "$var" 2>/dev/null || echo "")
        if [ -n "$value" ]; then
            # Ocultar valores sensibles
            if [[ "$var" == *"PASSWORD"* ]] || [[ "$var" == *"SECRET"* ]]; then
                print_success "  ✅ $var = ***"
            else
                print_success "  ✅ $var = $value"
            fi
        else
            print_error "  ❌ $var = NO DEFINIDA"
            missing_vars=$((missing_vars + 1))
        fi
    done
    
    # Mostrar todas las variables de entorno disponibles
    print_debug "Todas las variables de entorno disponibles en $service_name:"
    docker exec "$container" printenv | sort | while IFS='=' read -r key value; do
        if [[ "$key" == *"PASSWORD"* ]] || [[ "$key" == *"SECRET"* ]]; then
            print_debug "  $key=***"
        else
            print_debug "  $key=$value"
        fi
    done
    
    if [ $missing_vars -eq 0 ]; then
        print_success "Todas las variables críticas están definidas en $service_name"
        return 0
    else
        print_error "$missing_vars variables críticas faltantes en $service_name"
        return 1
    fi
}

# Function to test network connectivity between containers
test_container_connectivity() {
    local source_container=$1
    local target_service=$2
    local target_port=$3
    local service_name=$4
    
    print_step "Probando conectividad de red desde $service_name hacia $target_service:$target_port..."
    
    if ! is_container_running "$source_container"; then
        print_error "Contenedor fuente $service_name no está ejecutándose"
        return 1
    fi
    
    # Verificar si el contenedor tiene herramientas de red
    local has_nc=false
    local has_telnet=false
    local has_curl=false
    
    if docker exec "$source_container" which nc >/dev/null 2>&1; then
        has_nc=true
    fi
    
    if docker exec "$source_container" which telnet >/dev/null 2>&1; then
        has_telnet=true
    fi
    
    if docker exec "$source_container" which curl >/dev/null 2>&1; then
        has_curl=true
    fi
    
    print_debug "Herramientas disponibles en $service_name: nc=$has_nc, telnet=$has_telnet, curl=$has_curl"
    
    # Intentar conectividad usando diferentes métodos
    local connectivity_ok=false
    
    if [ "$has_nc" = true ]; then
        print_debug "Probando conectividad con netcat..."
        if docker exec "$source_container" timeout 5 nc -z "$target_service" "$target_port" >/dev/null 2>&1; then
            print_success "  ✅ Conectividad exitosa usando netcat"
            connectivity_ok=true
        else
            print_warning "  ⚠️ Conectividad falló usando netcat"
        fi
    fi
    
    if [ "$has_telnet" = true ] && [ "$connectivity_ok" = false ]; then
        print_debug "Probando conectividad con telnet..."
        if docker exec "$source_container" timeout 5 bash -c "echo '' | telnet $target_service $target_port" >/dev/null 2>&1; then
            print_success "  ✅ Conectividad exitosa usando telnet"
            connectivity_ok=true
        else
            print_warning "  ⚠️ Conectividad falló usando telnet"
        fi
    fi
    
    # Método alternativo usando /dev/tcp (disponible en bash)
    if [ "$connectivity_ok" = false ]; then
        print_debug "Probando conectividad con /dev/tcp..."
        if docker exec "$source_container" timeout 5 bash -c "exec 3<>/dev/tcp/$target_service/$target_port && echo 'Connected' >&3" >/dev/null 2>&1; then
            print_success "  ✅ Conectividad exitosa usando /dev/tcp"
            connectivity_ok=true
        else
            print_warning "  ⚠️ Conectividad falló usando /dev/tcp"
        fi
    fi
    
    if [ "$connectivity_ok" = true ]; then
        print_success "Conectividad de red exitosa desde $service_name hacia $target_service:$target_port"
        return 0
    else
        print_error "No se pudo establecer conectividad desde $service_name hacia $target_service:$target_port"
        return 1
    fi
}

# Function to test DNS resolution
test_dns_resolution() {
    local container=$1
    local hostname=$2
    local service_name=$3
    
    print_step "Probando resolución DNS de '$hostname' desde $service_name..."
    
    if ! is_container_running "$container"; then
        print_error "Contenedor $service_name no está ejecutándose"
        return 1
    fi
    
    # Verificar herramientas DNS disponibles
    local has_nslookup=false
    local has_dig=false
    local has_getent=false
    
    if docker exec "$container" which nslookup >/dev/null 2>&1; then
        has_nslookup=true
    fi
    
    if docker exec "$container" which dig >/dev/null 2>&1; then
        has_dig=true
    fi
    
    if docker exec "$container" which getent >/dev/null 2>&1; then
        has_getent=true
    fi
    
    print_debug "Herramientas DNS disponibles en $service_name: nslookup=$has_nslookup, dig=$has_dig, getent=$has_getent"
    
    local dns_ok=false
    local resolved_ip=""
    
    # Intentar resolución DNS usando diferentes métodos
    if [ "$has_nslookup" = true ]; then
        print_debug "Probando resolución DNS con nslookup..."
        resolved_ip=$(docker exec "$container" nslookup "$hostname" 2>/dev/null | grep -A1 "Name:" | grep "Address:" | awk '{print $2}' | head -1)
        if [ -n "$resolved_ip" ]; then
            print_success "  ✅ DNS resuelto con nslookup: $hostname -> $resolved_ip"
            dns_ok=true
        else
            print_warning "  ⚠️ DNS no resuelto con nslookup"
        fi
    fi
    
    if [ "$has_dig" = true ] && [ "$dns_ok" = false ]; then
        print_debug "Probando resolución DNS con dig..."
        resolved_ip=$(docker exec "$container" dig +short "$hostname" 2>/dev/null | head -1)
        if [ -n "$resolved_ip" ]; then
            print_success "  ✅ DNS resuelto con dig: $hostname -> $resolved_ip"
            dns_ok=true
        else
            print_warning "  ⚠️ DNS no resuelto con dig"
        fi
    fi
    
    if [ "$has_getent" = true ] && [ "$dns_ok" = false ]; then
        print_debug "Probando resolución DNS con getent..."
        resolved_ip=$(docker exec "$container" getent hosts "$hostname" 2>/dev/null | awk '{print $1}' | head -1)
        if [ -n "$resolved_ip" ]; then
            print_success "  ✅ DNS resuelto con getent: $hostname -> $resolved_ip"
            dns_ok=true
        else
            print_warning "  ⚠️ DNS no resuelto con getent"
        fi
    fi
    
    # Método alternativo usando ping
    if [ "$dns_ok" = false ]; then
        print_debug "Probando resolución DNS con ping..."
        if docker exec "$container" timeout 3 ping -c 1 "$hostname" >/dev/null 2>&1; then
            resolved_ip=$(docker exec "$container" ping -c 1 "$hostname" 2>/dev/null | head -1 | grep -oE '\([0-9.]+\)' | tr -d '()')
            if [ -n "$resolved_ip" ]; then
                print_success "  ✅ DNS resuelto con ping: $hostname -> $resolved_ip"
                dns_ok=true
            fi
        else
            print_warning "  ⚠️ DNS no resuelto con ping"
        fi
    fi
    
    if [ "$dns_ok" = true ]; then
        print_success "Resolución DNS exitosa: $hostname -> $resolved_ip"
        
        # Verificar que la IP no sea localhost
        if [ "$resolved_ip" = "127.0.0.1" ] || [ "$resolved_ip" = "::1" ]; then
            print_warning "⚠️ ADVERTENCIA: $hostname se resuelve a localhost ($resolved_ip)"
            print_warning "   Esto podría indicar un problema de configuración de red"
        fi
        
        return 0
    else
        print_error "No se pudo resolver DNS para $hostname desde $service_name"
        return 1
    fi
}

# Function to show network information
show_network_info() {
    local container=$1
    local service_name=$2
    
    print_step "Información de red para $service_name..."
    
    if ! is_container_running "$container"; then
        print_error "Contenedor $service_name no está ejecutándose"
        return 1
    fi
    
    print_info "Configuración de red en $service_name:"
    
    # Mostrar interfaces de red
    print_debug "Interfaces de red:"
    docker exec "$container" ip addr show 2>/dev/null || docker exec "$container" ifconfig 2>/dev/null || print_warning "No se pudo obtener información de interfaces"
    
    # Mostrar tabla de rutas
    print_debug "Tabla de rutas:"
    docker exec "$container" ip route show 2>/dev/null || docker exec "$container" route -n 2>/dev/null || print_warning "No se pudo obtener tabla de rutas"
    
    # Mostrar configuración DNS
    print_debug "Configuración DNS (/etc/resolv.conf):"
    docker exec "$container" cat /etc/resolv.conf 2>/dev/null || print_warning "No se pudo leer /etc/resolv.conf"
    
    # Mostrar hosts conocidos
    print_debug "Hosts conocidos (/etc/hosts):"
    docker exec "$container" cat /etc/hosts 2>/dev/null || print_warning "No se pudo leer /etc/hosts"
}

# Function to run comprehensive diagnostics
run_diagnostics() {
    local environment=${1:-development}
    
    echo "=============================================="
    echo "Auth Microservice - Diagnóstico Docker"
    echo "Ambiente: $environment"
    echo "=============================================="
    echo ""
    
    # Verificar que Docker Compose esté disponible
    if ! command_exists docker-compose; then
        print_error "Docker Compose no está disponible"
        exit 1
    fi
    
    # Verificar que los archivos de compose existan
    local compose_files="-f docker-compose.yml -f docker-compose.${environment}.yml"
    if ! docker-compose $compose_files config --quiet >/dev/null 2>&1; then
        print_error "Configuración de Docker Compose inválida para ambiente $environment"
        exit 1
    fi
    
    # Obtener contenedores
    print_step "Obteniendo información de contenedores..."
    local auth_container=$(get_container_name "auth-service")
    local postgres_container=$(get_container_name "postgres")
    local redis_container=$(get_container_name "redis")
    
    print_info "Contenedores identificados:"
    print_info "  Auth Service: ${auth_container:-'NO ENCONTRADO'}"
    print_info "  PostgreSQL: ${postgres_container:-'NO ENCONTRADO'}"
    print_info "  Redis: ${redis_container:-'NO ENCONTRADO'}"
    echo ""
    
    # Verificar estado de contenedores
    print_step "Verificando estado de contenedores..."
    docker-compose $compose_files ps
    echo ""
    
    local diagnostics_passed=0
    local diagnostics_failed=0
    
    # Diagnóstico del servicio de autenticación
    if [ -n "$auth_container" ] && is_container_running "$auth_container"; then
        print_step "=== DIAGNÓSTICO: AUTH SERVICE ==="
        
        # Verificar variables de entorno
        if verify_container_environment "$auth_container" "auth-service"; then
            diagnostics_passed=$((diagnostics_passed + 1))
        else
            diagnostics_failed=$((diagnostics_failed + 1))
        fi
        
        # Mostrar información de red
        show_network_info "$auth_container" "auth-service"
        
        # Probar resolución DNS de postgres
        if test_dns_resolution "$auth_container" "postgres" "auth-service"; then
            diagnostics_passed=$((diagnostics_passed + 1))
        else
            diagnostics_failed=$((diagnostics_failed + 1))
        fi
        
        # Probar resolución DNS de redis
        if test_dns_resolution "$auth_container" "redis" "auth-service"; then
            diagnostics_passed=$((diagnostics_passed + 1))
        else
            diagnostics_failed=$((diagnostics_failed + 1))
        fi
        
        # Probar conectividad a postgres
        if test_container_connectivity "$auth_container" "postgres" "5432" "auth-service"; then
            diagnostics_passed=$((diagnostics_passed + 1))
        else
            diagnostics_failed=$((diagnostics_failed + 1))
        fi
        
        # Probar conectividad a redis
        if test_container_connectivity "$auth_container" "redis" "6379" "auth-service"; then
            diagnostics_passed=$((diagnostics_passed + 1))
        else
            diagnostics_failed=$((diagnostics_failed + 1))
        fi
        
        echo ""
    else
        print_error "Contenedor auth-service no está ejecutándose, omitiendo diagnósticos"
        diagnostics_failed=$((diagnostics_failed + 5))
    fi
    
    # Diagnóstico de PostgreSQL
    if [ -n "$postgres_container" ] && is_container_running "$postgres_container"; then
        print_step "=== DIAGNÓSTICO: POSTGRESQL ==="
        
        # Verificar que PostgreSQL esté aceptando conexiones
        print_step "Verificando que PostgreSQL esté aceptando conexiones..."
        if docker exec "$postgres_container" pg_isready -U "${POSTGRES_USER:-auth_user}" >/dev/null 2>&1; then
            print_success "PostgreSQL está aceptando conexiones"
            diagnostics_passed=$((diagnostics_passed + 1))
        else
            print_error "PostgreSQL no está aceptando conexiones"
            diagnostics_failed=$((diagnostics_failed + 1))
        fi
        
        # Mostrar información de la base de datos
        print_debug "Información de PostgreSQL:"
        docker exec "$postgres_container" psql -U "${POSTGRES_USER:-auth_user}" -d "${POSTGRES_DB:-auth_microservice_dev}" -c "SELECT version();" 2>/dev/null || print_warning "No se pudo obtener versión de PostgreSQL"
        
        echo ""
    else
        print_error "Contenedor postgres no está ejecutándose"
        diagnostics_failed=$((diagnostics_failed + 1))
    fi
    
    # Diagnóstico de Redis
    if [ -n "$redis_container" ] && is_container_running "$redis_container"; then
        print_step "=== DIAGNÓSTICO: REDIS ==="
        
        # Verificar que Redis esté respondiendo
        print_step "Verificando que Redis esté respondiendo..."
        if docker exec "$redis_container" redis-cli ping >/dev/null 2>&1; then
            print_success "Redis está respondiendo"
            diagnostics_passed=$((diagnostics_passed + 1))
        else
            print_error "Redis no está respondiendo"
            diagnostics_failed=$((diagnostics_failed + 1))
        fi
        
        # Mostrar información de Redis
        print_debug "Información de Redis:"
        docker exec "$redis_container" redis-cli info server 2>/dev/null | head -10 || print_warning "No se pudo obtener información de Redis"
        
        echo ""
    else
        print_error "Contenedor redis no está ejecutándose"
        diagnostics_failed=$((diagnostics_failed + 1))
    fi
    
    # Resumen final
    echo "=============================================="
    echo "RESUMEN DE DIAGNÓSTICOS"
    echo "=============================================="
    print_info "Diagnósticos exitosos: $diagnostics_passed"
    print_info "Diagnósticos fallidos: $diagnostics_failed"
    
    if [ $diagnostics_failed -eq 0 ]; then
        print_success "🎉 Todos los diagnósticos pasaron exitosamente!"
        echo ""
        print_info "El entorno Docker está configurado correctamente."
        print_info "La aplicación debería poder conectarse a las bases de datos."
    else
        print_error "❌ $diagnostics_failed diagnósticos fallaron"
        echo ""
        print_warning "Revisa los errores anteriores para identificar problemas de configuración."
        print_info "Comandos útiles para debugging:"
        print_info "  docker-compose $compose_files logs auth-service"
        print_info "  docker-compose $compose_files logs postgres"
        print_info "  docker-compose $compose_files logs redis"
        print_info "  docker exec <container> printenv"
        print_info "  docker network ls"
        print_info "  docker network inspect <network_name>"
    fi
    
    echo ""
    return $diagnostics_failed
}

# Function to show usage
show_usage() {
    echo "Uso: $0 [ambiente]"
    echo ""
    echo "Ambientes disponibles:"
    echo "  development  - Ambiente de desarrollo (por defecto)"
    echo "  qa           - Ambiente de QA"
    echo "  production   - Ambiente de producción"
    echo ""
    echo "Ejemplos:"
    echo "  $0                    # Diagnóstico en desarrollo"
    echo "  $0 development        # Diagnóstico en desarrollo"
    echo "  $0 qa                 # Diagnóstico en QA"
    echo ""
}

# Main execution
main() {
    local environment=${1:-development}
    
    case $environment in
        development|qa|production)
            run_diagnostics "$environment"
            ;;
        --help|-h|help)
            show_usage
            exit 0
            ;;
        *)
            print_error "Ambiente inválido: $environment"
            show_usage
            exit 1
            ;;
    esac
}

# Run main function with all arguments
main "$@"
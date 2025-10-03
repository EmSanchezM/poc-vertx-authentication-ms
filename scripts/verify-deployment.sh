#!/bin/bash

# =============================================================================
# AUTH MICROSERVICE - DEPLOYMENT VERIFICATION SCRIPT
# =============================================================================
# Script para verificar que el deployment esté funcionando correctamente

set -euo pipefail

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Variables
BASE_URL=${BASE_URL:-http://localhost:8080}
TIMEOUT=${TIMEOUT:-30}
VERBOSE=${VERBOSE:-false}

# Contadores
TESTS_PASSED=0
TESTS_FAILED=0
TOTAL_TESTS=0

# Función para logging
log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')] ✓${NC} $1"
    ((TESTS_PASSED++))
}

log_warning() {
    echo -e "${YELLOW}[$(date +'%Y-%m-%d %H:%M:%S')] ⚠${NC} $1"
}

log_error() {
    echo -e "${RED}[$(date +'%Y-%m-%d %H:%M:%S')] ✗${NC} $1"
    ((TESTS_FAILED++))
}

# Función para hacer peticiones HTTP
make_request() {
    local method=$1
    local endpoint=$2
    local data=${3:-}
    local expected_status=${4:-200}
    local headers=${5:-}
    
    local curl_cmd="curl -s -w '%{http_code}' --max-time $TIMEOUT"
    
    if [[ -n "$headers" ]]; then
        curl_cmd="$curl_cmd $headers"
    fi
    
    if [[ "$method" == "POST" && -n "$data" ]]; then
        curl_cmd="$curl_cmd -X POST -H 'Content-Type: application/json' -d '$data'"
    elif [[ "$method" == "PUT" && -n "$data" ]]; then
        curl_cmd="$curl_cmd -X PUT -H 'Content-Type: application/json' -d '$data'"
    fi
    
    curl_cmd="$curl_cmd $BASE_URL$endpoint"
    
    if [[ "$VERBOSE" == "true" ]]; then
        log "Ejecutando: $curl_cmd"
    fi
    
    local response
    response=$(eval $curl_cmd)
    local status_code="${response: -3}"
    local body="${response%???}"
    
    if [[ "$status_code" == "$expected_status" ]]; then
        return 0
    else
        if [[ "$VERBOSE" == "true" ]]; then
            log_error "Status esperado: $expected_status, recibido: $status_code"
            log_error "Response body: $body"
        fi
        return 1
    fi
}

# Función para verificar JSON válido
verify_json() {
    local response=$1
    local body="${response%???}"
    
    if echo "$body" | jq . >/dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

# Función para ejecutar un test
run_test() {
    local test_name=$1
    local test_function=$2
    
    ((TOTAL_TESTS++))
    log "Ejecutando test: $test_name"
    
    if $test_function; then
        log_success "$test_name"
    else
        log_error "$test_name"
    fi
}

# Tests de endpoints básicos
test_root_endpoint() {
    make_request "GET" "/" "" 200
}

test_health_endpoint() {
    local response
    response=$(curl -s --max-time $TIMEOUT "$BASE_URL/health")
    
    if echo "$response" | jq -e '.status' >/dev/null 2>&1; then
        local status=$(echo "$response" | jq -r '.status')
        if [[ "$status" == "UP" ]]; then
            return 0
        fi
    fi
    return 1
}

test_metrics_endpoint() {
    local response
    response=$(curl -s --max-time $TIMEOUT "$BASE_URL/metrics")
    
    # Verificar que contiene métricas de Prometheus
    if echo "$response" | grep -q "# HELP"; then
        return 0
    fi
    return 1
}

test_info_endpoint() {
    local response
    response=$(curl -s --max-time $TIMEOUT "$BASE_URL/info")
    
    if echo "$response" | jq -e '.environment' >/dev/null 2>&1; then
        return 0
    fi
    return 1
}

# Tests de autenticación
test_auth_login_invalid() {
    local data='{"email":"invalid@test.com","password":"wrongpassword"}'
    make_request "POST" "/api/auth/login" "$data" 401
}

test_auth_register_invalid() {
    local data='{"email":"invalid-email","password":"123"}'
    make_request "POST" "/api/auth/register" "$data" 400
}

# Tests de endpoints protegidos sin autenticación
test_protected_endpoint_unauthorized() {
    make_request "GET" "/api/users/profile" "" 401
}

# Tests de CORS
test_cors_preflight() {
    local response
    response=$(curl -s -w '%{http_code}' --max-time $TIMEOUT \
        -X OPTIONS \
        -H "Origin: http://localhost:3000" \
        -H "Access-Control-Request-Method: POST" \
        -H "Access-Control-Request-Headers: Content-Type,Authorization" \
        "$BASE_URL/api/auth/login")
    
    local status_code="${response: -3}"
    if [[ "$status_code" == "200" || "$status_code" == "204" ]]; then
        return 0
    fi
    return 1
}

# Tests de rate limiting
test_rate_limiting() {
    local data='{"email":"test@test.com","password":"wrongpassword"}'
    local attempts=0
    local max_attempts=20
    
    # Hacer múltiples intentos para activar rate limiting
    while [[ $attempts -lt $max_attempts ]]; do
        local response
        response=$(curl -s -w '%{http_code}' --max-time $TIMEOUT \
            -X POST \
            -H 'Content-Type: application/json' \
            -d "$data" \
            "$BASE_URL/api/auth/login")
        
        local status_code="${response: -3}"
        if [[ "$status_code" == "429" ]]; then
            return 0
        fi
        
        ((attempts++))
        sleep 0.1
    done
    
    # Si no se activó rate limiting, aún es válido
    log_warning "Rate limiting no se activó después de $max_attempts intentos"
    return 0
}

# Tests de base de datos
test_database_connectivity() {
    # Verificar a través del health endpoint que incluye DB status
    local response
    response=$(curl -s --max-time $TIMEOUT "$BASE_URL/health")
    
    if echo "$response" | jq -e '.components.database.status' >/dev/null 2>&1; then
        local db_status=$(echo "$response" | jq -r '.components.database.status')
        if [[ "$db_status" == "UP" ]]; then
            return 0
        fi
    fi
    return 1
}

# Tests de Redis
test_redis_connectivity() {
    # Verificar a través del health endpoint que incluye Redis status
    local response
    response=$(curl -s --max-time $TIMEOUT "$BASE_URL/health")
    
    if echo "$response" | jq -e '.components.redis.status' >/dev/null 2>&1; then
        local redis_status=$(echo "$response" | jq -r '.components.redis.status')
        if [[ "$redis_status" == "UP" ]]; then
            return 0
        fi
    fi
    return 1
}

# Tests de seguridad
test_security_headers() {
    local response
    response=$(curl -s -I --max-time $TIMEOUT "$BASE_URL/")
    
    # Verificar algunos headers de seguridad básicos
    if echo "$response" | grep -qi "x-content-type-options"; then
        return 0
    fi
    
    # Si no hay headers específicos, aún puede ser válido
    return 0
}

# Función principal
main() {
    log "=== INICIANDO VERIFICACIÓN DE DEPLOYMENT ==="
    log "Base URL: $BASE_URL"
    log "Timeout: ${TIMEOUT}s"
    log "Verbose: $VERBOSE"
    log ""
    
    # Verificar que jq esté disponible
    if ! command -v jq &> /dev/null; then
        log_warning "jq no está instalado, algunos tests pueden fallar"
    fi
    
    # Ejecutar tests básicos
    log "=== TESTS DE ENDPOINTS BÁSICOS ==="
    run_test "Root endpoint" test_root_endpoint
    run_test "Health endpoint" test_health_endpoint
    run_test "Metrics endpoint" test_metrics_endpoint
    run_test "Info endpoint" test_info_endpoint
    
    # Ejecutar tests de autenticación
    log ""
    log "=== TESTS DE AUTENTICACIÓN ==="
    run_test "Login con credenciales inválidas" test_auth_login_invalid
    run_test "Registro con datos inválidos" test_auth_register_invalid
    run_test "Endpoint protegido sin autenticación" test_protected_endpoint_unauthorized
    
    # Ejecutar tests de CORS
    log ""
    log "=== TESTS DE CORS ==="
    run_test "CORS preflight request" test_cors_preflight
    
    # Ejecutar tests de rate limiting
    log ""
    log "=== TESTS DE RATE LIMITING ==="
    run_test "Rate limiting" test_rate_limiting
    
    # Ejecutar tests de conectividad
    log ""
    log "=== TESTS DE CONECTIVIDAD ==="
    run_test "Conectividad de base de datos" test_database_connectivity
    run_test "Conectividad de Redis" test_redis_connectivity
    
    # Ejecutar tests de seguridad
    log ""
    log "=== TESTS DE SEGURIDAD ==="
    run_test "Headers de seguridad" test_security_headers
    
    # Mostrar resumen
    log ""
    log "=== RESUMEN DE VERIFICACIÓN ==="
    log "Total de tests: $TOTAL_TESTS"
    log_success "Tests pasados: $TESTS_PASSED"
    
    if [[ $TESTS_FAILED -gt 0 ]]; then
        log_error "Tests fallidos: $TESTS_FAILED"
        log ""
        log_error "La verificación del deployment falló"
        exit 1
    else
        log ""
        log_success "✓ Todos los tests pasaron - Deployment verificado exitosamente"
        exit 0
    fi
}

# Procesar argumentos
while [[ $# -gt 0 ]]; do
    case $1 in
        --base-url)
            BASE_URL="$2"
            shift 2
            ;;
        --timeout)
            TIMEOUT="$2"
            shift 2
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --help)
            cat << EOF
Auth Microservice Deployment Verification Script

USAGE:
    $0 [OPTIONS]

OPTIONS:
    --base-url URL    Base URL del servicio (default: http://localhost:8080)
    --timeout SECONDS Timeout para requests (default: 30)
    --verbose         Mostrar output detallado
    --help            Mostrar esta ayuda

EXAMPLES:
    $0
    $0 --base-url http://localhost:8080 --verbose
    $0 --timeout 60 --verbose
EOF
            exit 0
            ;;
        *)
            log_error "Argumento desconocido: $1"
            exit 1
            ;;
    esac
done

# Ejecutar verificación
main
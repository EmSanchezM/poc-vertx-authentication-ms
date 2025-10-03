#!/bin/bash

# =============================================================================
# NETWORK CONNECTIVITY TEST SCRIPT
# =============================================================================
# Script simple para probar conectividad de red entre contenedores
# Diseñado para ejecutarse dentro del contenedor de la aplicación

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

# Function to test DNS resolution
test_dns() {
    local hostname=$1
    print_info "Probando resolución DNS para: $hostname"
    
    if nslookup "$hostname" >/dev/null 2>&1; then
        local ip=$(nslookup "$hostname" | grep -A1 "Name:" | grep "Address:" | awk '{print $2}' | head -1)
        print_success "DNS OK: $hostname -> $ip"
        return 0
    elif ping -c 1 "$hostname" >/dev/null 2>&1; then
        local ip=$(ping -c 1 "$hostname" | head -1 | grep -oE '\([0-9.]+\)' | tr -d '()')
        print_success "DNS OK (via ping): $hostname -> $ip"
        return 0
    else
        print_error "DNS FAIL: No se pudo resolver $hostname"
        return 1
    fi
}

# Function to test port connectivity
test_port() {
    local host=$1
    local port=$2
    print_info "Probando conectividad a: $host:$port"
    
    if command -v nc >/dev/null 2>&1; then
        if timeout 5 nc -z "$host" "$port" >/dev/null 2>&1; then
            print_success "CONECTIVIDAD OK: $host:$port"
            return 0
        else
            print_error "CONECTIVIDAD FAIL: $host:$port"
            return 1
        fi
    elif timeout 5 bash -c "exec 3<>/dev/tcp/$host/$port" >/dev/null 2>&1; then
        print_success "CONECTIVIDAD OK: $host:$port"
        return 0
    else
        print_error "CONECTIVIDAD FAIL: $host:$port"
        return 1
    fi
}

# Function to show network info
show_network_info() {
    print_info "=== INFORMACIÓN DE RED ==="
    
    print_info "Hostname: $(hostname)"
    print_info "Interfaces de red:"
    if command -v ip >/dev/null 2>&1; then
        ip addr show | grep -E "inet |UP" | sed 's/^/  /'
    elif command -v ifconfig >/dev/null 2>&1; then
        ifconfig | grep -E "inet |UP" | sed 's/^/  /'
    else
        print_warning "No se encontraron comandos de red (ip/ifconfig)"
    fi
    
    print_info "Tabla de rutas:"
    if command -v ip >/dev/null 2>&1; then
        ip route show | sed 's/^/  /'
    elif command -v route >/dev/null 2>&1; then
        route -n | sed 's/^/  /'
    else
        print_warning "No se encontraron comandos de rutas (ip/route)"
    fi
    
    print_info "DNS Configuration (/etc/resolv.conf):"
    if [ -f /etc/resolv.conf ]; then
        cat /etc/resolv.conf | sed 's/^/  /'
    else
        print_warning "/etc/resolv.conf no encontrado"
    fi
    
    print_info "Hosts conocidos (/etc/hosts):"
    if [ -f /etc/hosts ]; then
        cat /etc/hosts | sed 's/^/  /'
    else
        print_warning "/etc/hosts no encontrado"
    fi
}

# Function to show environment variables
show_env_vars() {
    print_info "=== VARIABLES DE ENTORNO CRÍTICAS ==="
    
    local critical_vars=(
        "DB_HOST"
        "DB_PORT"
        "DB_NAME"
        "DB_USERNAME"
        "REDIS_HOST"
        "REDIS_PORT"
        "APP_ENV"
        "HOSTNAME"
    )
    
    for var in "${critical_vars[@]}"; do
        local value=$(printenv "$var" 2>/dev/null || echo "")
        if [ -n "$value" ]; then
            if [[ "$var" == *"PASSWORD"* ]] || [[ "$var" == *"SECRET"* ]]; then
                print_success "  $var = ***"
            else
                print_success "  $var = $value"
            fi
        else
            print_error "  $var = NO DEFINIDA"
        fi
    done
}

# Main test function
run_tests() {
    echo "=============================================="
    echo "Test de Conectividad de Red"
    echo "=============================================="
    echo ""
    
    # Mostrar información del sistema
    show_network_info
    echo ""
    
    # Mostrar variables de entorno
    show_env_vars
    echo ""
    
    # Tests de DNS
    print_info "=== TESTS DE DNS ==="
    local dns_tests=0
    local dns_passed=0
    
    for host in "postgres" "redis" "localhost"; do
        dns_tests=$((dns_tests + 1))
        if test_dns "$host"; then
            dns_passed=$((dns_passed + 1))
        fi
    done
    echo ""
    
    # Tests de conectividad
    print_info "=== TESTS DE CONECTIVIDAD ==="
    local conn_tests=0
    local conn_passed=0
    
    # Test PostgreSQL
    local db_host=${DB_HOST:-postgres}
    local db_port=${DB_PORT:-5432}
    conn_tests=$((conn_tests + 1))
    if test_port "$db_host" "$db_port"; then
        conn_passed=$((conn_passed + 1))
    fi
    
    # Test Redis
    local redis_host=${REDIS_HOST:-redis}
    local redis_port=${REDIS_PORT:-6379}
    conn_tests=$((conn_tests + 1))
    if test_port "$redis_host" "$redis_port"; then
        conn_passed=$((conn_passed + 1))
    fi
    
    echo ""
    
    # Resumen
    print_info "=== RESUMEN ==="
    print_info "Tests DNS: $dns_passed/$dns_tests pasaron"
    print_info "Tests Conectividad: $conn_passed/$conn_tests pasaron"
    
    local total_tests=$((dns_tests + conn_tests))
    local total_passed=$((dns_passed + conn_passed))
    
    if [ $total_passed -eq $total_tests ]; then
        print_success "🎉 Todos los tests pasaron ($total_passed/$total_tests)"
        return 0
    else
        print_error "❌ $((total_tests - total_passed)) tests fallaron ($total_passed/$total_tests)"
        return 1
    fi
}

# Function to show usage
show_usage() {
    echo "Uso: $0 [opción]"
    echo ""
    echo "Opciones:"
    echo "  test     - Ejecutar todos los tests (por defecto)"
    echo "  dns      - Solo tests de DNS"
    echo "  network  - Solo información de red"
    echo "  env      - Solo variables de entorno"
    echo "  help     - Mostrar esta ayuda"
    echo ""
    echo "Ejemplos:"
    echo "  $0           # Ejecutar todos los tests"
    echo "  $0 test      # Ejecutar todos los tests"
    echo "  $0 dns       # Solo tests de DNS"
    echo "  $0 network   # Solo información de red"
    echo ""
}

# Main execution
case "${1:-test}" in
    test)
        run_tests
        ;;
    dns)
        print_info "=== TESTS DE DNS ==="
        for host in "postgres" "redis" "localhost"; do
            test_dns "$host"
        done
        ;;
    network)
        show_network_info
        ;;
    env)
        show_env_vars
        ;;
    help|--help|-h)
        show_usage
        ;;
    *)
        print_error "Opción inválida: $1"
        show_usage
        exit 1
        ;;
esac
#!/bin/bash

# Script para ejecutar seeds usando Docker
# Uso: ./scripts/docker-seed.sh [all|basic|test|check]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

show_help() {
    echo "Uso: $0 [TIPO]"
    echo ""
    echo "Tipos de seed disponibles:"
    echo "  all    - Ejecuta todos los seeds (permisos, roles, usuarios)"
    echo "  basic  - Ejecuta solo datos básicos (permisos, roles, admin)"
    echo "  test   - Ejecuta solo usuarios de prueba"
    echo "  check  - Verifica si existen datos básicos"
    echo ""
    echo "Este script ejecuta los seeds usando el contenedor Docker."
    echo "Asegúrate de que los contenedores estén ejecutándose."
}

if [ $# -eq 0 ]; then
    print_error "Se requiere especificar el tipo de seed"
    show_help
    exit 1
fi

SEED_TYPE="$1"

case "$SEED_TYPE" in
    all|basic|test|check)
        ;;
    *)
        print_error "Tipo de seed no válido: $SEED_TYPE"
        show_help
        exit 1
        ;;
esac

print_info "Ejecutando seed tipo: $SEED_TYPE usando Docker"

cd "$PROJECT_DIR"

# Verificar si los contenedores están ejecutándose
if ! docker-compose -f docker-compose.development.yml ps | grep -q "authentication-ms.*Up"; then
    print_error "El contenedor authentication-ms no está ejecutándose"
    print_info "Ejecuta: docker-compose -f docker-compose.development.yml up -d"
    exit 1
fi

# Ejecutar seed en el contenedor
print_info "Ejecutando seed en el contenedor..."

docker exec authentication-ms java -jar app.jar seed "$SEED_TYPE"

if [ $? -eq 0 ]; then
    print_success "Seed ejecutado exitosamente en el contenedor"
else
    print_error "Error ejecutando seed en el contenedor"
    exit 1
fi
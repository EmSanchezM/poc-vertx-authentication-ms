#!/bin/bash

# Script para ejecutar seeds de la base de datos
# Uso: ./scripts/seed.sh [all|basic|test|check]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Función para imprimir mensajes con color
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

# Función para mostrar ayuda
show_help() {
    echo "Uso: $0 [TIPO]"
    echo ""
    echo "Tipos de seed disponibles:"
    echo "  all    - Ejecuta todos los seeds (permisos, roles, usuarios)"
    echo "  basic  - Ejecuta solo datos básicos (permisos, roles, admin)"
    echo "  test   - Ejecuta solo usuarios de prueba"
    echo "  check  - Verifica si existen datos básicos"
    echo ""
    echo "Ejemplos:"
    echo "  $0 all"
    echo "  $0 basic"
    echo "  $0 test"
    echo "  $0 check"
}

# Verificar argumentos
if [ $# -eq 0 ]; then
    print_error "Se requiere especificar el tipo de seed"
    show_help
    exit 1
fi

SEED_TYPE="$1"

# Validar tipo de seed
case "$SEED_TYPE" in
    all|basic|test|check)
        ;;
    *)
        print_error "Tipo de seed no válido: $SEED_TYPE"
        show_help
        exit 1
        ;;
esac

print_info "Ejecutando seed tipo: $SEED_TYPE"

# Verificar si el JAR existe
JAR_FILE="$PROJECT_DIR/build/libs/auth-microservice-1.0.0-fat.jar"

if [ ! -f "$JAR_FILE" ]; then
    print_warning "JAR no encontrado, compilando proyecto..."
    cd "$PROJECT_DIR"
    ./gradlew shadowJar
    
    if [ ! -f "$JAR_FILE" ]; then
        print_error "Error compilando el proyecto"
        exit 1
    fi
fi

# Ejecutar seed
print_info "Ejecutando comando: java -jar $JAR_FILE seed $SEED_TYPE"

cd "$PROJECT_DIR"
java -jar "$JAR_FILE" seed "$SEED_TYPE"

if [ $? -eq 0 ]; then
    print_success "Seed ejecutado exitosamente"
else
    print_error "Error ejecutando seed"
    exit 1
fi
#!/bin/bash

# Script para ejecutar seeds de la base de datos
# Uso: ./scripts/seed.sh [all|basic|test|check|fresh]
# Similar a 'php artisan db:seed' de Laravel

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
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

print_header() {
    echo -e "${CYAN}=== $1 ===${NC}"
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
    echo "  fresh  - Limpia y recrea todos los datos (¡CUIDADO!)"
    echo ""
    echo "Ejemplos:"
    echo "  $0 all     # Seed completo"
    echo "  $0 basic   # Solo permisos y roles"
    echo "  $0 test    # Solo usuarios de prueba"
    echo "  $0 check   # Verificar datos"
    echo "  $0 fresh   # Limpiar y recrear todo"
    echo ""
    echo "Nota: Este script NO inicia el servidor, solo ejecuta los seeds."
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
    all|basic|test|check|fresh)
        ;;
    *)
        print_error "Tipo de seed no válido: $SEED_TYPE"
        show_help
        exit 1
        ;;
esac

print_header "EJECUTANDO SEED: $SEED_TYPE"

# Verificar si hay un proceso en el puerto 8080
if command -v lsof >/dev/null 2>&1; then
    if lsof -i :8080 >/dev/null 2>&1; then
        print_warning "Hay un proceso ejecutándose en el puerto 8080"
        print_info "Esto no afectará la ejecución del seed (solo usa la base de datos)"
    fi
fi

# Verificar dependencias
print_info "Verificando dependencias..."

if ! command -v java >/dev/null 2>&1; then
    print_error "Java no está instalado o no está en el PATH"
    exit 1
fi

if ! command -v ./gradlew >/dev/null 2>&1 && ! command -v gradle >/dev/null 2>&1; then
    print_error "Gradle no está disponible"
    exit 1
fi

# Compilar si es necesario
print_info "Verificando compilación..."
cd "$PROJECT_DIR"

if [ ! -d "build/classes" ] || [ ! -f "build/libs/auth-microservice-1.0.0-fat.jar" ]; then
    print_warning "Proyecto no compilado, compilando..."
    ./gradlew shadowJar --quiet
    
    if [ $? -ne 0 ]; then
        print_error "Error compilando el proyecto"
        exit 1
    fi
    print_success "Proyecto compilado exitosamente"
else
    print_info "Proyecto ya compilado"
fi

# Ejecutar seed usando el comando CLI integrado
print_info "Ejecutando seed a través del JAR principal..."

java -jar "$JAR_FILE" seed "$SEED_TYPE"

if [ $? -eq 0 ]; then
    print_success "Seed '$SEED_TYPE' ejecutado exitosamente"
    
    # Mostrar información adicional según el tipo
    case "$SEED_TYPE" in
        check)
            print_info "Usa 'basic' para crear datos básicos si no existen"
            ;;
        basic)
            print_info "Datos básicos creados. Usa 'test' para agregar usuarios de prueba"
            ;;
        fresh)
            print_warning "Todos los datos fueron recreados desde cero"
            ;;
    esac
else
    print_error "Error ejecutando seed '$SEED_TYPE'"
    exit 1
fi

print_header "SEED COMPLETADO"
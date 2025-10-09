#!/bin/bash

# Script simple para ejecutar seeds de la base de datos
# Uso: ./scripts/db-seed.sh [tipo]
# Tipos: all, basic, test, check, fresh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Colores
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

print_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
print_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
print_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Verificar argumentos
if [ $# -eq 0 ]; then
    echo "Uso: $0 [tipo]"
    echo "Tipos: all, basic, test, check, fresh"
    exit 1
fi

SEED_TYPE="$1"
JAR_FILE="$PROJECT_DIR/build/libs/auth-microservice-1.0.0-fat.jar"

# Compilar si es necesario
if [ ! -f "$JAR_FILE" ]; then
    print_info "Compilando proyecto..."
    cd "$PROJECT_DIR"
    ./gradlew shadowJar --quiet
fi

# Ejecutar seed
print_info "Ejecutando seed: $SEED_TYPE"
cd "$PROJECT_DIR"

if java -jar "$JAR_FILE" seed "$SEED_TYPE"; then
    print_success "Seed completado"
else
    print_error "Error en seed"
    exit 1
fi
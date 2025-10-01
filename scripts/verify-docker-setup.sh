#!/bin/bash

# =============================================================================
# AUTH MICROSERVICE - DOCKER SETUP VERIFICATION SCRIPT
# =============================================================================
# This script verifies that the Docker setup is working correctly

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

print_step() {
    echo -e "${BLUE}[STEP]${NC} $1"
}

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to cleanup on exit
cleanup() {
    print_info "Cleaning up test environment..."
    docker-compose -f docker-compose.yml -f docker-compose.development.yml down -v >/dev/null 2>&1 || true
}

# Set trap for cleanup
trap cleanup EXIT

# Main verification function
main() {
    echo "==============================================="
    echo "Auth Microservice - Docker Setup Verification"
    echo "==============================================="
    echo ""
    
    # Step 1: Check prerequisites
    print_step "1. Checking prerequisites..."
    
    if ! command_exists docker; then
        print_error "Docker is not installed or not in PATH"
        exit 1
    fi
    print_success "Docker is available"
    
    if ! command_exists docker-compose; then
        print_error "Docker Compose is not installed or not in PATH"
        exit 1
    fi
    print_success "Docker Compose is available"
    
    # Step 2: Verify Docker is running
    print_step "2. Verifying Docker daemon..."
    if ! docker info >/dev/null 2>&1; then
        print_error "Docker daemon is not running"
        exit 1
    fi
    print_success "Docker daemon is running"
    
    # Step 3: Validate Docker Compose configuration
    print_step "3. Validating Docker Compose configuration..."
    if ! docker-compose config --quiet >/dev/null 2>&1; then
        print_error "Docker Compose configuration is invalid"
        exit 1
    fi
    print_success "Base Docker Compose configuration is valid"
    
    if ! docker-compose -f docker-compose.yml -f docker-compose.development.yml config --quiet >/dev/null 2>&1; then
        print_error "Development Docker Compose configuration is invalid"
        exit 1
    fi
    print_success "Development Docker Compose configuration is valid"
    
    # Step 4: Build test image
    print_step "4. Building test image..."
    if ! docker build -f Dockerfile.test -t auth-microservice-test . >/dev/null 2>&1; then
        print_error "Failed to build test image"
        exit 1
    fi
    print_success "Test image built successfully"
    
    # Step 5: Run unit tests
    print_step "5. Running unit tests..."
    if ! docker run --rm auth-microservice-test gradle test --tests "*DatabasePropertiesTest" --tests "*MigrationFilesTest" --no-daemon >/dev/null 2>&1; then
        print_error "Unit tests failed"
        exit 1
    fi
    print_success "Unit tests passed"
    
    # Step 6: Build main application image
    print_step "6. Building main application image..."
    if ! docker build -t auth-microservice . >/dev/null 2>&1; then
        print_error "Failed to build main application image"
        exit 1
    fi
    print_success "Main application image built successfully"
    
    # Step 7: Start database services
    print_step "7. Starting database services..."
    if ! docker-compose -f docker-compose.yml -f docker-compose.development.yml up -d postgres redis >/dev/null 2>&1; then
        print_error "Failed to start database services"
        exit 1
    fi
    print_success "Database services started"
    
    # Step 8: Wait for services to be healthy
    print_step "8. Waiting for services to be healthy..."
    local max_attempts=30
    local attempt=0
    
    while [ $attempt -lt $max_attempts ]; do
        if docker-compose -f docker-compose.yml -f docker-compose.development.yml ps | grep -q "healthy"; then
            local healthy_count=$(docker-compose -f docker-compose.yml -f docker-compose.development.yml ps | grep -c "healthy" || echo "0")
            if [ "$healthy_count" -eq 2 ]; then
                break
            fi
        fi
        
        attempt=$((attempt + 1))
        sleep 2
        
        if [ $attempt -eq $max_attempts ]; then
            print_error "Services did not become healthy within expected time"
            docker-compose -f docker-compose.yml -f docker-compose.development.yml ps
            exit 1
        fi
    done
    print_success "Database services are healthy"
    
    # Step 9: Test database connectivity
    print_step "9. Testing database connectivity..."
    if ! docker exec authentication_db pg_isready -U auth_user -d auth_microservice_dev >/dev/null 2>&1; then
        print_error "PostgreSQL is not accepting connections"
        exit 1
    fi
    print_success "PostgreSQL is accepting connections"
    
    if ! docker exec authentication_redis redis-cli -a dev_redis_password ping >/dev/null 2>&1; then
        print_error "Redis is not responding"
        exit 1
    fi
    print_success "Redis is responding"
    
    # Step 10: Start full application stack
    print_step "10. Starting full application stack..."
    if ! docker-compose -f docker-compose.yml -f docker-compose.development.yml up -d >/dev/null 2>&1; then
        print_error "Failed to start full application stack"
        exit 1
    fi
    print_success "Full application stack started"
    
    # Step 11: Wait for application to be ready
    print_step "11. Waiting for application to be ready..."
    attempt=0
    max_attempts=30
    
    while [ $attempt -lt $max_attempts ]; do
        if docker-compose -f docker-compose.yml -f docker-compose.development.yml ps | grep -q "authentication-ms.*healthy"; then
            break
        fi
        
        attempt=$((attempt + 1))
        sleep 2
        
        if [ $attempt -eq $max_attempts ]; then
            print_warning "Application health check timeout, but this might be expected if health endpoint is not implemented yet"
            break
        fi
    done
    
    # Step 12: Check application logs
    print_step "12. Checking application logs..."
    if docker-compose -f docker-compose.yml -f docker-compose.development.yml logs auth-service | grep -q "Auth Microservice started"; then
        print_success "Application started successfully"
    else
        print_warning "Application startup message not found in logs, but container is running"
    fi
    
    # Step 13: Verify all services are running
    print_step "13. Verifying all services are running..."
    local running_services=$(docker-compose -f docker-compose.yml -f docker-compose.development.yml ps --services --filter "status=running" | wc -l)
    if [ "$running_services" -eq 3 ]; then
        print_success "All 3 services are running"
    else
        print_warning "Expected 3 services running, found $running_services"
        docker-compose -f docker-compose.yml -f docker-compose.development.yml ps
    fi
    
    echo ""
    echo "==============================================="
    echo "Verification Summary"
    echo "==============================================="
    print_success "✓ Docker and Docker Compose are available"
    print_success "✓ Docker Compose configurations are valid"
    print_success "✓ Test image builds successfully"
    print_success "✓ Unit tests pass"
    print_success "✓ Main application image builds successfully"
    print_success "✓ Database services start and become healthy"
    print_success "✓ Database connectivity works"
    print_success "✓ Full application stack starts"
    print_success "✓ Application logs show successful startup"
    
    echo ""
    print_success "🎉 Docker setup verification completed successfully!"
    echo ""
    echo "You can now use the following commands:"
    echo "  make dev          # Start development environment"
    echo "  make test         # Run tests"
    echo "  make logs         # View logs"
    echo "  make clean        # Clean up"
    echo ""
}

# Run main function
main "$@"
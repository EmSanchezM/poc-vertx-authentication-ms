#!/bin/bash

# =============================================================================
# AUTH MICROSERVICE - DOCKER ENVIRONMENT MANAGEMENT SCRIPT
# =============================================================================
# This script manages Docker environments for the Auth Microservice

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

# Function to show usage
show_usage() {
    echo "Usage: $0 <command> [environment] [options]"
    echo ""
    echo "Commands:"
    echo "  up       - Start services"
    echo "  down     - Stop services"
    echo "  restart  - Restart services"
    echo "  logs     - Show logs"
    echo "  build    - Build images"
    echo "  test     - Run tests"
    echo "  clean    - Clean up containers and volumes"
    echo "  status   - Show service status"
    echo ""
    echo "Environments:"
    echo "  development  - Development environment (default)"
    echo "  qa           - QA/staging environment"
    echo "  production   - Production environment"
    echo ""
    echo "Options:"
    echo "  --build      - Force rebuild images"
    echo "  --detach     - Run in background"
    echo "  --follow     - Follow logs"
    echo "  --service    - Target specific service"
    echo ""
    echo "Examples:"
    echo "  $0 up development"
    echo "  $0 up production --build"
    echo "  $0 logs qa --follow"
    echo "  $0 test development"
    echo ""
}

# Function to validate environment
validate_environment() {
    local env=$1
    case $env in
        development|qa|production)
            return 0
            ;;
        *)
            print_error "Invalid environment: $env"
            print_info "Valid environments: development, qa, production"
            exit 1
            ;;
    esac
}

# Function to get compose files for environment
get_compose_files() {
    local env=$1
    echo "-f docker-compose.yml -f docker-compose.${env}.yml"
}

# Function to load environment file
load_env_file() {
    local env=$1
    local env_file=".env.${env}"
    
    if [ -f "$env_file" ]; then
        print_info "Loading environment file: $env_file"
        export $(grep -v '^#' "$env_file" | xargs)
    else
        print_warning "Environment file $env_file not found, using defaults"
    fi
}

# Function to start services
start_services() {
    local env=$1
    local build_flag=$2
    local detach_flag=$3
    
    validate_environment "$env"
    load_env_file "$env"
    
    local compose_files=$(get_compose_files "$env")
    local docker_cmd="docker-compose $compose_files"
    
    print_info "Starting Auth Microservice in $env environment..."
    
    if [ "$build_flag" = "--build" ]; then
        print_info "Building images..."
        $docker_cmd build
    fi
    
    if [ "$detach_flag" = "--detach" ]; then
        $docker_cmd up -d
    else
        $docker_cmd up
    fi
    
    print_success "Services started successfully!"
}

# Function to stop services
stop_services() {
    local env=$1
    
    validate_environment "$env"
    
    local compose_files=$(get_compose_files "$env")
    local docker_cmd="docker-compose $compose_files"
    
    print_info "Stopping Auth Microservice services..."
    $docker_cmd down
    
    print_success "Services stopped successfully!"
}

# Function to restart services
restart_services() {
    local env=$1
    local build_flag=$2
    
    validate_environment "$env"
    
    print_info "Restarting Auth Microservice services..."
    stop_services "$env"
    start_services "$env" "$build_flag" "--detach"
    
    print_success "Services restarted successfully!"
}

# Function to show logs
show_logs() {
    local env=$1
    local follow_flag=$2
    local service=$3
    
    validate_environment "$env"
    
    local compose_files=$(get_compose_files "$env")
    local docker_cmd="docker-compose $compose_files"
    
    if [ "$follow_flag" = "--follow" ]; then
        if [ -n "$service" ]; then
            $docker_cmd logs -f "$service"
        else
            $docker_cmd logs -f
        fi
    else
        if [ -n "$service" ]; then
            $docker_cmd logs "$service"
        else
            $docker_cmd logs
        fi
    fi
}

# Function to build images
build_images() {
    local env=$1
    
    validate_environment "$env"
    load_env_file "$env"
    
    local compose_files=$(get_compose_files "$env")
    local docker_cmd="docker-compose $compose_files"
    
    print_info "Building images for $env environment..."
    $docker_cmd build --no-cache
    
    print_success "Images built successfully!"
}

# Function to run tests
run_tests() {
    local env=${1:-development}
    
    print_info "Running tests in Docker..."
    
    # Build test image
    docker build -f Dockerfile.test -t auth-microservice-test .
    
    # Run unit tests
    print_info "Running unit tests..."
    docker run --rm auth-microservice-test gradle test --tests "*DatabasePropertiesTest" --tests "*MigrationFilesTest" --no-daemon
    
    # Run integration tests with testcontainers (if Docker-in-Docker is available)
    if docker info >/dev/null 2>&1; then
        print_info "Running integration tests..."
        docker run --rm -v /var/run/docker.sock:/var/run/docker.sock auth-microservice-test gradle test --no-daemon || print_warning "Integration tests skipped (Docker-in-Docker not available)"
    fi
    
    print_success "Tests completed!"
}

# Function to clean up
clean_up() {
    local env=$1
    
    validate_environment "$env"
    
    local compose_files=$(get_compose_files "$env")
    local docker_cmd="docker-compose $compose_files"
    
    print_warning "This will remove all containers, networks, and volumes for $env environment"
    read -p "Are you sure? (y/N): " -n 1 -r
    echo
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        print_info "Cleaning up $env environment..."
        $docker_cmd down -v --remove-orphans
        docker system prune -f
        print_success "Cleanup completed!"
    else
        print_info "Cleanup cancelled"
    fi
}

# Function to show service status
show_status() {
    local env=$1
    
    validate_environment "$env"
    
    local compose_files=$(get_compose_files "$env")
    local docker_cmd="docker-compose $compose_files"
    
    print_info "Service status for $env environment:"
    $docker_cmd ps
    
    echo ""
    print_info "Docker system information:"
    docker system df
}

# Main execution
main() {
    local command=${1:-}
    local environment=${2:-development}
    local option1=${3:-}
    local option2=${4:-}
    local option3=${5:-}
    
    if [ -z "$command" ]; then
        print_error "Command not specified"
        show_usage
        exit 1
    fi
    
    case $command in
        up)
            start_services "$environment" "$option1" "$option2"
            ;;
        down)
            stop_services "$environment"
            ;;
        restart)
            restart_services "$environment" "$option1"
            ;;
        logs)
            show_logs "$environment" "$option1" "$option2"
            ;;
        build)
            build_images "$environment"
            ;;
        test)
            run_tests "$environment"
            ;;
        clean)
            clean_up "$environment"
            ;;
        status)
            show_status "$environment"
            ;;
        *)
            print_error "Invalid command: $command"
            show_usage
            exit 1
            ;;
    esac
}

# Run main function with all arguments
main "$@"
#!/bin/bash

# =============================================================================
# AUTH MICROSERVICE - ENVIRONMENT SETUP SCRIPT
# =============================================================================
# This script helps set up the environment for different deployment scenarios

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
    echo "Usage: $0 [ENVIRONMENT]"
    echo ""
    echo "Available environments:"
    echo "  development  - Set up development environment"
    echo "  qa           - Set up QA/staging environment"
    echo "  production   - Set up production environment"
    echo "  docker       - Set up for Docker deployment"
    echo ""
    echo "Examples:"
    echo "  $0 development"
    echo "  $0 production"
    echo ""
}

# Function to validate environment
validate_environment() {
    local env=$1
    case $env in
        development|qa|production|docker)
            return 0
            ;;
        *)
            print_error "Invalid environment: $env"
            show_usage
            exit 1
            ;;
    esac
}

# Function to copy environment file
setup_environment_file() {
    local env=$1
    local source_file=".env.${env}"
    local target_file=".env"
    
    if [ ! -f "$source_file" ]; then
        print_error "Environment file $source_file not found!"
        exit 1
    fi
    
    print_info "Setting up environment: $env"
    
    # Backup existing .env if it exists
    if [ -f "$target_file" ]; then
        cp "$target_file" "${target_file}.backup.$(date +%Y%m%d_%H%M%S)"
        print_info "Backed up existing .env file"
    fi
    
    # Copy environment-specific file
    cp "$source_file" "$target_file"
    print_success "Environment file copied: $source_file -> $target_file"
}

# Function to set Java system property for Spring profiles
set_spring_profile() {
    local env=$1
    local profile_file="application.profile"
    
    echo "spring.profiles.active=$env" > "$profile_file"
    print_success "Spring profile set to: $env"
}

# Function to validate required environment variables
validate_required_vars() {
    local env=$1
    local missing_vars=()
    
    # Load the environment file
    if [ -f ".env" ]; then
        export $(grep -v '^#' .env | xargs)
    fi
    
    # Check required variables based on environment
    case $env in
        production)
            required_vars=(
                "DB_PASSWORD"
                "REDIS_PASSWORD" 
                "JWT_SECRET"
                "EMAIL_SMTP_PASSWORD"
                "GEOLOCATION_API_KEY"
            )
            ;;
        qa)
            required_vars=(
                "DB_PASSWORD"
                "REDIS_PASSWORD"
                "JWT_SECRET"
            )
            ;;
        development)
            required_vars=(
                "DB_PASSWORD"
            )
            ;;
        *)
            required_vars=()
            ;;
    esac
    
    for var in "${required_vars[@]}"; do
        if [ -z "${!var}" ]; then
            missing_vars+=("$var")
        fi
    done
    
    if [ ${#missing_vars[@]} -gt 0 ]; then
        print_warning "The following environment variables are not set:"
        for var in "${missing_vars[@]}"; do
            echo "  - $var"
        done
        print_warning "Please set these variables in your .env file or environment"
    else
        print_success "All required environment variables are set"
    fi
}

# Function to create necessary directories
create_directories() {
    local dirs=("logs" "data" "tmp")
    
    for dir in "${dirs[@]}"; do
        if [ ! -d "$dir" ]; then
            mkdir -p "$dir"
            print_info "Created directory: $dir"
        fi
    done
}

# Function to set appropriate file permissions
set_permissions() {
    local env=$1
    
    # Set restrictive permissions for environment files
    chmod 600 .env 2>/dev/null || true
    chmod 600 .env.* 2>/dev/null || true
    
    # Set permissions for log directory
    if [ -d "logs" ]; then
        chmod 755 logs
    fi
    
    print_success "File permissions set appropriately"
}

# Function to show environment summary
show_summary() {
    local env=$1
    
    echo ""
    echo "==============================================="
    echo "Environment Setup Summary"
    echo "==============================================="
    echo "Environment: $env"
    echo "Configuration file: .env"
    echo "Spring profile: $env"
    echo ""
    
    if [ -f ".env" ]; then
        echo "Key configuration values:"
        echo "  APP_ENV: $(grep '^APP_ENV=' .env | cut -d'=' -f2)"
        echo "  SERVER_PORT: $(grep '^SERVER_PORT=' .env | cut -d'=' -f2)"
        echo "  DB_HOST: $(grep '^DB_HOST=' .env | cut -d'=' -f2)"
        echo "  LOG_LEVEL: $(grep '^LOG_LEVEL=' .env | cut -d'=' -f2)"
    fi
    
    echo ""
    echo "Next steps:"
    echo "1. Review and update .env file with your specific values"
    echo "2. Ensure all required services are running (PostgreSQL, Redis)"
    echo "3. Run the application with: ./gradlew run"
    echo ""
}

# Main execution
main() {
    local environment=${1:-}
    
    if [ -z "$environment" ]; then
        print_error "Environment not specified"
        show_usage
        exit 1
    fi
    
    validate_environment "$environment"
    
    print_info "Setting up Auth Microservice for environment: $environment"
    
    setup_environment_file "$environment"
    set_spring_profile "$environment"
    create_directories
    set_permissions "$environment"
    validate_required_vars "$environment"
    show_summary "$environment"
    
    print_success "Environment setup completed successfully!"
}

# Run main function with all arguments
main "$@"
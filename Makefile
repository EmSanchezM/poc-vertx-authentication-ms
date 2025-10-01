# =============================================================================
# AUTH MICROSERVICE - MAKEFILE
# =============================================================================
# Simplified Docker commands for Auth Microservice

.PHONY: help dev qa prod build test clean logs status

# Default environment
ENV ?= development

# Docker compose files
COMPOSE_DEV = -f docker-compose.yml -f docker-compose.development.yml
COMPOSE_QA = -f docker-compose.yml -f docker-compose.qa.yml
COMPOSE_PROD = -f docker-compose.yml -f docker-compose.production.yml

# Colors for output
BLUE = \033[0;34m
GREEN = \033[0;32m
YELLOW = \033[1;33m
RED = \033[0;31m
NC = \033[0m # No Color

# Help target
help: ## Show this help message
	@echo "$(BLUE)Auth Microservice - Docker Commands$(NC)"
	@echo ""
	@echo "$(GREEN)Available targets:$(NC)"
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  $(YELLOW)%-15s$(NC) %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo ""
	@echo "$(GREEN)Environment variables:$(NC)"
	@echo "  $(YELLOW)ENV$(NC)              Environment to use (development, qa, production)"
	@echo ""
	@echo "$(GREEN)Examples:$(NC)"
	@echo "  make dev             # Start development environment"
	@echo "  make qa              # Start QA environment"
	@echo "  make prod            # Start production environment"
	@echo "  make test            # Run tests"
	@echo "  make logs ENV=qa     # Show QA logs"
	@echo ""

# Development environment
dev: ## Start development environment
	@echo "$(BLUE)Starting development environment...$(NC)"
	@if [ -f .env.development ]; then export $$(grep -v '^#' .env.development | xargs); fi
	@docker-compose $(COMPOSE_DEV) up --build

dev-detach: ## Start development environment in background
	@echo "$(BLUE)Starting development environment in background...$(NC)"
	@if [ -f .env.development ]; then export $$(grep -v '^#' .env.development | xargs); fi
	@docker-compose $(COMPOSE_DEV) up -d --build

dev-down: ## Stop development environment
	@echo "$(BLUE)Stopping development environment...$(NC)"
	@docker-compose $(COMPOSE_DEV) down

# QA environment
qa: ## Start QA environment
	@echo "$(BLUE)Starting QA environment...$(NC)"
	@if [ -f .env.qa ]; then export $$(grep -v '^#' .env.qa | xargs); fi
	@docker-compose $(COMPOSE_QA) up --build

qa-detach: ## Start QA environment in background
	@echo "$(BLUE)Starting QA environment in background...$(NC)"
	@if [ -f .env.qa ]; then export $$(grep -v '^#' .env.qa | xargs); fi
	@docker-compose $(COMPOSE_QA) up -d --build

qa-down: ## Stop QA environment
	@echo "$(BLUE)Stopping QA environment...$(NC)"
	@docker-compose $(COMPOSE_QA) down

# Production environment
prod: ## Start production environment
	@echo "$(BLUE)Starting production environment...$(NC)"
	@if [ -f .env.production ]; then export $$(grep -v '^#' .env.production | xargs); fi
	@docker-compose $(COMPOSE_PROD) up --build

prod-detach: ## Start production environment in background
	@echo "$(BLUE)Starting production environment in background...$(NC)"
	@if [ -f .env.production ]; then export $$(grep -v '^#' .env.production | xargs); fi
	@docker-compose $(COMPOSE_PROD) up -d --build

prod-down: ## Stop production environment
	@echo "$(BLUE)Stopping production environment...$(NC)"
	@docker-compose $(COMPOSE_PROD) down

# Build targets
build: ## Build images for specified environment
	@echo "$(BLUE)Building images for $(ENV) environment...$(NC)"
	@$(MAKE) build-$(ENV)

build-development: ## Build development images
	@docker-compose $(COMPOSE_DEV) build --no-cache

build-qa: ## Build QA images
	@docker-compose $(COMPOSE_QA) build --no-cache

build-production: ## Build production images
	@docker-compose $(COMPOSE_PROD) build --no-cache

# Test targets
test: ## Run tests in Docker
	@echo "$(BLUE)Running tests...$(NC)"
	@docker build -f Dockerfile.test -t auth-microservice-test .
	@echo "$(GREEN)Running unit tests...$(NC)"
	@docker run --rm auth-microservice-test gradle test --tests "*DatabasePropertiesTest" --tests "*MigrationFilesTest" --no-daemon
	@echo "$(GREEN)Tests completed!$(NC)"

test-integration: ## Run integration tests (requires Docker-in-Docker)
	@echo "$(BLUE)Running integration tests...$(NC)"
	@docker build -f Dockerfile.test -t auth-microservice-test .
	@docker run --rm -v /var/run/docker.sock:/var/run/docker.sock auth-microservice-test gradle test --no-daemon

# Log targets
logs: ## Show logs for specified environment
	@$(MAKE) logs-$(ENV)

logs-development: ## Show development logs
	@docker-compose $(COMPOSE_DEV) logs -f

logs-qa: ## Show QA logs
	@docker-compose $(COMPOSE_QA) logs -f

logs-production: ## Show production logs
	@docker-compose $(COMPOSE_PROD) logs -f

logs-app: ## Show application logs only
	@$(MAKE) logs-app-$(ENV)

logs-app-development: ## Show development application logs
	@docker-compose $(COMPOSE_DEV) logs -f auth-service

logs-app-qa: ## Show QA application logs
	@docker-compose $(COMPOSE_QA) logs -f auth-service

logs-app-production: ## Show production application logs
	@docker-compose $(COMPOSE_PROD) logs -f auth-service

# Status targets
status: ## Show service status for specified environment
	@$(MAKE) status-$(ENV)

status-development: ## Show development service status
	@echo "$(BLUE)Development environment status:$(NC)"
	@docker-compose $(COMPOSE_DEV) ps

status-qa: ## Show QA service status
	@echo "$(BLUE)QA environment status:$(NC)"
	@docker-compose $(COMPOSE_QA) ps

status-production: ## Show production service status
	@echo "$(BLUE)Production environment status:$(NC)"
	@docker-compose $(COMPOSE_PROD) ps

# Utility targets
clean: ## Clean up containers and volumes for specified environment
	@echo "$(YELLOW)Warning: This will remove all containers and volumes for $(ENV) environment$(NC)"
	@read -p "Are you sure? (y/N): " confirm && [ "$$confirm" = "y" ] || exit 1
	@$(MAKE) clean-$(ENV)

clean-development: ## Clean up development environment
	@docker-compose $(COMPOSE_DEV) down -v --remove-orphans
	@docker system prune -f

clean-qa: ## Clean up QA environment
	@docker-compose $(COMPOSE_QA) down -v --remove-orphans
	@docker system prune -f

clean-production: ## Clean up production environment
	@docker-compose $(COMPOSE_PROD) down -v --remove-orphans
	@docker system prune -f

clean-all: ## Clean up all environments and images
	@echo "$(RED)Warning: This will remove ALL containers, volumes, and images$(NC)"
	@read -p "Are you sure? (y/N): " confirm && [ "$$confirm" = "y" ] || exit 1
	@docker-compose $(COMPOSE_DEV) down -v --remove-orphans 2>/dev/null || true
	@docker-compose $(COMPOSE_QA) down -v --remove-orphans 2>/dev/null || true
	@docker-compose $(COMPOSE_PROD) down -v --remove-orphans 2>/dev/null || true
	@docker system prune -af
	@docker volume prune -f

# Database targets
db-migrate: ## Run database migrations
	@echo "$(BLUE)Running database migrations for $(ENV) environment...$(NC)"
	@$(MAKE) db-migrate-$(ENV)

db-migrate-development: ## Run development database migrations
	@docker-compose $(COMPOSE_DEV) exec auth-service java -cp app.jar com.auth.microservice.infrastructure.migration.FlywayMigrationService

db-migrate-qa: ## Run QA database migrations
	@docker-compose $(COMPOSE_QA) exec auth-service java -cp app.jar com.auth.microservice.infrastructure.migration.FlywayMigrationService

db-migrate-production: ## Run production database migrations
	@docker-compose $(COMPOSE_PROD) exec auth-service java -cp app.jar com.auth.microservice.infrastructure.migration.FlywayMigrationService

# Health check targets
health: ## Check service health for specified environment
	@$(MAKE) health-$(ENV)

health-development: ## Check development service health
	@echo "$(BLUE)Checking development service health...$(NC)"
	@curl -f http://localhost:8080/health || echo "$(RED)Service is not healthy$(NC)"

health-qa: ## Check QA service health
	@echo "$(BLUE)Checking QA service health...$(NC)"
	@curl -f http://localhost:8080/health || echo "$(RED)Service is not healthy$(NC)"

health-production: ## Check production service health
	@echo "$(BLUE)Checking production service health...$(NC)"
	@curl -f http://localhost:8080/health || echo "$(RED)Service is not healthy$(NC)"

# Default target
.DEFAULT_GOAL := help
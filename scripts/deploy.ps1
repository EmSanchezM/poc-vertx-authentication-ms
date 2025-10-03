# =============================================================================
# AUTH MICROSERVICE - DEPLOYMENT SCRIPT (PowerShell)
# =============================================================================
# Script para desplegar el microservicio de autenticación en diferentes ambientes

param(
    [Parameter(Position=0)]
    [ValidateSet("development", "qa", "production")]
    [string]$Environment = "development",
    
    [switch]$Monitoring,
    [switch]$SkipTests,
    [switch]$ForceRebuild,
    [switch]$Help
)

# Configuración de colores
$Red = "Red"
$Green = "Green"
$Yellow = "Yellow"
$Blue = "Blue"

# Variables
$ComposeProjectName = "auth-microservice"
$DockerComposeFiles = "-f docker-compose.yml"

# Funciones de logging
function Write-Log {
    param([string]$Message, [string]$Color = "White")
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Host "[$timestamp] $Message" -ForegroundColor $Color
}

function Write-Success {
    param([string]$Message)
    Write-Log "✓ $Message" -Color $Green
}

function Write-Warning {
    param([string]$Message)
    Write-Log "⚠ $Message" -Color $Yellow
}

function Write-Error {
    param([string]$Message)
    Write-Log "✗ $Message" -Color $Red
}

# Función para mostrar ayuda
function Show-Help {
    Write-Host @"
Auth Microservice Deployment Script (PowerShell)

USAGE:
    .\scripts\deploy.ps1 [ENVIRONMENT] [OPTIONS]

ENVIRONMENTS:
    development     Deploy for development (default)
    qa              Deploy for QA/staging
    production      Deploy for production

OPTIONS:
    -Monitoring     Enable monitoring stack (Prometheus, Grafana, AlertManager)
    -SkipTests      Skip running tests during build
    -ForceRebuild   Force rebuild of Docker images
    -Help           Show this help message

EXAMPLES:
    .\scripts\deploy.ps1 development
    .\scripts\deploy.ps1 production -Monitoring
    .\scripts\deploy.ps1 qa -SkipTests -Monitoring
"@
}

# Mostrar ayuda si se solicita
if ($Help) {
    Show-Help
    exit 0
}

# Configurar archivos de compose según el ambiente
switch ($Environment) {
    "development" { $DockerComposeFiles += " -f docker-compose.development.yml" }
    "qa" { $DockerComposeFiles += " -f docker-compose.qa.yml" }
    "production" { $DockerComposeFiles += " -f docker-compose.production.yml" }
}

# Agregar monitoring si está habilitado
if ($Monitoring) {
    $DockerComposeFiles += " --profile monitoring"
}

Write-Log "=== INICIANDO DEPLOYMENT DE AUTH MICROSERVICE ===" -Color $Blue
Write-Log "Ambiente: $Environment"
Write-Log "Monitoring: $Monitoring"
Write-Log "Skip Tests: $SkipTests"
Write-Log "Force Rebuild: $ForceRebuild"
Write-Log "Compose Files: $DockerComposeFiles"

# Verificar prerrequisitos
Write-Log "Verificando prerrequisitos..."

# Verificar Docker
try {
    $dockerVersion = docker --version
    Write-Log "Docker encontrado: $dockerVersion"
} catch {
    Write-Error "Docker no está instalado o no está en el PATH"
    exit 1
}

# Verificar Docker Compose
$dockerComposeCmd = "docker-compose"
try {
    $composeVersion = docker-compose --version
    Write-Log "Docker Compose encontrado: $composeVersion"
} catch {
    try {
        $composeVersion = docker compose version
        $dockerComposeCmd = "docker compose"
        Write-Log "Docker Compose (plugin) encontrado: $composeVersion"
    } catch {
        Write-Error "Docker Compose no está instalado"
        exit 1
    }
}

Write-Success "Prerrequisitos verificados"

# Verificar archivos de configuración
Write-Log "Verificando archivos de configuración..."

$requiredFiles = @(
    "docker-compose.yml",
    "docker-compose.$Environment.yml",
    "Dockerfile",
    ".env.$Environment"
)

foreach ($file in $requiredFiles) {
    if (-not (Test-Path $file)) {
        Write-Error "Archivo requerido no encontrado: $file"
        exit 1
    }
}

Write-Success "Archivos de configuración verificados"

# Cargar variables de entorno
$envFile = ".env.$Environment"
if (Test-Path $envFile) {
    Write-Log "Cargando variables de entorno desde $envFile"
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^([^#][^=]+)=(.*)$') {
            [Environment]::SetEnvironmentVariable($matches[1], $matches[2], "Process")
        }
    }
}

# Ejecutar tests si no se omiten
if (-not $SkipTests) {
    Write-Log "Ejecutando tests..."
    $testResult = & $dockerComposeCmd -f docker-compose.test.yml run --rm test-runner
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Tests fallaron"
        exit 1
    }
    Write-Success "Tests completados exitosamente"
}

# Detener servicios existentes
Write-Log "Deteniendo servicios existentes..."
$stopCmd = "$dockerComposeCmd $DockerComposeFiles -p $ComposeProjectName down --remove-orphans"
Invoke-Expression $stopCmd

# Construir imágenes
$buildArgs = ""
if ($ForceRebuild) {
    $buildArgs += " --no-cache"
}

if ($SkipTests) {
    $buildArgs += " --build-arg SKIP_TESTS=true"
}

Write-Log "Construyendo imágenes..."
$buildCmd = "$dockerComposeCmd $DockerComposeFiles -p $ComposeProjectName build$buildArgs"
Invoke-Expression $buildCmd

if ($LASTEXITCODE -ne 0) {
    Write-Error "Error construyendo imágenes"
    exit 1
}

# Iniciar servicios
Write-Log "Iniciando servicios..."
$startCmd = "$dockerComposeCmd $DockerComposeFiles -p $ComposeProjectName up -d"
Invoke-Expression $startCmd

if ($LASTEXITCODE -ne 0) {
    Write-Error "Error iniciando servicios"
    exit 1
}

# Esperar a que los servicios estén listos
Write-Log "Esperando a que los servicios estén listos..."
Start-Sleep -Seconds 10

# Función para verificar salud de un servicio
function Test-ServiceHealth {
    param(
        [string]$ServiceName,
        [string]$HealthUrl,
        [int]$MaxAttempts = 30
    )
    
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        try {
            $response = Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -eq 200) {
                Write-Success "$ServiceName está saludable"
                return $true
            }
        } catch {
            # Continuar intentando
        }
        
        Write-Log "Intento $attempt/$MaxAttempts`: Esperando a que $ServiceName esté listo..."
        Start-Sleep -Seconds 5
    }
    
    Write-Error "$ServiceName no está respondiendo después de $MaxAttempts intentos"
    return $false
}

# Verificar servicios principales
Write-Log "Verificando salud de los servicios..."

$servicesToCheck = @(
    @{ Name = "Auth Service"; Url = "http://localhost:8080/health" }
)

if ($Monitoring) {
    $servicesToCheck += @(
        @{ Name = "Prometheus"; Url = "http://localhost:9090/-/healthy" },
        @{ Name = "Grafana"; Url = "http://localhost:3000/api/health" }
    )
}

$allHealthy = $true
foreach ($service in $servicesToCheck) {
    if (-not (Test-ServiceHealth -ServiceName $service.Name -HealthUrl $service.Url)) {
        $allHealthy = $false
    }
}

if (-not $allHealthy) {
    Write-Error "Algunos servicios no están saludables"
    Write-Log "Mostrando logs de los servicios..."
    $logsCmd = "$dockerComposeCmd $DockerComposeFiles -p $ComposeProjectName logs --tail=50"
    Invoke-Expression $logsCmd
    exit 1
}

# Mostrar información de deployment
Write-Success "=== DEPLOYMENT COMPLETADO EXITOSAMENTE ===" -Color $Green
Write-Host ""
Write-Log "Información del deployment:"
Write-Log "  Ambiente: $Environment"
Write-Log "  Proyecto: $ComposeProjectName"
Write-Host ""
Write-Log "Servicios disponibles:"
Write-Log "  - Auth Service: http://localhost:8080"
Write-Log "  - Health Check: http://localhost:8080/health"
Write-Log "  - Métricas: http://localhost:8080/metrics"
Write-Log "  - Info: http://localhost:8080/info"

if ($Monitoring) {
    Write-Host ""
    Write-Log "Monitoreo disponible:"
    Write-Log "  - Prometheus: http://localhost:9090"
    Write-Log "  - Grafana: http://localhost:3000 (admin/admin)"
    Write-Log "  - AlertManager: http://localhost:9093"
}

Write-Host ""
Write-Log "Comandos útiles:"
Write-Log "  Ver logs: $dockerComposeCmd $DockerComposeFiles -p $ComposeProjectName logs -f"
Write-Log "  Detener: $dockerComposeCmd $DockerComposeFiles -p $ComposeProjectName down"
Write-Log "  Reiniciar: $dockerComposeCmd $DockerComposeFiles -p $ComposeProjectName restart"
Write-Host ""
Write-Success "Deployment completado en ambiente: $Environment"
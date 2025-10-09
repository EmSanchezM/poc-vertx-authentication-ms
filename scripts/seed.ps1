# Script para ejecutar seeds de la base de datos
# Uso: .\scripts\seed.ps1 [all|basic|test|check]

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("all", "basic", "test", "check")]
    [string]$SeedType
)

$ErrorActionPreference = "Stop"

# Obtener directorios
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir

# Funciones para output con colores
function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor Blue
}

function Write-Success {
    param([string]$Message)
    Write-Host "[SUCCESS] $Message" -ForegroundColor Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "[WARNING] $Message" -ForegroundColor Yellow
}

function Write-Error {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

Write-Info "Ejecutando seed tipo: $SeedType"

# Verificar si el JAR existe
$JarFile = Join-Path $ProjectDir "build\libs\auth-microservice-1.0.0-fat.jar"

if (-not (Test-Path $JarFile)) {
    Write-Warning "JAR no encontrado, compilando proyecto..."
    Set-Location $ProjectDir
    
    if (Get-Command "gradlew.bat" -ErrorAction SilentlyContinue) {
        .\gradlew.bat shadowJar
    } elseif (Get-Command "gradle" -ErrorAction SilentlyContinue) {
        gradle shadowJar
    } else {
        Write-Error "Gradle no encontrado. Instala Gradle o usa el wrapper."
        exit 1
    }
    
    if (-not (Test-Path $JarFile)) {
        Write-Error "Error compilando el proyecto"
        exit 1
    }
}

# Ejecutar seed
Write-Info "Ejecutando comando: java -jar $JarFile seed $SeedType"

Set-Location $ProjectDir

try {
    java -jar $JarFile seed $SeedType
    Write-Success "Seed ejecutado exitosamente"
} catch {
    Write-Error "Error ejecutando seed: $_"
    exit 1
}
# Script para ejecutar seeds de la base de datos
# Uso: .\scripts\seed.ps1 [all|basic|test|check|fresh]
# Similar a 'php artisan db:seed' de Laravel

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("all", "basic", "test", "check", "fresh")]
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

function Write-Header {
    param([string]$Message)
    Write-Host "=== $Message ===" -ForegroundColor Cyan
}

# Función para mostrar ayuda
function Show-Help {
    Write-Host "Uso: .\scripts\seed.ps1 [TIPO]"
    Write-Host ""
    Write-Host "Tipos de seed disponibles:"
    Write-Host "  all    - Ejecuta todos los seeds (permisos, roles, usuarios)"
    Write-Host "  basic  - Ejecuta solo datos básicos (permisos, roles, admin)"
    Write-Host "  test   - Ejecuta solo usuarios de prueba"
    Write-Host "  check  - Verifica si existen datos básicos"
    Write-Host "  fresh  - Limpia y recrea todos los datos (¡CUIDADO!)"
    Write-Host ""
    Write-Host "Ejemplos:"
    Write-Host "  .\scripts\seed.ps1 all     # Seed completo"
    Write-Host "  .\scripts\seed.ps1 basic   # Solo permisos y roles"
    Write-Host "  .\scripts\seed.ps1 test    # Solo usuarios de prueba"
    Write-Host "  .\scripts\seed.ps1 check   # Verificar datos"
    Write-Host "  .\scripts\seed.ps1 fresh   # Limpiar y recrear todo"
    Write-Host ""
    Write-Host "Nota: Este script NO inicia el servidor, solo ejecuta los seeds."
}

Write-Header "EJECUTANDO SEED: $SeedType"

# Verificar si hay un proceso en el puerto 8080
try {
    $process = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue
    if ($process) {
        Write-Warning "Hay un proceso ejecutándose en el puerto 8080"
        Write-Info "Esto no afectará la ejecución del seed (solo usa la base de datos)"
    }
} catch {
    # Ignorar errores de verificación de puerto
}

# Verificar dependencias
Write-Info "Verificando dependencias..."

# Verificar Java
$javaVersion = $null
try {
    $javaOutput = java -version 2>&1
    $javaVersion = ($javaOutput | Select-String "version").Line
    Write-Info "Java encontrado: $javaVersion"
} catch {
    Write-Error "Java no está instalado o no está en el PATH"
    Write-Info "Instala Java 21+ y asegúrate de que esté en el PATH"
    exit 1
}

# Verificar JAVA_HOME si está configurado
if ($env:JAVA_HOME) {
    if (-not (Test-Path $env:JAVA_HOME)) {
        Write-Warning "JAVA_HOME apunta a un directorio inválido: $env:JAVA_HOME"
        Write-Info "Considera eliminar JAVA_HOME o configurarlo correctamente"
        Write-Info "Intentando continuar con Java del PATH..."
    } else {
        Write-Info "JAVA_HOME: $env:JAVA_HOME"
    }
}

# Compilar si es necesario
Write-Info "Verificando compilación..."
Set-Location $ProjectDir

$JarFile = Join-Path $ProjectDir "build\libs\auth-microservice-1.0.0-fat.jar"
$BuildDir = Join-Path $ProjectDir "build\classes"

if (-not (Test-Path $BuildDir) -or -not (Test-Path $JarFile)) {
    Write-Warning "Proyecto no compilado, compilando..."
    
    # Intentar diferentes formas de ejecutar Gradle
    $gradleSuccess = $false
    
    # 1. Intentar con gradlew.bat
    if (Test-Path "gradlew.bat") {
        Write-Info "Usando Gradle Wrapper (gradlew.bat)..."
        try {
            # Temporalmente limpiar JAVA_HOME si está mal configurado
            $originalJavaHome = $env:JAVA_HOME
            if ($env:JAVA_HOME -and -not (Test-Path $env:JAVA_HOME)) {
                Write-Info "Temporalmente limpiando JAVA_HOME inválido para la compilación..."
                $env:JAVA_HOME = $null
            }
            
            .\gradlew.bat shadowJar --quiet --no-daemon
            $gradleSuccess = $true
            
            # Restaurar JAVA_HOME
            if ($originalJavaHome) {
                $env:JAVA_HOME = $originalJavaHome
            }
        } catch {
            Write-Warning "Error con gradlew.bat: $_"
            # Restaurar JAVA_HOME
            if ($originalJavaHome) {
                $env:JAVA_HOME = $originalJavaHome
            }
        }
    }
    
    # 2. Intentar con gradle global si gradlew falló
    if (-not $gradleSuccess -and (Get-Command "gradle" -ErrorAction SilentlyContinue)) {
        Write-Info "Intentando con Gradle global..."
        try {
            gradle shadowJar --quiet --no-daemon
            $gradleSuccess = $true
        } catch {
            Write-Warning "Error con gradle global: $_"
        }
    }
    
    if (-not $gradleSuccess) {
        Write-Error "No se pudo compilar el proyecto."
        Write-Info "Soluciones posibles:"
        Write-Info "1. Instala Java 21+ correctamente"
        Write-Info "2. Configura JAVA_HOME correctamente o elimínalo"
        Write-Info "3. Ejecuta manualmente: .\gradlew.bat shadowJar"
        exit 1
    }
    
    if (-not (Test-Path $JarFile)) {
        Write-Error "Error: JAR no fue generado después de la compilación"
        exit 1
    }
    Write-Success "Proyecto compilado exitosamente"
} else {
    Write-Info "Proyecto ya compilado"
}

# Ejecutar seed usando el comando CLI integrado
Write-Info "Ejecutando seed a través del JAR principal..."

try {
    java -jar $JarFile seed $SeedType
    
    Write-Success "Seed '$SeedType' ejecutado exitosamente"
    
    # Mostrar información adicional según el tipo
    switch ($SeedType) {
        "check" {
            Write-Info "Usa 'basic' para crear datos básicos si no existen"
        }
        "basic" {
            Write-Info "Datos básicos creados. Usa 'test' para agregar usuarios de prueba"
        }
        "fresh" {
            Write-Warning "Todos los datos fueron recreados desde cero"
        }
    }
} catch {
    Write-Error "Error ejecutando seed '$SeedType': $_"
    exit 1
}

Write-Header "SEED COMPLETADO"
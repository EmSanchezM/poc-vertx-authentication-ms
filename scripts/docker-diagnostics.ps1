# =============================================================================
# AUTH MICROSERVICE - DOCKER DIAGNOSTICS SCRIPT (PowerShell)
# =============================================================================
# Script de diagnóstico que verifica variables de entorno en el contenedor,
# conectividad de red entre contenedores y resolución DNS del servicio postgres

param(
    [string]$Environment = "development"
)

# Function to print colored output
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

function Write-Step {
    param([string]$Message)
    Write-Host "[STEP] $Message" -ForegroundColor Cyan
}

function Write-Debug {
    param([string]$Message)
    Write-Host "[DEBUG] $Message" -ForegroundColor Magenta
}

# Function to check if command exists
function Test-Command {
    param([string]$Command)
    return (Get-Command $Command -ErrorAction SilentlyContinue) -ne $null
}

# Function to get container name for service
function Get-ContainerName {
    param([string]$Service)
    try {
        $containerId = docker-compose ps -q $Service 2>$null | Select-Object -First 1
        return $containerId
    }
    catch {
        return $null
    }
}

# Function to check if container is running
function Test-ContainerRunning {
    param([string]$Container)
    try {
        $state = docker inspect -f '{{.State.Running}}' $Container 2>$null
        return $state -eq "true"
    }
    catch {
        return $false
    }
}

# Function to verify environment variables in container
function Test-ContainerEnvironment {
    param(
        [string]$Container,
        [string]$ServiceName
    )
    
    Write-Step "Verificando variables de entorno en contenedor $ServiceName..."
    
    if (-not (Test-ContainerRunning $Container)) {
        Write-Error "Contenedor $ServiceName no está ejecutándose"
        return $false
    }
    
    # Variables críticas para verificar
    $criticalVars = @(
        "DB_HOST",
        "DB_PORT", 
        "DB_NAME",
        "DB_USERNAME",
        "DB_PASSWORD",
        "REDIS_HOST",
        "REDIS_PORT",
        "JWT_SECRET",
        "APP_ENV"
    )
    
    Write-Info "Variables de entorno críticas en $ServiceName:"
    $missingVars = 0
    
    foreach ($var in $criticalVars) {
        try {
            $value = docker exec $Container printenv $var 2>$null
            if ($value) {
                # Ocultar valores sensibles
                if ($var -like "*PASSWORD*" -or $var -like "*SECRET*") {
                    Write-Success "  ✅ $var = ***"
                }
                else {
                    Write-Success "  ✅ $var = $value"
                }
            }
            else {
                Write-Error "  ❌ $var = NO DEFINIDA"
                $missingVars++
            }
        }
        catch {
            Write-Error "  ❌ $var = NO DEFINIDA"
            $missingVars++
        }
    }
    
    # Mostrar todas las variables de entorno disponibles
    Write-Debug "Todas las variables de entorno disponibles en $ServiceName:"
    try {
        $envVars = docker exec $Container printenv 2>$null | Sort-Object
        foreach ($envVar in $envVars) {
            $parts = $envVar -split "=", 2
            if ($parts.Length -eq 2) {
                $key = $parts[0]
                $value = $parts[1]
                if ($key -like "*PASSWORD*" -or $key -like "*SECRET*") {
                    Write-Debug "  $key=***"
                }
                else {
                    Write-Debug "  $key=$value"
                }
            }
        }
    }
    catch {
        Write-Warning "No se pudieron obtener todas las variables de entorno"
    }
    
    if ($missingVars -eq 0) {
        Write-Success "Todas las variables críticas están definidas en $ServiceName"
        return $true
    }
    else {
        Write-Error "$missingVars variables críticas faltantes en $ServiceName"
        return $false
    }
}

# Function to test network connectivity between containers
function Test-ContainerConnectivity {
    param(
        [string]$SourceContainer,
        [string]$TargetService,
        [string]$TargetPort,
        [string]$ServiceName
    )
    
    Write-Step "Probando conectividad de red desde $ServiceName hacia ${TargetService}:$TargetPort..."
    
    if (-not (Test-ContainerRunning $SourceContainer)) {
        Write-Error "Contenedor fuente $ServiceName no está ejecutándose"
        return $false
    }
    
    # Verificar conectividad usando diferentes métodos
    $connectivityOk = $false
    
    # Método usando /dev/tcp (disponible en bash)
    Write-Debug "Probando conectividad con /dev/tcp..."
    try {
        $result = docker exec $SourceContainer timeout 5 bash -c "exec 3<>/dev/tcp/$TargetService/$TargetPort && echo 'Connected' >&3" 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Success "  ✅ Conectividad exitosa usando /dev/tcp"
            $connectivityOk = $true
        }
        else {
            Write-Warning "  ⚠️ Conectividad falló usando /dev/tcp"
        }
    }
    catch {
        Write-Warning "  ⚠️ Conectividad falló usando /dev/tcp"
    }
    
    # Método alternativo usando netcat si está disponible
    if (-not $connectivityOk) {
        Write-Debug "Probando conectividad con netcat..."
        try {
            $result = docker exec $SourceContainer timeout 5 nc -z $TargetService $TargetPort 2>$null
            if ($LASTEXITCODE -eq 0) {
                Write-Success "  ✅ Conectividad exitosa usando netcat"
                $connectivityOk = $true
            }
            else {
                Write-Warning "  ⚠️ Conectividad falló usando netcat"
            }
        }
        catch {
            Write-Warning "  ⚠️ Conectividad falló usando netcat"
        }
    }
    
    if ($connectivityOk) {
        Write-Success "Conectividad de red exitosa desde $ServiceName hacia ${TargetService}:$TargetPort"
        return $true
    }
    else {
        Write-Error "No se pudo establecer conectividad desde $ServiceName hacia ${TargetService}:$TargetPort"
        return $false
    }
}

# Function to test DNS resolution
function Test-DnsResolution {
    param(
        [string]$Container,
        [string]$Hostname,
        [string]$ServiceName
    )
    
    Write-Step "Probando resolución DNS de '$Hostname' desde $ServiceName..."
    
    if (-not (Test-ContainerRunning $Container)) {
        Write-Error "Contenedor $ServiceName no está ejecutándose"
        return $false
    }
    
    $dnsOk = $false
    $resolvedIp = ""
    
    # Intentar resolución DNS usando nslookup
    Write-Debug "Probando resolución DNS con nslookup..."
    try {
        $nslookupResult = docker exec $Container nslookup $Hostname 2>$null
        if ($LASTEXITCODE -eq 0 -and $nslookupResult) {
            $resolvedIp = ($nslookupResult | Select-String "Address:" | Select-Object -Last 1) -replace "Address:\s*", ""
            if ($resolvedIp) {
                Write-Success "  ✅ DNS resuelto con nslookup: $Hostname -> $resolvedIp"
                $dnsOk = $true
            }
        }
    }
    catch {
        Write-Warning "  ⚠️ DNS no resuelto con nslookup"
    }
    
    # Método alternativo usando ping
    if (-not $dnsOk) {
        Write-Debug "Probando resolución DNS con ping..."
        try {
            $pingResult = docker exec $Container timeout 3 ping -c 1 $Hostname 2>$null
            if ($LASTEXITCODE -eq 0 -and $pingResult) {
                $resolvedIp = ($pingResult | Select-String "\([0-9.]+\)" | ForEach-Object { $_.Matches[0].Value }) -replace "[()]", ""
                if ($resolvedIp) {
                    Write-Success "  ✅ DNS resuelto con ping: $Hostname -> $resolvedIp"
                    $dnsOk = $true
                }
            }
        }
        catch {
            Write-Warning "  ⚠️ DNS no resuelto con ping"
        }
    }
    
    if ($dnsOk) {
        Write-Success "Resolución DNS exitosa: $Hostname -> $resolvedIp"
        
        # Verificar que la IP no sea localhost
        if ($resolvedIp -eq "127.0.0.1" -or $resolvedIp -eq "::1") {
            Write-Warning "⚠️ ADVERTENCIA: $Hostname se resuelve a localhost ($resolvedIp)"
            Write-Warning "   Esto podría indicar un problema de configuración de red"
        }
        
        return $true
    }
    else {
        Write-Error "No se pudo resolver DNS para $Hostname desde $ServiceName"
        return $false
    }
}

# Function to show network information
function Show-NetworkInfo {
    param(
        [string]$Container,
        [string]$ServiceName
    )
    
    Write-Step "Información de red para $ServiceName..."
    
    if (-not (Test-ContainerRunning $Container)) {
        Write-Error "Contenedor $ServiceName no está ejecutándose"
        return
    }
    
    Write-Info "Configuración de red en $ServiceName:"
    
    # Mostrar configuración DNS
    Write-Debug "Configuración DNS (/etc/resolv.conf):"
    try {
        $resolveConf = docker exec $Container cat /etc/resolv.conf 2>$null
        if ($resolveConf) {
            $resolveConf | ForEach-Object { Write-Debug "  $_" }
        }
    }
    catch {
        Write-Warning "No se pudo leer /etc/resolv.conf"
    }
    
    # Mostrar hosts conocidos
    Write-Debug "Hosts conocidos (/etc/hosts):"
    try {
        $hostsFile = docker exec $Container cat /etc/hosts 2>$null
        if ($hostsFile) {
            $hostsFile | ForEach-Object { Write-Debug "  $_" }
        }
    }
    catch {
        Write-Warning "No se pudo leer /etc/hosts"
    }
}

# Function to run comprehensive diagnostics
function Start-Diagnostics {
    param([string]$Environment)
    
    Write-Host "=============================================="
    Write-Host "Auth Microservice - Diagnóstico Docker"
    Write-Host "Ambiente: $Environment"
    Write-Host "=============================================="
    Write-Host ""
    
    # Verificar que Docker Compose esté disponible
    if (-not (Test-Command "docker-compose")) {
        Write-Error "Docker Compose no está disponible"
        exit 1
    }
    
    # Verificar que los archivos de compose existan
    $composeFiles = "-f docker-compose.yml -f docker-compose.$Environment.yml"
    try {
        $null = Invoke-Expression "docker-compose $composeFiles config --quiet" 2>$null
    }
    catch {
        Write-Error "Configuración de Docker Compose inválida para ambiente $Environment"
        exit 1
    }
    
    # Obtener contenedores
    Write-Step "Obteniendo información de contenedores..."
    $authContainer = Get-ContainerName "auth-service"
    $postgresContainer = Get-ContainerName "postgres"
    $redisContainer = Get-ContainerName "redis"
    
    Write-Info "Contenedores identificados:"
    Write-Info "  Auth Service: $(if ($authContainer) { $authContainer } else { 'NO ENCONTRADO' })"
    Write-Info "  PostgreSQL: $(if ($postgresContainer) { $postgresContainer } else { 'NO ENCONTRADO' })"
    Write-Info "  Redis: $(if ($redisContainer) { $redisContainer } else { 'NO ENCONTRADO' })"
    Write-Host ""
    
    # Verificar estado de contenedores
    Write-Step "Verificando estado de contenedores..."
    try {
        Invoke-Expression "docker-compose $composeFiles ps"
    }
    catch {
        Write-Warning "No se pudo obtener estado de contenedores"
    }
    Write-Host ""
    
    $diagnosticsPassed = 0
    $diagnosticsFailed = 0
    
    # Diagnóstico del servicio de autenticación
    if ($authContainer -and (Test-ContainerRunning $authContainer)) {
        Write-Step "=== DIAGNÓSTICO: AUTH SERVICE ==="
        
        # Verificar variables de entorno
        if (Test-ContainerEnvironment $authContainer "auth-service") {
            $diagnosticsPassed++
        }
        else {
            $diagnosticsFailed++
        }
        
        # Mostrar información de red
        Show-NetworkInfo $authContainer "auth-service"
        
        # Probar resolución DNS de postgres
        if (Test-DnsResolution $authContainer "postgres" "auth-service") {
            $diagnosticsPassed++
        }
        else {
            $diagnosticsFailed++
        }
        
        # Probar resolución DNS de redis
        if (Test-DnsResolution $authContainer "redis" "auth-service") {
            $diagnosticsPassed++
        }
        else {
            $diagnosticsFailed++
        }
        
        # Probar conectividad a postgres
        if (Test-ContainerConnectivity $authContainer "postgres" "5432" "auth-service") {
            $diagnosticsPassed++
        }
        else {
            $diagnosticsFailed++
        }
        
        # Probar conectividad a redis
        if (Test-ContainerConnectivity $authContainer "redis" "6379" "auth-service") {
            $diagnosticsPassed++
        }
        else {
            $diagnosticsFailed++
        }
        
        Write-Host ""
    }
    else {
        Write-Error "Contenedor auth-service no está ejecutándose, omitiendo diagnósticos"
        $diagnosticsFailed += 5
    }
    
    # Diagnóstico de PostgreSQL
    if ($postgresContainer -and (Test-ContainerRunning $postgresContainer)) {
        Write-Step "=== DIAGNÓSTICO: POSTGRESQL ==="
        
        # Verificar que PostgreSQL esté aceptando conexiones
        Write-Step "Verificando que PostgreSQL esté aceptando conexiones..."
        try {
            $pgUser = $env:POSTGRES_USER
            if (-not $pgUser) { $pgUser = "auth_user" }
            
            $result = docker exec $postgresContainer pg_isready -U $pgUser 2>$null
            if ($LASTEXITCODE -eq 0) {
                Write-Success "PostgreSQL está aceptando conexiones"
                $diagnosticsPassed++
            }
            else {
                Write-Error "PostgreSQL no está aceptando conexiones"
                $diagnosticsFailed++
            }
        }
        catch {
            Write-Error "PostgreSQL no está aceptando conexiones"
            $diagnosticsFailed++
        }
        
        Write-Host ""
    }
    else {
        Write-Error "Contenedor postgres no está ejecutándose"
        $diagnosticsFailed++
    }
    
    # Diagnóstico de Redis
    if ($redisContainer -and (Test-ContainerRunning $redisContainer)) {
        Write-Step "=== DIAGNÓSTICO: REDIS ==="
        
        # Verificar que Redis esté respondiendo
        Write-Step "Verificando que Redis esté respondiendo..."
        try {
            $result = docker exec $redisContainer redis-cli ping 2>$null
            if ($LASTEXITCODE -eq 0) {
                Write-Success "Redis está respondiendo"
                $diagnosticsPassed++
            }
            else {
                Write-Error "Redis no está respondiendo"
                $diagnosticsFailed++
            }
        }
        catch {
            Write-Error "Redis no está respondiendo"
            $diagnosticsFailed++
        }
        
        Write-Host ""
    }
    else {
        Write-Error "Contenedor redis no está ejecutándose"
        $diagnosticsFailed++
    }
    
    # Resumen final
    Write-Host "=============================================="
    Write-Host "RESUMEN DE DIAGNÓSTICOS"
    Write-Host "=============================================="
    Write-Info "Diagnósticos exitosos: $diagnosticsPassed"
    Write-Info "Diagnósticos fallidos: $diagnosticsFailed"
    
    if ($diagnosticsFailed -eq 0) {
        Write-Success "🎉 Todos los diagnósticos pasaron exitosamente!"
        Write-Host ""
        Write-Info "El entorno Docker está configurado correctamente."
        Write-Info "La aplicación debería poder conectarse a las bases de datos."
    }
    else {
        Write-Error "❌ $diagnosticsFailed diagnósticos fallaron"
        Write-Host ""
        Write-Warning "Revisa los errores anteriores para identificar problemas de configuración."
        Write-Info "Comandos útiles para debugging:"
        Write-Info "  docker-compose $composeFiles logs auth-service"
        Write-Info "  docker-compose $composeFiles logs postgres"
        Write-Info "  docker-compose $composeFiles logs redis"
        Write-Info "  docker exec <container> printenv"
        Write-Info "  docker network ls"
        Write-Info "  docker network inspect <network_name>"
    }
    
    Write-Host ""
    return $diagnosticsFailed
}

# Function to show usage
function Show-Usage {
    Write-Host "Uso: .\docker-diagnostics.ps1 [-Environment <ambiente>]"
    Write-Host ""
    Write-Host "Ambientes disponibles:"
    Write-Host "  development  - Ambiente de desarrollo (por defecto)"
    Write-Host "  qa           - Ambiente de QA"
    Write-Host "  production   - Ambiente de producción"
    Write-Host ""
    Write-Host "Ejemplos:"
    Write-Host "  .\docker-diagnostics.ps1                    # Diagnóstico en desarrollo"
    Write-Host "  .\docker-diagnostics.ps1 -Environment development"
    Write-Host "  .\docker-diagnostics.ps1 -Environment qa"
    Write-Host ""
}

# Main execution
switch ($Environment.ToLower()) {
    { $_ -in @("development", "qa", "production") } {
        $exitCode = Start-Diagnostics $Environment
        exit $exitCode
    }
    { $_ -in @("--help", "-h", "help") } {
        Show-Usage
        exit 0
    }
    default {
        Write-Error "Ambiente inválido: $Environment"
        Show-Usage
        exit 1
    }
}
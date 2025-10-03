# =============================================================================
# NETWORK CONNECTIVITY TEST SCRIPT (PowerShell)
# =============================================================================
# Script simple para probar conectividad de red entre contenedores
# Diseñado para ejecutarse dentro del contenedor de la aplicación

param(
    [string]$Action = "test"
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

# Function to test DNS resolution
function Test-DnsResolution {
    param([string]$Hostname)
    
    Write-Info "Probando resolución DNS para: $Hostname"
    
    try {
        $result = Resolve-DnsName $Hostname -ErrorAction Stop
        if ($result) {
            $ip = $result[0].IPAddress
            Write-Success "DNS OK: $Hostname -> $ip"
            return $true
        }
    }
    catch {
        try {
            # Método alternativo usando ping
            $pingResult = Test-Connection $Hostname -Count 1 -Quiet -ErrorAction Stop
            if ($pingResult) {
                Write-Success "DNS OK (via ping): $Hostname"
                return $true
            }
        }
        catch {
            Write-Error "DNS FAIL: No se pudo resolver $Hostname"
            return $false
        }
    }
    
    Write-Error "DNS FAIL: No se pudo resolver $Hostname"
    return $false
}

# Function to test port connectivity
function Test-PortConnectivity {
    param(
        [string]$Host,
        [int]$Port
    )
    
    Write-Info "Probando conectividad a: ${Host}:$Port"
    
    try {
        $tcpClient = New-Object System.Net.Sockets.TcpClient
        $tcpClient.ReceiveTimeout = 5000
        $tcpClient.SendTimeout = 5000
        
        $result = $tcpClient.BeginConnect($Host, $Port, $null, $null)
        $success = $result.AsyncWaitHandle.WaitOne(5000, $false)
        
        if ($success -and $tcpClient.Connected) {
            Write-Success "CONECTIVIDAD OK: ${Host}:$Port"
            $tcpClient.Close()
            return $true
        }
        else {
            Write-Error "CONECTIVIDAD FAIL: ${Host}:$Port"
            $tcpClient.Close()
            return $false
        }
    }
    catch {
        Write-Error "CONECTIVIDAD FAIL: ${Host}:$Port - $($_.Exception.Message)"
        return $false
    }
}

# Function to show network info
function Show-NetworkInfo {
    Write-Info "=== INFORMACIÓN DE RED ==="
    
    Write-Info "Hostname: $env:COMPUTERNAME"
    
    Write-Info "Interfaces de red:"
    try {
        $interfaces = Get-NetIPAddress | Where-Object { $_.AddressFamily -eq "IPv4" -and $_.IPAddress -ne "127.0.0.1" }
        foreach ($interface in $interfaces) {
            Write-Host "  $($interface.InterfaceAlias): $($interface.IPAddress)" -ForegroundColor White
        }
    }
    catch {
        Write-Warning "No se pudo obtener información de interfaces de red"
    }
    
    Write-Info "Tabla de rutas:"
    try {
        $routes = Get-NetRoute | Where-Object { $_.DestinationPrefix -eq "0.0.0.0/0" }
        foreach ($route in $routes) {
            Write-Host "  Default via $($route.NextHop) dev $($route.InterfaceAlias)" -ForegroundColor White
        }
    }
    catch {
        Write-Warning "No se pudo obtener información de rutas"
    }
    
    Write-Info "Servidores DNS:"
    try {
        $dnsServers = Get-DnsClientServerAddress | Where-Object { $_.AddressFamily -eq 2 }
        foreach ($dns in $dnsServers) {
            if ($dns.ServerAddresses) {
                Write-Host "  $($dns.InterfaceAlias): $($dns.ServerAddresses -join ', ')" -ForegroundColor White
            }
        }
    }
    catch {
        Write-Warning "No se pudo obtener información de DNS"
    }
}

# Function to show environment variables
function Show-EnvironmentVariables {
    Write-Info "=== VARIABLES DE ENTORNO CRÍTICAS ==="
    
    $criticalVars = @(
        "DB_HOST",
        "DB_PORT",
        "DB_NAME",
        "DB_USERNAME",
        "REDIS_HOST",
        "REDIS_PORT",
        "APP_ENV",
        "COMPUTERNAME"
    )
    
    foreach ($var in $criticalVars) {
        $value = [Environment]::GetEnvironmentVariable($var)
        if ($value) {
            if ($var -like "*PASSWORD*" -or $var -like "*SECRET*") {
                Write-Success "  $var = ***"
            }
            else {
                Write-Success "  $var = $value"
            }
        }
        else {
            Write-Error "  $var = NO DEFINIDA"
        }
    }
}

# Main test function
function Start-Tests {
    Write-Host "=============================================="
    Write-Host "Test de Conectividad de Red"
    Write-Host "=============================================="
    Write-Host ""
    
    # Mostrar información del sistema
    Show-NetworkInfo
    Write-Host ""
    
    # Mostrar variables de entorno
    Show-EnvironmentVariables
    Write-Host ""
    
    # Tests de DNS
    Write-Info "=== TESTS DE DNS ==="
    $dnsTests = 0
    $dnsPassed = 0
    
    $testHosts = @("postgres", "redis", "localhost")
    foreach ($host in $testHosts) {
        $dnsTests++
        if (Test-DnsResolution $host) {
            $dnsPassed++
        }
    }
    Write-Host ""
    
    # Tests de conectividad
    Write-Info "=== TESTS DE CONECTIVIDAD ==="
    $connTests = 0
    $connPassed = 0
    
    # Test PostgreSQL
    $dbHost = $env:DB_HOST
    if (-not $dbHost) { $dbHost = "postgres" }
    $dbPort = $env:DB_PORT
    if (-not $dbPort) { $dbPort = 5432 }
    
    $connTests++
    if (Test-PortConnectivity $dbHost $dbPort) {
        $connPassed++
    }
    
    # Test Redis
    $redisHost = $env:REDIS_HOST
    if (-not $redisHost) { $redisHost = "redis" }
    $redisPort = $env:REDIS_PORT
    if (-not $redisPort) { $redisPort = 6379 }
    
    $connTests++
    if (Test-PortConnectivity $redisHost $redisPort) {
        $connPassed++
    }
    
    Write-Host ""
    
    # Resumen
    Write-Info "=== RESUMEN ==="
    Write-Info "Tests DNS: $dnsPassed/$dnsTests pasaron"
    Write-Info "Tests Conectividad: $connPassed/$connTests pasaron"
    
    $totalTests = $dnsTests + $connTests
    $totalPassed = $dnsPassed + $connPassed
    
    if ($totalPassed -eq $totalTests) {
        Write-Success "🎉 Todos los tests pasaron ($totalPassed/$totalTests)"
        return 0
    }
    else {
        $failed = $totalTests - $totalPassed
        Write-Error "❌ $failed tests fallaron ($totalPassed/$totalTests)"
        return 1
    }
}

# Function to show usage
function Show-Usage {
    Write-Host "Uso: .\test-network-connectivity.ps1 [-Action <acción>]"
    Write-Host ""
    Write-Host "Acciones:"
    Write-Host "  test     - Ejecutar todos los tests (por defecto)"
    Write-Host "  dns      - Solo tests de DNS"
    Write-Host "  network  - Solo información de red"
    Write-Host "  env      - Solo variables de entorno"
    Write-Host "  help     - Mostrar esta ayuda"
    Write-Host ""
    Write-Host "Ejemplos:"
    Write-Host "  .\test-network-connectivity.ps1                    # Ejecutar todos los tests"
    Write-Host "  .\test-network-connectivity.ps1 -Action test       # Ejecutar todos los tests"
    Write-Host "  .\test-network-connectivity.ps1 -Action dns        # Solo tests de DNS"
    Write-Host "  .\test-network-connectivity.ps1 -Action network    # Solo información de red"
    Write-Host ""
}

# Main execution
switch ($Action.ToLower()) {
    "test" {
        $exitCode = Start-Tests
        exit $exitCode
    }
    "dns" {
        Write-Info "=== TESTS DE DNS ==="
        $testHosts = @("postgres", "redis", "localhost")
        foreach ($host in $testHosts) {
            Test-DnsResolution $host
        }
    }
    "network" {
        Show-NetworkInfo
    }
    "env" {
        Show-EnvironmentVariables
    }
    { $_ -in @("help", "--help", "-h") } {
        Show-Usage
    }
    default {
        Write-Error "Acción inválida: $Action"
        Show-Usage
        exit 1
    }
}
# =============================================================================
# AUTH MICROSERVICE - ENVIRONMENT SETUP SCRIPT (PowerShell)
# =============================================================================
# This script helps set up the environment for different deployment scenarios

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("development", "qa", "production", "docker")]
    [string]$Environment
)

# Function to write colored output
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

# Function to show usage
function Show-Usage {
    Write-Host "Usage: .\set-environment.ps1 -Environment <ENVIRONMENT>"
    Write-Host ""
    Write-Host "Available environments:"
    Write-Host "  development  - Set up development environment"
    Write-Host "  qa           - Set up QA/staging environment"
    Write-Host "  production   - Set up production environment"
    Write-Host "  docker       - Set up for Docker deployment"
    Write-Host ""
    Write-Host "Examples:"
    Write-Host "  .\set-environment.ps1 -Environment development"
    Write-Host "  .\set-environment.ps1 -Environment production"
    Write-Host ""
}

# Function to copy environment file
function Set-EnvironmentFile {
    param([string]$Env)
    
    $sourceFile = ".env.$Env"
    $targetFile = ".env"
    
    if (-not (Test-Path $sourceFile)) {
        Write-Error "Environment file $sourceFile not found!"
        exit 1
    }
    
    Write-Info "Setting up environment: $Env"
    
    # Backup existing .env if it exists
    if (Test-Path $targetFile) {
        $backupFile = "$targetFile.backup.$(Get-Date -Format 'yyyyMMdd_HHmmss')"
        Copy-Item $targetFile $backupFile
        Write-Info "Backed up existing .env file to $backupFile"
    }
    
    # Copy environment-specific file
    Copy-Item $sourceFile $targetFile
    Write-Success "Environment file copied: $sourceFile -> $targetFile"
}

# Function to set Spring profile
function Set-SpringProfile {
    param([string]$Env)
    
    $profileFile = "application.profile"
    "spring.profiles.active=$Env" | Out-File -FilePath $profileFile -Encoding UTF8
    Write-Success "Spring profile set to: $Env"
}

# Function to validate required environment variables
function Test-RequiredVariables {
    param([string]$Env)
    
    $missingVars = @()
    
    # Load environment variables from .env file
    if (Test-Path ".env") {
        Get-Content ".env" | Where-Object { $_ -match "^[^#].*=" } | ForEach-Object {
            $parts = $_ -split "=", 2
            if ($parts.Length -eq 2) {
                [Environment]::SetEnvironmentVariable($parts[0], $parts[1], "Process")
            }
        }
    }
    
    # Check required variables based on environment
    $requiredVars = switch ($Env) {
        "production" { @("DB_PASSWORD", "REDIS_PASSWORD", "JWT_SECRET", "EMAIL_SMTP_PASSWORD", "GEOLOCATION_API_KEY") }
        "qa" { @("DB_PASSWORD", "REDIS_PASSWORD", "JWT_SECRET") }
        "development" { @("DB_PASSWORD") }
        default { @() }
    }
    
    foreach ($var in $requiredVars) {
        $value = [Environment]::GetEnvironmentVariable($var, "Process")
        if ([string]::IsNullOrEmpty($value)) {
            $missingVars += $var
        }
    }
    
    if ($missingVars.Count -gt 0) {
        Write-Warning "The following environment variables are not set:"
        foreach ($var in $missingVars) {
            Write-Host "  - $var"
        }
        Write-Warning "Please set these variables in your .env file or environment"
    } else {
        Write-Success "All required environment variables are set"
    }
}

# Function to create necessary directories
function New-RequiredDirectories {
    $dirs = @("logs", "data", "tmp")
    
    foreach ($dir in $dirs) {
        if (-not (Test-Path $dir)) {
            New-Item -ItemType Directory -Path $dir -Force | Out-Null
            Write-Info "Created directory: $dir"
        }
    }
}

# Function to show environment summary
function Show-Summary {
    param([string]$Env)
    
    Write-Host ""
    Write-Host "==============================================="
    Write-Host "Environment Setup Summary"
    Write-Host "==============================================="
    Write-Host "Environment: $Env"
    Write-Host "Configuration file: .env"
    Write-Host "Spring profile: $Env"
    Write-Host ""
    
    if (Test-Path ".env") {
        Write-Host "Key configuration values:"
        $content = Get-Content ".env"
        $appEnv = ($content | Where-Object { $_ -match "^APP_ENV=" }) -replace "APP_ENV=", ""
        $serverPort = ($content | Where-Object { $_ -match "^SERVER_PORT=" }) -replace "SERVER_PORT=", ""
        $dbHost = ($content | Where-Object { $_ -match "^DB_HOST=" }) -replace "DB_HOST=", ""
        $logLevel = ($content | Where-Object { $_ -match "^LOG_LEVEL=" }) -replace "LOG_LEVEL=", ""
        
        Write-Host "  APP_ENV: $appEnv"
        Write-Host "  SERVER_PORT: $serverPort"
        Write-Host "  DB_HOST: $dbHost"
        Write-Host "  LOG_LEVEL: $logLevel"
    }
    
    Write-Host ""
    Write-Host "Next steps:"
    Write-Host "1. Review and update .env file with your specific values"
    Write-Host "2. Ensure all required services are running (PostgreSQL, Redis)"
    Write-Host "3. Run the application with: .\gradlew.bat run"
    Write-Host ""
}

# Main execution
try {
    Write-Info "Setting up Auth Microservice for environment: $Environment"
    
    Set-EnvironmentFile -Env $Environment
    Set-SpringProfile -Env $Environment
    New-RequiredDirectories
    Test-RequiredVariables -Env $Environment
    Show-Summary -Env $Environment
    
    Write-Success "Environment setup completed successfully!"
}
catch {
    Write-Error "An error occurred: $($_.Exception.Message)"
    exit 1
}
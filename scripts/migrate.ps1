param(
    [string]$Image = 'pulseops-migration:local',
    [switch]$Build,
    [string]$Network
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$backendPath = Join-Path $projectRoot 'backend'
$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue |
    Select-Object -First 1 -ExpandProperty Source

if (-not $dockerCommand -and $env:OS -eq 'Windows_NT') {
    $dockerDesktopCommand = Join-Path $env:ProgramFiles 'Docker\Docker\resources\bin\docker.exe'
    if (Test-Path -LiteralPath $dockerDesktopCommand) {
        $dockerCommand = $dockerDesktopCommand
    }
}

if (-not $dockerCommand) {
    throw 'Docker CLI was not found. Start Docker Desktop and open a new PowerShell session.'
}

if ($env:OS -eq 'Windows_NT') {
    $dockerDirectory = Split-Path -Parent $dockerCommand
    if (($env:PATH -split ';') -notcontains $dockerDirectory) {
        $env:PATH = "$dockerDirectory;$env:PATH"
    }
}

$requiredEnvironment = @(
    'DATABASE_URL',
    'DATABASE_USERNAME',
    'DATABASE_PASSWORD'
)
foreach ($variableName in $requiredEnvironment) {
    $value = [Environment]::GetEnvironmentVariable($variableName)
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "$variableName must be set in the current process."
    }
}

if ($Build) {
    & $dockerCommand build --file (Join-Path $backendPath 'Dockerfile.migrate') `
        --tag $Image $backendPath
    if ($LASTEXITCODE -ne 0) {
        throw 'The migration image failed to build.'
    }
}

$previousFlywayUrl = $env:FLYWAY_URL
$previousFlywayUser = $env:FLYWAY_USER
$previousFlywayPassword = $env:FLYWAY_PASSWORD
try {
    $env:FLYWAY_URL = $env:DATABASE_URL
    $env:FLYWAY_USER = $env:DATABASE_USERNAME
    $env:FLYWAY_PASSWORD = $env:DATABASE_PASSWORD

    $arguments = @(
        'run',
        '--rm',
        '--env', 'FLYWAY_URL',
        '--env', 'FLYWAY_USER',
        '--env', 'FLYWAY_PASSWORD'
    )
    if (-not [string]::IsNullOrWhiteSpace($Network)) {
        $arguments += @('--network', $Network)
    }
    $arguments += @($Image, 'migrate')

    & $dockerCommand @arguments
    if ($LASTEXITCODE -ne 0) {
        throw 'Flyway migration failed. The application deployment must not continue.'
    }
}
finally {
    $env:FLYWAY_URL = $previousFlywayUrl
    $env:FLYWAY_USER = $previousFlywayUser
    $env:FLYWAY_PASSWORD = $previousFlywayPassword
}

Write-Host 'PulseOps database migration completed.' -ForegroundColor Green

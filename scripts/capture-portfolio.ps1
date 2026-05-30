param(
    [switch]$InstallBrowsers
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$frontendPath = Join-Path $projectRoot 'frontend'
$composeProject = 'pulseops-portfolio'
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

$dockerArguments = @(
    'compose',
    '--profile', 'demo',
    '-p', $composeProject
)

$secretBytes = New-Object byte[] 32
$randomNumberGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $randomNumberGenerator.GetBytes($secretBytes)
}
finally {
    $randomNumberGenerator.Dispose()
}

$env:JWT_SECRET = [Convert]::ToBase64String($secretBytes)
$env:POSTGRES_PASSWORD = [Guid]::NewGuid().ToString('N')
$env:POSTGRES_PORT = '56432'
$env:BACKEND_PORT = '8280'
$env:FRONTEND_PORT = '5280'
$env:DEMO_PORT = '8291'
$env:MONITORING_ALLOW_PRIVATE_TARGETS = 'true'
$env:MONITORING_SCHEDULER_ENABLED = 'false'
$env:AUTH_RATE_LIMIT_REGISTRATION_ATTEMPTS = '100'
$env:CORS_ALLOWED_ORIGINS = 'http://127.0.0.1:5280'
$env:E2E_BASE_URL = 'http://127.0.0.1:5280'
$env:E2E_DEMO_URL = 'http://127.0.0.1:8291'
$env:PORTFOLIO_SCREENSHOT_DIR = Join-Path $projectRoot 'docs\assets\screenshots'

Push-Location -LiteralPath $projectRoot
try {
    & $dockerCommand @dockerArguments up --build --detach --wait
    if ($LASTEXITCODE -ne 0) {
        throw 'The isolated portfolio stack failed to start.'
    }

    Push-Location -LiteralPath $frontendPath
    try {
        if ($InstallBrowsers) {
            npx.cmd playwright install chromium
            if ($LASTEXITCODE -ne 0) {
                throw 'Playwright browser installation failed.'
            }
        }

        node '.\e2e\capture-portfolio.mjs'
        if ($LASTEXITCODE -ne 0) {
            throw 'Portfolio screenshot capture failed.'
        }
    }
    finally {
        Pop-Location
    }
}
catch {
    & $dockerCommand @dockerArguments logs --no-color
    throw
}
finally {
    & $dockerCommand @dockerArguments down --volumes --remove-orphans
    Pop-Location
}

Write-Host 'PulseOps portfolio screenshots captured.' -ForegroundColor Green

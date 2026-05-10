$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot

& (Join-Path $PSScriptRoot 'check-secrets.ps1')
if ($LASTEXITCODE -ne 0) {
    throw "Tracked-file secret check failed with exit code $LASTEXITCODE."
}

Push-Location -LiteralPath (Join-Path $projectRoot 'backend')
try {
    .\mvnw.cmd --batch-mode --no-transfer-progress verify
    if ($LASTEXITCODE -ne 0) {
        throw "Backend verification failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

Push-Location -LiteralPath (Join-Path $projectRoot 'frontend')
try {
    npm.cmd ci
    if ($LASTEXITCODE -ne 0) {
        throw "Frontend dependency installation failed with exit code $LASTEXITCODE."
    }

    npm.cmd run lint
    if ($LASTEXITCODE -ne 0) {
        throw "Frontend linting failed with exit code $LASTEXITCODE."
    }

    npm.cmd test
    if ($LASTEXITCODE -ne 0) {
        throw "Frontend tests failed with exit code $LASTEXITCODE."
    }

    npm.cmd run build
    if ($LASTEXITCODE -ne 0) {
        throw "Frontend build failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

Push-Location -LiteralPath $projectRoot
try {
    if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
        $verificationKey = New-Object byte[] 32
        $randomNumberGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try {
            $randomNumberGenerator.GetBytes($verificationKey)
        }
        finally {
            $randomNumberGenerator.Dispose()
        }
        $env:JWT_SECRET = [Convert]::ToBase64String($verificationKey)
    }
    if ([string]::IsNullOrWhiteSpace($env:POSTGRES_PASSWORD)) {
        $env:POSTGRES_PASSWORD = [Guid]::NewGuid().ToString('N')
    }

    docker compose config --quiet
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose validation failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

Write-Host 'PulseOps verification passed.' -ForegroundColor Green

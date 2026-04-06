$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot

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
    docker compose config --quiet
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose validation failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

Write-Host 'PulseOps foundation verification passed.' -ForegroundColor Green

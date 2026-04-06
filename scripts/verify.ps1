$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot

Push-Location -LiteralPath (Join-Path $projectRoot 'backend')
try {
    .\mvnw.cmd test
}
finally {
    Pop-Location
}

Push-Location -LiteralPath (Join-Path $projectRoot 'frontend')
try {
    npm.cmd ci
    npm.cmd run lint
    npm.cmd test
    npm.cmd run build
}
finally {
    Pop-Location
}

Push-Location -LiteralPath $projectRoot
try {
    docker compose config --quiet
}
finally {
    Pop-Location
}

Write-Host 'PulseOps foundation verification passed.' -ForegroundColor Green

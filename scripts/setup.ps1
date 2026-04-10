$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$examplePath = Join-Path $projectRoot '.env.example'
$environmentPath = Join-Path $projectRoot '.env'

if (Test-Path -LiteralPath $environmentPath) {
    Write-Host '.env already exists; no values were changed.' -ForegroundColor Yellow
    exit 0
}

$secretBytes = New-Object byte[] 32
$randomNumberGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $randomNumberGenerator.GetBytes($secretBytes)
}
finally {
    $randomNumberGenerator.Dispose()
}
$jwtSecret = [Convert]::ToBase64String($secretBytes)
$environmentContent = Get-Content -LiteralPath $examplePath -Raw
$environmentContent = $environmentContent -replace '(?m)^JWT_SECRET=.*$', "JWT_SECRET=$jwtSecret"

[System.IO.File]::WriteAllText(
    $environmentPath,
    $environmentContent,
    [System.Text.UTF8Encoding]::new($false))

Write-Host 'Created .env with a new 256-bit JWT signing secret.' -ForegroundColor Green

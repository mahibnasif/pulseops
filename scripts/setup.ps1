$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$examplePath = Join-Path $projectRoot '.env.example'
$environmentPath = Join-Path $projectRoot '.env'

if (Test-Path -LiteralPath $environmentPath) {
    Write-Host '.env already exists; no values were changed.' -ForegroundColor Yellow
    exit 0
}

$secretBytes = New-Object byte[] 32
$databaseSecretBytes = New-Object byte[] 24
$randomNumberGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $randomNumberGenerator.GetBytes($secretBytes)
    $randomNumberGenerator.GetBytes($databaseSecretBytes)
}
finally {
    $randomNumberGenerator.Dispose()
}
$jwtSecret = [Convert]::ToBase64String($secretBytes)
$databaseSecret = [Convert]::ToBase64String($databaseSecretBytes)
$databaseSecret = $databaseSecret.TrimEnd('=').Replace('+', '-').Replace('/', '_')
$environmentContent = Get-Content -LiteralPath $examplePath -Raw
$environmentContent = $environmentContent -replace '(?m)^JWT_SECRET=.*$', "JWT_SECRET=$jwtSecret"
$environmentContent = $environmentContent -replace `
    '(?m)^POSTGRES_PASSWORD=.*$', `
    "POSTGRES_PASSWORD=$databaseSecret"

[System.IO.File]::WriteAllText(
    $environmentPath,
    $environmentContent,
    [System.Text.UTF8Encoding]::new($false))

Write-Host `
    'Created .env with independent random JWT and database secrets.' `
    -ForegroundColor Green

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$trackedFiles = @(& git -C $projectRoot ls-files)
if ($LASTEXITCODE -ne 0) {
    throw "Unable to enumerate tracked files."
}

$forbiddenFiles = @('.env', '.env.local', '.env.production')
$findings = [System.Collections.Generic.List[string]]::new()
foreach ($forbiddenFile in $forbiddenFiles) {
    if ($trackedFiles -contains $forbiddenFile) {
        $findings.Add("$forbiddenFile must not be tracked.")
    }
}

$patterns = [ordered]@{
    'AWS access key' = 'AKIA[0-9A-Z]{16}'
    'GitHub token' = 'gh[pousr]_[A-Za-z0-9]{30,}'
    'private key' = '(?m)^-----BEGIN (?:EC |OPENSSH |PGP |RSA )?PRIVATE KEY-----'
    'populated JWT secret' = '(?m)^[ \t]*JWT_SECRET[ \t]*=[ \t]*(?!\$\{|$)[^\s#]{16,}'
    'populated database secret' = '(?m)^[ \t]*POSTGRES_PASSWORD[ \t]*=[ \t]*(?!\$\{|$)[^\s#]{12,}'
}

foreach ($relativePath in $trackedFiles) {
    $absolutePath = Join-Path $projectRoot $relativePath
    if (-not (Test-Path -LiteralPath $absolutePath -PathType Leaf)) {
        continue
    }
    try {
        $content = [System.IO.File]::ReadAllText($absolutePath)
    }
    catch {
        continue
    }
    foreach ($pattern in $patterns.GetEnumerator()) {
        if ($content -match $pattern.Value) {
            $findings.Add("$relativePath matches the $($pattern.Key) signature.")
        }
    }
}

if ($findings.Count -gt 0) {
    foreach ($finding in $findings) {
        Write-Error $finding
    }
    exit 1
}

Write-Host 'Tracked-file secret check passed.' -ForegroundColor Green

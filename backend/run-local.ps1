param(
    [string]$EnvironmentFile = (Join-Path $PSScriptRoot '..\.env.local'),
    [switch]$ValidateOnly
)

$resolvedEnvironmentFile = [IO.Path]::GetFullPath($EnvironmentFile)
if (-not (Test-Path -LiteralPath $resolvedEnvironmentFile)) {
    throw "Local environment file was not found: $resolvedEnvironmentFile"
}

foreach ($rawLine in Get-Content -LiteralPath $resolvedEnvironmentFile) {
    $line = $rawLine.Trim()
    if (-not $line -or $line.StartsWith('#')) {
        continue
    }

    $separatorIndex = $line.IndexOf('=')
    if ($separatorIndex -lt 1) {
        throw "Invalid environment entry: $rawLine"
    }

    $name = $line.Substring(0, $separatorIndex).Trim()
    $value = $line.Substring($separatorIndex + 1)
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}

$requiredVariables = @('JWT_SECRET', 'GOOGLE_CLIENT_ID', 'FACEBOOK_APP_ID')
$missingVariables = $requiredVariables | Where-Object {
    [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_, 'Process'))
}
if ($missingVariables) {
    throw "Missing required values in ${resolvedEnvironmentFile}: $($missingVariables -join ', ')"
}

if ([string]::IsNullOrWhiteSpace($env:FACEBOOK_APP_SECRET)) {
    Write-Warning 'FACEBOOK_APP_SECRET is empty. Google login can be tested, but Facebook login will return 503.'
}

if ($ValidateOnly) {
    Write-Host "Environment loaded from $resolvedEnvironmentFile"
    return
}

Push-Location $PSScriptRoot
try {
    # Local startup should not be blocked by unrelated test compilation.
    & .\mvnw.cmd '-Dmaven.test.skip=true' spring-boot:run
    if ($LASTEXITCODE -ne 0) {
        throw "Backend exited with code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

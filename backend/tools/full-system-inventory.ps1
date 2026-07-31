param(
    [string]$OutputPath = "docs/audit/system/inventory-source-baseline.json"
)

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$scopeRoots = @(
    (Join-Path $repoRoot "backend\src\main\java"),
    (Join-Path $repoRoot "backend\src\main\resources\db\migration"),
    (Join-Path $repoRoot "backend\src\test"),
    (Join-Path $repoRoot "frontend\src\app"),
    (Join-Path $repoRoot "frontend\e2e")
)

function Get-Category([string]$relativePath) {
    if ($relativePath -match "controllers|Controller") { return "api-controller" }
    if ($relativePath -match "services|Service") { return "business-service" }
    if ($relativePath -match "repositories|Repository") { return "repository" }
    if ($relativePath -match "entities|Entity") { return "entity" }
    if ($relativePath -match "migration") { return "database-migration" }
    if ($relativePath -match "e2e") { return "browser-test" }
    if ($relativePath -match "test") { return "automated-test" }
    if ($relativePath -match "routes") { return "route" }
    if ($relativePath -match "features|layout|shared") { return "frontend-ui" }
    return "source"
}

$items = @()
foreach ($root in $scopeRoots) {
    if (-not (Test-Path $root)) { continue }
    Get-ChildItem $root -Recurse -File | ForEach-Object {
        $relative = $_.FullName.Substring($repoRoot.Length + 1).Replace("\", "/")
        $text = Get-Content -Raw $_.FullName -ErrorAction SilentlyContinue
        if ($null -eq $text) { $text = "" }
        $routes = @([regex]::Matches($text, '(?i)(?:@(?:Get|Post|Put|Delete|Patch)Mapping|path\s*:\s*|routerLink\s*=\s*)[^\r\n]*') | ForEach-Object { $_.Value.Trim() } | Select-Object -Unique)
        $permissions = @([regex]::Matches($text, '(?i)(?:Permission|FunctionCode|functionCode)[^\r\n]*') | ForEach-Object { $_.Value.Trim() } | Select-Object -Unique)
        $items += [pscustomobject]@{
            path = $relative
            category = Get-Category $relative
            extension = $_.Extension
            sizeBytes = $_.Length
            routeEvidence = $routes
            permissionEvidence = $permissions
        }
    }
}

$parent = Split-Path $OutputPath -Parent
if (-not [IO.Path]::IsPathRooted($OutputPath)) { $OutputPath = Join-Path $repoRoot $OutputPath }
$parent = Split-Path $OutputPath -Parent
New-Item -ItemType Directory -Force $parent | Out-Null
$items | Sort-Object path | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $OutputPath
Write-Output "Wrote $($items.Count) inventory items to $OutputPath"

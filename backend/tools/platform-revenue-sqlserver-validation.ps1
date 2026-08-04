param([string]$Image = 'mcr.microsoft.com/mssql/server:2022-latest', [int]$Port = 0)

$ErrorActionPreference = 'Stop'
$previousNativeErrorPreference = $PSNativeCommandUseErrorActionPreference
$PSNativeCommandUseErrorActionPreference = $false
$backendRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runId = '{0}-{1}' -f $PID, (Get-Date -Format 'yyyyMMddHHmmss')
$containerName = 'luxestay-platform-revenue-{0}' -f $runId
$databaseName = 'PlatformRevenue_{0}' -f ($runId -replace '-', '_')
$sqlPort = if ($Port -gt 0) { $Port } else { Get-Random -Minimum 17435 -Maximum 18435 }
$saPassword = 'Lx!{0}aA9' -f ([guid]::NewGuid().ToString('N').Substring(0, 20))
$server = '127.0.0.1,{0}' -f $sqlPort

try {
    docker run --detach --name $containerName --env ACCEPT_EULA=Y --env MSSQL_PID=Developer --env MSSQL_SA_PASSWORD=$saPassword --publish "${sqlPort}:1433" $Image | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not start the isolated SQL Server container.' }
    $ready = $false
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        try { $null = & sqlcmd -S $server -U sa -P $saPassword -C -b -d master -Q 'SELECT 1' 2>&1 } catch { }
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw 'SQL Server did not become ready within 120 seconds.' }
    & sqlcmd -S $server -U sa -P $saPassword -C -b -d master -Q "CREATE DATABASE [$databaseName];"
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the isolated validation database.' }
    $env:PLATFORM_REVENUE_DATABASE_URL = 'jdbc:sqlserver://127.0.0.1:{0};databaseName={1};encrypt=true;trustServerCertificate=true' -f $sqlPort, $databaseName
    $env:PLATFORM_REVENUE_DATABASE_USERNAME = 'sa'
    $env:PLATFORM_REVENUE_DATABASE_PASSWORD = $saPassword
    $env:PLATFORM_REVENUE_DATABASE_DRIVER = 'com.microsoft.sqlserver.jdbc.SQLServerDriver'
    $env:PLATFORM_REVENUE_DATABASE_DIALECT = 'org.hibernate.dialect.SQLServerDialect'
    Push-Location $backendRoot
    try {
        & .\mvnw.cmd -q surefire:test '-Dtest=PlatformRevenueReconciliationIntegrationTest'
        if ($LASTEXITCODE -ne 0) { throw 'Platform revenue SQL Server reconciliation failed.' }
    } finally { Pop-Location }
    Write-Output 'Platform revenue rows, totals and export checksum reconciled on SQL Server.'
} finally {
    $PSNativeCommandUseErrorActionPreference = $previousNativeErrorPreference
    foreach ($name in @('PLATFORM_REVENUE_DATABASE_URL','PLATFORM_REVENUE_DATABASE_USERNAME','PLATFORM_REVENUE_DATABASE_PASSWORD','PLATFORM_REVENUE_DATABASE_DRIVER','PLATFORM_REVENUE_DATABASE_DIALECT')) { Remove-Item "Env:$name" -ErrorAction SilentlyContinue }
    if ($containerName -match '^luxestay-platform-revenue-[0-9]+-[0-9]{14}$') { docker rm --force $containerName *> $null }
}

param([string]$Image = 'mcr.microsoft.com/mssql/server:2022-latest', [int]$Port = 0)

$ErrorActionPreference = 'Stop'
$previousNativeErrorPreference = $PSNativeCommandUseErrorActionPreference
$PSNativeCommandUseErrorActionPreference = $false
$backendRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runId = '{0}-{1}' -f $PID, (Get-Date -Format 'yyyyMMddHHmmss')
$containerName = 'luxestay-property-revenue-{0}' -f $runId
$databaseName = 'PropertyRevenue_{0}' -f ($runId -replace '-', '_')
$sqlPort = if ($Port -gt 0) { $Port } else { Get-Random -Minimum 16434 -Maximum 17434 }
$saPassword = 'Lx!{0}aA9' -f ([guid]::NewGuid().ToString('N').Substring(0, 20))
$server = '127.0.0.1,{0}' -f $sqlPort

try {
    docker run --detach --name $containerName --env ACCEPT_EULA=Y --env MSSQL_PID=Developer --env MSSQL_SA_PASSWORD=$saPassword --publish "${sqlPort}:1433" $Image | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not start the isolated SQL Server container.' }
    $ready = $false
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        try {
            $null = & sqlcmd -S $server -U sa -P $saPassword -C -b -d master -Q 'SELECT 1' 2>&1
        } catch {
            # SQL Server emits handshake errors while the container is starting.
        }
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw 'SQL Server did not become ready within 120 seconds.' }
    & sqlcmd -S $server -U sa -P $saPassword -C -b -d master -Q "CREATE DATABASE [$databaseName];"
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the isolated validation database.' }
    $env:PROPERTY_REVENUE_DATABASE_URL = 'jdbc:sqlserver://127.0.0.1:{0};databaseName={1};encrypt=true;trustServerCertificate=true' -f $sqlPort, $databaseName
    $env:PROPERTY_REVENUE_DATABASE_USERNAME = 'sa'
    $env:PROPERTY_REVENUE_DATABASE_PASSWORD = $saPassword
    $env:PROPERTY_REVENUE_DATABASE_DRIVER = 'com.microsoft.sqlserver.jdbc.SQLServerDriver'
    $env:PROPERTY_REVENUE_DATABASE_DIALECT = 'org.hibernate.dialect.SQLServerDialect'
    Push-Location $backendRoot
    try {
        & .\mvnw.cmd -q surefire:test '-Dtest=PropertyRevenueReconciliationIntegrationTest'
        if ($LASTEXITCODE -ne 0) { throw 'Property revenue SQL Server reconciliation failed.' }
    } finally { Pop-Location }
    Write-Output 'Property revenue SQL Server reconciliation matched ledger totals to one VND.'
} finally {
    $PSNativeCommandUseErrorActionPreference = $previousNativeErrorPreference
    foreach ($name in @('PROPERTY_REVENUE_DATABASE_URL','PROPERTY_REVENUE_DATABASE_USERNAME','PROPERTY_REVENUE_DATABASE_PASSWORD','PROPERTY_REVENUE_DATABASE_DRIVER','PROPERTY_REVENUE_DATABASE_DIALECT')) { Remove-Item "Env:$name" -ErrorAction SilentlyContinue }
    if ($containerName -match '^luxestay-property-revenue-[0-9]+-[0-9]{14}$') { docker rm --force $containerName *> $null }
}

param(
    [string]$Image = 'mcr.microsoft.com/mssql/server:2022-latest',
    [int]$Port = 0
)

$ErrorActionPreference = 'Stop'
$previousNativeErrorPreference = $PSNativeCommandUseErrorActionPreference
$PSNativeCommandUseErrorActionPreference = $false
$backendRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runId = '{0}-{1}' -f $PID, (Get-Date -Format 'yyyyMMddHHmmss')
$containerName = 'luxestay-stay-lifecycle-{0}' -f $runId
$databaseName = 'StayLifecycle_{0}' -f ($runId -replace '-', '_')
$sqlPort = if ($Port -gt 0) { $Port } else { Get-Random -Minimum 15433 -Maximum 16433 }
$saPassword = 'Lx!{0}aA9' -f ([guid]::NewGuid().ToString('N').Substring(0, 20))
$server = '127.0.0.1,{0}' -f $sqlPort
$previousEnvironment = @{}

function Invoke-SqlQuery {
    param(
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$Query
    )

    & sqlcmd -S $server -U sa -P $saPassword -C -b -d $Database -Q $Query
    if ($LASTEXITCODE -ne 0) {
        throw "sqlcmd failed for database $Database"
    }
}

try {
    docker run --detach --name $containerName `
        --env ACCEPT_EULA=Y `
        --env MSSQL_PID=Developer `
        --env MSSQL_SA_PASSWORD=$saPassword `
        --publish "${sqlPort}:1433" `
        $Image | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not start the isolated SQL Server container.'
    }

    $ready = $false
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        try {
            $null = & sqlcmd -S $server -U sa -P $saPassword -C -b -d master -Q 'SELECT 1' 2>&1
        } catch {
            # SQL Server emits handshake errors while the container is booting.
        }
        if ($LASTEXITCODE -eq 0) {
            $ready = $true
            break
        }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) {
        throw 'SQL Server did not become ready within 120 seconds.'
    }

    Invoke-SqlQuery -Database 'master' -Query "CREATE DATABASE [$databaseName];"

    foreach ($name in @(
        'STAY_LIFECYCLE_SQLSERVER_ENABLED',
        'STAY_LIFECYCLE_SQLSERVER_URL',
        'STAY_LIFECYCLE_SQLSERVER_USERNAME',
        'STAY_LIFECYCLE_SQLSERVER_PASSWORD')) {
        $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    }

    $env:STAY_LIFECYCLE_SQLSERVER_ENABLED = 'true'
    $env:STAY_LIFECYCLE_SQLSERVER_URL = 'jdbc:sqlserver://127.0.0.1:{0};databaseName={1};encrypt=true;trustServerCertificate=true' -f $sqlPort, $databaseName
    $env:STAY_LIFECYCLE_SQLSERVER_USERNAME = 'sa'
    $env:STAY_LIFECYCLE_SQLSERVER_PASSWORD = $saPassword

    Push-Location $backendRoot
    try {
        & .\mvnw.cmd -q '-Dtest=StayLifecycleSqlServerIT,CheckoutAggregateSqlServerIT' test
        if ($LASTEXITCODE -ne 0) {
            throw 'Stay lifecycle SQL Server integration test failed.'
        }
    } finally {
        Pop-Location
    }

    Write-Output 'Stay lifecycle SQL Server assignment/check-in/checkout, aggregate rollback and replay validation passed.'
} finally {
    $PSNativeCommandUseErrorActionPreference = $previousNativeErrorPreference
    foreach ($name in $previousEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
    }
    if ($containerName -match '^luxestay-stay-lifecycle-[0-9]+-[0-9]{14}$') {
        docker rm --force $containerName *> $null
    }
}

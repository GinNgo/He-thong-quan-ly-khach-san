param(
    [string]$Image = 'mcr.microsoft.com/mssql/server:2022-latest',
    [int]$Port = 0
)

$ErrorActionPreference = 'Stop'
$previousNativeErrorPreference = $PSNativeCommandUseErrorActionPreference
$PSNativeCommandUseErrorActionPreference = $false
$backendRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runId = '{0}-{1}' -f $PID, (Get-Date -Format 'yyyyMMddHHmmss')
$containerName = 'luxestay-booking-hold-{0}' -f $runId
$databaseName = 'BookingHold_{0}' -f ($runId -replace '-', '_')
$sqlPort = if ($Port -gt 0) { $Port } else { Get-Random -Minimum 16434 -Maximum 17434 }
$saPassword = 'Lx!{0}aA9' -f ([guid]::NewGuid().ToString('N').Substring(0, 20))
$previousEnvironment = @{}
$sqlcmdPath = $null

function Invoke-ContainerSql {
    param(
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$Query
    )

    & docker exec $containerName $sqlcmdPath `
        -S localhost -U sa -P $saPassword -C -b -d $Database -Q $Query
    if ($LASTEXITCODE -ne 0) {
        throw "sqlcmd failed for database $Database"
    }
}

try {
    & docker run --detach --name $containerName `
        --env ACCEPT_EULA=Y `
        --env MSSQL_PID=Developer `
        --env MSSQL_SA_PASSWORD=$saPassword `
        --publish "${sqlPort}:1433" `
        $Image | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not start the isolated SQL Server container.'
    }

    foreach ($candidate in @('/opt/mssql-tools18/bin/sqlcmd', '/opt/mssql-tools/bin/sqlcmd')) {
        & docker exec $containerName test -x $candidate *> $null
        if ($LASTEXITCODE -eq 0) {
            $sqlcmdPath = $candidate
            break
        }
    }
    if ($null -eq $sqlcmdPath) {
        throw 'The SQL Server image does not contain sqlcmd.'
    }

    $ready = $false
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        try {
            $null = & docker exec $containerName $sqlcmdPath `
                -S localhost -U sa -P $saPassword -C -b -d master -Q 'SELECT 1' 2>&1
        } catch {
            # SQL Server emits connection errors while the container is booting.
        }
        if ($LASTEXITCODE -eq 0) {
            $ready = $true
            break
        }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) {
        throw 'SQL Server did not become ready within the validation window.'
    }

    Invoke-ContainerSql -Database 'master' -Query "CREATE DATABASE [$databaseName];"

    foreach ($name in @(
        'BOOKING_HOLD_SQLSERVER_ENABLED',
        'BOOKING_HOLD_SQLSERVER_URL',
        'BOOKING_HOLD_SQLSERVER_USERNAME',
        'BOOKING_HOLD_SQLSERVER_PASSWORD')) {
        $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    }

    $env:BOOKING_HOLD_SQLSERVER_ENABLED = 'true'
    $env:BOOKING_HOLD_SQLSERVER_URL = 'jdbc:sqlserver://127.0.0.1:{0};databaseName={1};encrypt=true;trustServerCertificate=true' -f $sqlPort, $databaseName
    $env:BOOKING_HOLD_SQLSERVER_USERNAME = 'sa'
    $env:BOOKING_HOLD_SQLSERVER_PASSWORD = $saPassword

    Push-Location $backendRoot
    try {
        & .\mvnw.cmd -q '-Dtest=ReservationHoldSqlServerIT' test
        if ($LASTEXITCODE -ne 0) {
            throw 'Booking hold SQL Server integration test failed.'
        }
    } finally {
        Pop-Location
    }

    Write-Output 'SQL Server last-room booking and payment/expiry locking validation passed.'
} finally {
    $PSNativeCommandUseErrorActionPreference = $previousNativeErrorPreference
    foreach ($name in $previousEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
    }
    if ($containerName -match '^luxestay-booking-hold-[0-9]+-[0-9]{14}$') {
        & docker rm --force $containerName *> $null
    }
}

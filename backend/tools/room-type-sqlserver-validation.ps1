param([string]$Image = 'mcr.microsoft.com/mssql/server:2022-latest', [int]$Port = 0)

$ErrorActionPreference = 'Stop'
$previousNativeErrorPreference = $PSNativeCommandUseErrorActionPreference
$PSNativeCommandUseErrorActionPreference = $false
$migrationPath = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')).Path 'src/main/resources/db/migration/V83__room_type_lifecycle_constraints.sql'
$runId = '{0}-{1}' -f $PID, (Get-Date -Format 'yyyyMMddHHmmss')
$containerName = 'luxestay-room-type-{0}' -f $runId
$databaseName = 'RoomType_{0}' -f ($runId -replace '-', '_')
$sqlPort = if ($Port -gt 0) { $Port } else { Get-Random -Minimum 19437 -Maximum 20437 }
$saPassword = 'Lx!{0}aA9' -f ([guid]::NewGuid().ToString('N').Substring(0, 20))
$server = '127.0.0.1,{0}' -f $sqlPort
$containerStarted = $false; $databaseCreated = $false

function Invoke-SqlQuery([string]$Database, [string]$Query) {
    & sqlcmd -S $server -U sa -P $saPassword -C -b -d $Database -Q $Query
    if ($LASTEXITCODE -ne 0) { throw "sqlcmd failed for database $Database" }
}
function Invoke-SqlFile([string]$Database, [string]$Path) {
    & sqlcmd -S $server -U sa -P $saPassword -C -b -d $Database -i $Path
    if ($LASTEXITCODE -ne 0) { throw "sqlcmd failed for $Path" }
}

try {
    docker run --detach --name $containerName --env ACCEPT_EULA=Y --env MSSQL_PID=Developer `
        --env MSSQL_SA_PASSWORD=$saPassword --publish "${sqlPort}:1433" $Image | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not start isolated SQL Server.' }
    $containerStarted = $true; $ready = $false
    for ($attempt=1; $attempt -le 60; $attempt++) {
        try { $null = & sqlcmd -S $server -U sa -P $saPassword -C -b -d master -Q 'SELECT 1' 2>&1 } catch {}
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }; Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw 'SQL Server did not become ready.' }
    Invoke-SqlQuery master "CREATE DATABASE [$databaseName];"; $databaseCreated = $true
    Invoke-SqlQuery $databaseName @'
CREATE TABLE dbo.room_types(
 id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY, hotel_id BIGINT NOT NULL, code VARCHAR(50) NOT NULL,
 status VARCHAR(20) NOT NULL, max_adults INT NULL, max_children INT NULL, max_guests INT NULL,
 base_price DECIMAL(19,0) NOT NULL, hourly_price DECIMAL(19,0) NULL
);
INSERT dbo.room_types(hotel_id,code,status,max_adults,max_children,max_guests,base_price)
VALUES(1,'DLX','ACTIVE',2,1,3,500000);
'@
    Invoke-SqlFile $databaseName $migrationPath; Invoke-SqlFile $databaseName $migrationPath
    if (-not (& sqlcmd -S $server -U sa -P $saPassword -C -h -1 -W -d $databaseName -Q "SET NOCOUNT ON; SELECT COUNT(*) FROM sys.indexes WHERE name='UK_room_types_hotel_code';" | Select-String '^1$')) { throw 'Unique index missing.' }
    $duplicateRejected=$false; try { Invoke-SqlQuery $databaseName "INSERT dbo.room_types(hotel_id,code,status,max_guests,base_price) VALUES(1,'DLX','ACTIVE',2,1);" } catch { $duplicateRejected=$true }
    if (-not $duplicateRejected) { throw 'Duplicate code was accepted.' }
    $invalidRejected=$false; try { Invoke-SqlQuery $databaseName "INSERT dbo.room_types(hotel_id,code,status,max_guests,base_price) VALUES(1,'BAD','DELETED',2,1);" } catch { $invalidRejected=$true }
    if (-not $invalidRejected) { throw 'Invalid lifecycle status was accepted.' }
    Write-Output 'Room-type SQL Server validation passed: idempotence, uniqueness and lifecycle constraints.'
} finally {
    if ($databaseCreated) { try { Invoke-SqlQuery master "ALTER DATABASE [$databaseName] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$databaseName];" } catch {} }
    if ($containerStarted) { docker rm --force $containerName | Out-Null }
    $PSNativeCommandUseErrorActionPreference = $previousNativeErrorPreference
}

param([string]$Image = 'mcr.microsoft.com/mssql/server:2022-latest', [int]$Port = 0)
$ErrorActionPreference = 'Stop'
$previousNativeErrorPreference = $PSNativeCommandUseErrorActionPreference
$PSNativeCommandUseErrorActionPreference = $false
$migrationPath = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')).Path 'src/main/resources/db/migration/V85__physical_room_crud_integrity.sql'
$runId = '{0}-{1}' -f $PID, (Get-Date -Format 'yyyyMMddHHmmss')
$containerName = 'luxestay-physical-room-{0}' -f $runId
$databaseName = 'PhysicalRoom_{0}' -f ($runId -replace '-', '_')
$sqlPort = if ($Port -gt 0) { $Port } else { Get-Random -Minimum 20438 -Maximum 21438 }
$saPassword = 'Lx!{0}aA9' -f ([guid]::NewGuid().ToString('N').Substring(0, 20))
$server = '127.0.0.1,{0}' -f $sqlPort
$containerStarted = $false; $databaseCreated = $false
function Invoke-SqlQuery([string]$Database, [string]$Query) { & sqlcmd -S $server -U sa -P $saPassword -C -b -d $Database -Q $Query; if ($LASTEXITCODE -ne 0) { throw "sqlcmd failed for database $Database" } }
function Invoke-SqlFile([string]$Database, [string]$Path) { & sqlcmd -S $server -U sa -P $saPassword -C -b -d $Database -i $Path; if ($LASTEXITCODE -ne 0) { throw "sqlcmd failed for $Path" } }
try {
    docker run --detach --name $containerName --env ACCEPT_EULA=Y --env MSSQL_PID=Developer --env MSSQL_SA_PASSWORD=$saPassword --publish "${sqlPort}:1433" $Image | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not start isolated SQL Server.' }; $containerStarted = $true; $ready = $false
    for ($attempt=1; $attempt -le 60; $attempt++) { try { $null = & sqlcmd -S $server -U sa -P $saPassword -C -b -d master -Q 'SELECT 1' 2>&1 } catch {}; if ($LASTEXITCODE -eq 0) { $ready=$true; break }; Start-Sleep -Seconds 2 }
    if (-not $ready) { throw 'SQL Server did not become ready.' }
    Invoke-SqlQuery master "CREATE DATABASE [$databaseName];"; $databaseCreated=$true
    Invoke-SqlQuery $databaseName "CREATE TABLE dbo.rooms(id BIGINT IDENTITY PRIMARY KEY,hotel_id BIGINT NOT NULL,room_number NVARCHAR(50) NOT NULL,floor INT NOT NULL); INSERT dbo.rooms(hotel_id,room_number,floor) VALUES(1,N' a101 ',1);"
    Invoke-SqlFile $databaseName $migrationPath; Invoke-SqlFile $databaseName $migrationPath
    $duplicateRejected=$false; try { Invoke-SqlQuery $databaseName "INSERT dbo.rooms(hotel_id,room_number,floor) VALUES(1,N'A101',1);" } catch { $duplicateRejected=$true }
    if (-not $duplicateRejected) { throw 'Property-local duplicate room number was accepted.' }
    $floorRejected=$false; try { Invoke-SqlQuery $databaseName "INSERT dbo.rooms(hotel_id,room_number,floor) VALUES(1,N'A102',501);" } catch { $floorRejected=$true }
    if (-not $floorRejected) { throw 'Invalid floor was accepted.' }
    Write-Output 'Physical-room SQL Server validation passed: normalization, idempotence, uniqueness and floor constraints.'
} finally {
    if ($databaseCreated) { try { Invoke-SqlQuery master "ALTER DATABASE [$databaseName] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$databaseName];" } catch {} }
    if ($containerStarted) { docker rm --force $containerName | Out-Null }
    $PSNativeCommandUseErrorActionPreference = $previousNativeErrorPreference
}

param([string]$Image = 'mcr.microsoft.com/mssql/server:2022-latest', [int]$Port = 0, [string]$LocalServer = '')
$ErrorActionPreference = 'Stop'
$previousNativeErrorPreference = $PSNativeCommandUseErrorActionPreference
$PSNativeCommandUseErrorActionPreference = $false
$migrationPath = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')).Path 'src/main/resources/db/migration/V97__maintenance_work_orders.sql'
$runId = '{0}-{1}' -f $PID, (Get-Date -Format 'yyyyMMddHHmmss')
$containerName = 'luxestay-maintenance-{0}' -f $runId
$databaseName = 'Maintenance_{0}' -f ($runId -replace '-', '_')
$sqlPort = if ($Port -gt 0) { $Port } else { Get-Random -Minimum 22440 -Maximum 23440 }
$saPassword = 'Lx!{0}aA9' -f ([guid]::NewGuid().ToString('N').Substring(0, 20))
$server = if ($LocalServer) { $LocalServer } else { '127.0.0.1,{0}' -f $sqlPort }
$containerStarted = $false; $databaseCreated = $false
function Invoke-SqlQuery([string]$Database, [string]$Query) { $safeQuery = "SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON; $Query"; if ($LocalServer) { & sqlcmd -S $server -E -C -b -d $Database -Q $safeQuery } else { & sqlcmd -S $server -U sa -P $saPassword -C -b -d $Database -Q $safeQuery }; if ($LASTEXITCODE -ne 0) { throw "sqlcmd failed for $Database" } }
function Invoke-SqlFile([string]$Database, [string]$Path) { if ($LocalServer) { & sqlcmd -S $server -E -C -b -d $Database -i $Path } else { & sqlcmd -S $server -U sa -P $saPassword -C -b -d $Database -i $Path }; if ($LASTEXITCODE -ne 0) { throw "sqlcmd failed for $Path" } }
try {
    if (-not $LocalServer) {
        docker run --detach --name $containerName --env ACCEPT_EULA=Y --env MSSQL_PID=Developer --env MSSQL_SA_PASSWORD=$saPassword --publish "${sqlPort}:1433" $Image | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Could not start isolated SQL Server.' }; $containerStarted = $true; $ready = $false
        for ($attempt=1; $attempt -le 60; $attempt++) { try { $null = & sqlcmd -S $server -U sa -P $saPassword -C -b -d master -Q 'SELECT 1' 2>&1 } catch {}; if ($LASTEXITCODE -eq 0) { $ready=$true; break }; Start-Sleep -Seconds 2 }
        if (-not $ready) { throw 'SQL Server did not become ready.' }
    }
    Invoke-SqlQuery master "CREATE DATABASE [$databaseName];"; $databaseCreated=$true
    Invoke-SqlQuery $databaseName @'
CREATE TABLE dbo.hotels(id BIGINT NOT NULL PRIMARY KEY);
CREATE TABLE dbo.users(id BIGINT NOT NULL PRIMARY KEY);
CREATE TABLE dbo.rooms(id BIGINT NOT NULL PRIMARY KEY, hotel_id BIGINT NOT NULL);
INSERT dbo.hotels(id) VALUES(1); INSERT dbo.users(id) VALUES(2); INSERT dbo.rooms(id,hotel_id) VALUES(3,1);
'@
    Invoke-SqlFile $databaseName $migrationPath; Invoke-SqlFile $databaseName $migrationPath
    Invoke-SqlQuery $databaseName "INSERT dbo.maintenance_work_orders(hotel_id,room_id,reason,priority,status) VALUES(1,3,N'Leak','HIGH','OPEN');"
    $duplicateRejected=$false; try { Invoke-SqlQuery $databaseName "INSERT dbo.maintenance_work_orders(hotel_id,room_id,reason,priority,status) VALUES(1,3,N'Other','LOW','IN_PROGRESS');" } catch { $duplicateRejected=$true }
    if (-not $duplicateRejected) { throw 'A second active work order was accepted for the same room.' }
    $scheduleRejected=$false; try { Invoke-SqlQuery $databaseName "INSERT dbo.maintenance_work_orders(hotel_id,room_id,reason,priority,status,scheduled_start,scheduled_end) VALUES(1,3,N'Bad','LOW','COMPLETED','2026-08-05','2026-08-04');" } catch { $scheduleRejected=$true }
    if (-not $scheduleRejected) { throw 'An invalid schedule was accepted.' }
    Write-Output 'Maintenance work-order SQL Server validation passed: idempotence, lifecycle domains, schedule and one-active-order constraint.'
} finally {
    if ($databaseCreated) { try { Invoke-SqlQuery master "ALTER DATABASE [$databaseName] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$databaseName];" } catch {} }
    if ($containerStarted) { docker rm --force $containerName | Out-Null }
    $PSNativeCommandUseErrorActionPreference = $previousNativeErrorPreference
}

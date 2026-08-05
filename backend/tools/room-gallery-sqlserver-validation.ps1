param([string]$Image = 'mcr.microsoft.com/mssql/server:2022-latest', [int]$Port = 0, [string]$LocalServer = '')
$ErrorActionPreference = 'Stop'
$previousNativeErrorPreference = $PSNativeCommandUseErrorActionPreference
$PSNativeCommandUseErrorActionPreference = $false
$migrationPath = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')).Path 'src/main/resources/db/migration/V86__room_gallery_integrity.sql'
$runId = '{0}-{1}' -f $PID, (Get-Date -Format 'yyyyMMddHHmmss')
$containerName = 'luxestay-room-gallery-{0}' -f $runId
$databaseName = 'RoomGallery_{0}' -f ($runId -replace '-', '_')
$sqlPort = if ($Port -gt 0) { $Port } else { Get-Random -Minimum 21439 -Maximum 22439 }
$saPassword = 'Lx!{0}aA9' -f ([guid]::NewGuid().ToString('N').Substring(0, 20))
$server = if ($LocalServer) { $LocalServer } else { '127.0.0.1,{0}' -f $sqlPort }
$containerStarted = $false; $databaseCreated = $false
function Invoke-SqlQuery([string]$Database, [string]$Query) { if ($LocalServer) { & sqlcmd -S $server -E -C -b -d $Database -Q "SET QUOTED_IDENTIFIER ON; $Query" } else { & sqlcmd -S $server -U sa -P $saPassword -C -b -d $Database -Q "SET QUOTED_IDENTIFIER ON; $Query" }; if ($LASTEXITCODE -ne 0) { throw "sqlcmd failed for $Database" } }
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
CREATE TABLE dbo.room_images(id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY, room_id BIGINT NOT NULL, sort_order INT NOT NULL, is_primary BIT NOT NULL);
INSERT dbo.room_images(room_id,sort_order,is_primary) VALUES(10,5,1),(10,5,1),(10,9,0);
'@
  Invoke-SqlFile $databaseName $migrationPath; Invoke-SqlFile $databaseName $migrationPath
  $normalized = if ($LocalServer) { & sqlcmd -S $server -E -C -h -1 -W -d $databaseName -Q "SET NOCOUNT ON; SELECT CONCAT(COUNT(*),':',COUNT(DISTINCT sort_order),':',SUM(CASE WHEN is_primary=1 THEN 1 ELSE 0 END)) FROM dbo.room_images WHERE room_id=10;" } else { & sqlcmd -S $server -U sa -P $saPassword -C -h -1 -W -d $databaseName -Q "SET NOCOUNT ON; SELECT CONCAT(COUNT(*),':',COUNT(DISTINCT sort_order),':',SUM(CASE WHEN is_primary=1 THEN 1 ELSE 0 END)) FROM dbo.room_images WHERE room_id=10;" }
  if (-not ($normalized | Select-String '^3:3:1$')) { throw 'Gallery normalization failed.' }
  $orderRejected=$false; try { Invoke-SqlQuery $databaseName "INSERT dbo.room_images(room_id,sort_order,is_primary) VALUES(10,0,0);" } catch { $orderRejected=$true }
  if (-not $orderRejected) { throw 'Duplicate order was accepted.' }
  $primaryRejected=$false; try { Invoke-SqlQuery $databaseName "INSERT dbo.room_images(room_id,sort_order,is_primary) VALUES(10,20,1);" } catch { $primaryRejected=$true }
  if (-not $primaryRejected) { throw 'Second primary was accepted.' }
  Write-Output 'Room gallery SQL Server validation passed: normalization, idempotence, unique order and single primary.'
} finally {
  if ($databaseCreated) { try { Invoke-SqlQuery master "ALTER DATABASE [$databaseName] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$databaseName];" } catch {} }
  if ($containerStarted) { docker rm --force $containerName | Out-Null }
  $PSNativeCommandUseErrorActionPreference = $previousNativeErrorPreference
}

param([string]$Image = 'mcr.microsoft.com/mssql/server:2022-latest', [int]$Port = 0, [string]$LocalServer = '')
$ErrorActionPreference = 'Stop'
$previousNativeErrorPreference = $PSNativeCommandUseErrorActionPreference
$PSNativeCommandUseErrorActionPreference = $false
$migrationPath = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')).Path 'src/main/resources/db/migration/V88__service_catalog_lifecycle.sql'
$runId = '{0}-{1}' -f $PID, (Get-Date -Format 'yyyyMMddHHmmss')
$containerName = 'luxestay-service-catalog-{0}' -f $runId
$databaseName = 'ServiceCatalog_{0}' -f ($runId -replace '-', '_')
$sqlPort = if ($Port -gt 0) { $Port } else { Get-Random -Minimum 23441 -Maximum 24441 }
$saPassword = 'Lx!{0}aA9' -f ([guid]::NewGuid().ToString('N').Substring(0, 20))
$server = if ($LocalServer) { $LocalServer } else { '127.0.0.1,{0}' -f $sqlPort }
$containerStarted = $false; $databaseCreated = $false
function Invoke-SqlQuery([string]$Database, [string]$Query) { $safe = "SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON; $Query"; if ($LocalServer) { & sqlcmd -S $server -E -C -b -d $Database -Q $safe } else { & sqlcmd -S $server -U sa -P $saPassword -C -b -d $Database -Q $safe }; if ($LASTEXITCODE -ne 0) { throw "sqlcmd failed for $Database" } }
function Invoke-SqlFile([string]$Database, [string]$Path) { if ($LocalServer) { & sqlcmd -S $server -E -C -b -d $Database -i $Path } else { & sqlcmd -S $server -U sa -P $saPassword -C -b -d $Database -i $Path }; if ($LASTEXITCODE -ne 0) { throw "sqlcmd failed for $Path" } }
try {
    if (-not $LocalServer) {
        docker run --detach --name $containerName --env ACCEPT_EULA=Y --env MSSQL_PID=Developer --env MSSQL_SA_PASSWORD=$saPassword --publish "${sqlPort}:1433" $Image | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Could not start isolated SQL Server.' }; $containerStarted=$true; $ready=$false
        for($attempt=1;$attempt -le 60;$attempt++){ try{$null=& sqlcmd -S $server -U sa -P $saPassword -C -b -d master -Q 'SELECT 1' 2>&1}catch{}; if($LASTEXITCODE -eq 0){$ready=$true;break}; Start-Sleep -Seconds 2 }
        if(-not $ready){throw 'SQL Server did not become ready.'}
    }
    Invoke-SqlQuery master "CREATE DATABASE [$databaseName];"; $databaseCreated=$true
    Invoke-SqlQuery $databaseName "CREATE TABLE dbo.services(id BIGINT IDENTITY PRIMARY KEY,hotel_id BIGINT NULL,code VARCHAR(80) NOT NULL,name_vi NVARCHAR(255) NOT NULL,name_en NVARCHAR(255) NOT NULL,price DECIMAL(19,0) NOT NULL,status VARCHAR(20) NOT NULL,is_system BIT NOT NULL,created_at DATETIME2 NULL,updated_at DATETIME2 NULL,created_by VARCHAR(255) NULL,updated_by VARCHAR(255) NULL); INSERT dbo.services(hotel_id,code,name_vi,name_en,price,status,is_system) VALUES(1,'BREAKFAST',N'Bua sang',N'Breakfast',150000,'ACTIVE',0);"
    Invoke-SqlFile $databaseName $migrationPath; Invoke-SqlFile $databaseName $migrationPath
    $badPrice=$false; try { Invoke-SqlQuery $databaseName "INSERT dbo.services(hotel_id,code,name_vi,name_en,price,status,is_system) VALUES(1,'BAD',N'Bad',N'Bad',0,'ACTIVE',0);" } catch { $badPrice=$true }
    if(-not $badPrice){throw 'Non-positive VND service price was accepted.'}
    $badStatus=$false; try { Invoke-SqlQuery $databaseName "UPDATE dbo.services SET status='ARCHIVED' WHERE id=1;" } catch { $badStatus=$true }
    if(-not $badStatus){throw 'Unsupported service status was accepted.'}
    Write-Output 'Service catalog SQL Server validation passed: idempotence, version, history and price/status constraints.'
} finally {
    if($databaseCreated){try{Invoke-SqlQuery master "ALTER DATABASE [$databaseName] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$databaseName];"}catch{}}
    if($containerStarted){docker rm --force $containerName | Out-Null}
    $PSNativeCommandUseErrorActionPreference=$previousNativeErrorPreference
}

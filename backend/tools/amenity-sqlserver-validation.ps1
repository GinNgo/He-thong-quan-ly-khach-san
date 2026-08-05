param(
    [string]$Image = 'mcr.microsoft.com/mssql/server:2022-latest',
    [int]$Port = 0,
    [string]$ExistingServer,
    [string]$ExistingPassword
)

$ErrorActionPreference = 'Stop'
$previousNativeErrorPreference = $PSNativeCommandUseErrorActionPreference
$PSNativeCommandUseErrorActionPreference = $false
$backendRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$migrationPath = Join-Path $backendRoot 'src/main/resources/db/migration/V81__amenity_catalog_assignments.sql'
$runId = '{0}-{1}' -f $PID, (Get-Date -Format 'yyyyMMddHHmmss')
$containerName = 'luxestay-amenities-{0}' -f $runId
$databaseName = 'Amenities_{0}' -f ($runId -replace '-', '_')
$sqlPort = if ($Port -gt 0) { $Port } else { Get-Random -Minimum 17435 -Maximum 18435 }
$useExistingServer = -not [string]::IsNullOrWhiteSpace($ExistingServer)
if ($useExistingServer -and [string]::IsNullOrWhiteSpace($ExistingPassword)) {
    throw 'ExistingPassword is required when ExistingServer is provided.'
}
$saPassword = if ($useExistingServer) {
    $ExistingPassword
} else {
    'Lx!{0}aA9' -f ([guid]::NewGuid().ToString('N').Substring(0, 20))
}
$server = if ($useExistingServer) { $ExistingServer } else { '127.0.0.1,{0}' -f $sqlPort }
$containerStarted = $false
$databaseCreated = $false

function Invoke-SqlQuery {
    param([string]$Database, [string]$Query)
    & sqlcmd -S $server -U sa -P $saPassword -C -b -d $Database -Q $Query
    if ($LASTEXITCODE -ne 0) { throw "sqlcmd failed for database $Database" }
}

function Invoke-SqlFile {
    param([string]$Database, [string]$Path)
    & sqlcmd -S $server -U sa -P $saPassword -C -b -d $Database -i $Path
    if ($LASTEXITCODE -ne 0) { throw "sqlcmd failed for $Path" }
}

try {
    if (-not $useExistingServer) {
        docker run --detach --name $containerName `
            --env ACCEPT_EULA=Y `
            --env MSSQL_PID=Developer `
            --env MSSQL_SA_PASSWORD=$saPassword `
            --publish "${sqlPort}:1433" `
            $Image | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Could not start the isolated SQL Server container.' }
        $containerStarted = $true

        $ready = $false
        for ($attempt = 1; $attempt -le 60; $attempt++) {
            try {
                $null = & sqlcmd -S $server -U sa -P $saPassword -C -b -d master -Q 'SELECT 1' 2>&1
            } catch {
                # SQL Server emits connection errors while the container is starting.
            }
            if ($LASTEXITCODE -eq 0) { $ready = $true; break }
            Start-Sleep -Seconds 2
        }
        if (-not $ready) { throw 'SQL Server did not become ready within 120 seconds.' }
    } else {
        Invoke-SqlQuery -Database master -Query 'SELECT 1;'
    }

    Invoke-SqlQuery -Database master -Query "CREATE DATABASE [$databaseName];"
    $databaseCreated = $true
    Invoke-SqlQuery -Database $databaseName -Query @'
CREATE TABLE dbo.hotels(id BIGINT NOT NULL PRIMARY KEY);
CREATE TABLE dbo.room_types(
    id BIGINT NOT NULL PRIMARY KEY,
    hotel_id BIGINT NOT NULL REFERENCES dbo.hotels(id)
);
INSERT dbo.hotels(id) VALUES (1), (2);
INSERT dbo.room_types(id, hotel_id) VALUES (10, 1);
'@
    Invoke-SqlFile -Database $databaseName -Path $migrationPath
    Invoke-SqlFile -Database $databaseName -Path $migrationPath
    Invoke-SqlQuery -Database $databaseName -Query @'
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

IF (SELECT COUNT(*) FROM dbo.amenities WHERE status = 'ACTIVE') <> 8
    THROW 51088, 'Amenity seed or idempotence count is incorrect.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.property_amenities') AND name='UX_property_amenities_hotel_amenity')
    THROW 51089, 'Property amenity ownership index is missing.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.room_type_amenities') AND name='IX_room_type_amenities_amenity_hotel_room_type')
    THROW 51090, 'Room-type amenity search index is missing.', 1;

DECLARE @wifi BIGINT = (SELECT id FROM dbo.amenities WHERE code='WIFI');
INSERT dbo.property_amenities(hotel_id, amenity_id, created_at, updated_at)
VALUES(1, @wifi, SYSUTCDATETIME(), SYSUTCDATETIME());
INSERT dbo.room_type_amenities(hotel_id, room_type_id, amenity_id, created_at, updated_at)
VALUES(1, 10, @wifi, SYSUTCDATETIME(), SYSUTCDATETIME());

BEGIN TRY
    INSERT dbo.property_amenities(hotel_id, amenity_id) VALUES(1, @wifi);
    THROW 51091, 'Duplicate property assignment was accepted.', 1;
END TRY
BEGIN CATCH
    IF ERROR_NUMBER() = 51091 THROW;
END CATCH;

BEGIN TRY
    INSERT dbo.room_type_amenities(hotel_id, room_type_id, amenity_id) VALUES(2, 10, @wifi);
    THROW 51092, 'Cross-property room-type assignment was accepted.', 1;
END TRY
BEGIN CATCH
    IF ERROR_NUMBER() = 51092 THROW;
END CATCH;
'@
    Write-Output 'Amenity SQL Server catalog/ownership/index/idempotence validation passed.'
} finally {
    $PSNativeCommandUseErrorActionPreference = $previousNativeErrorPreference
    if ($databaseCreated -and $databaseName -match '^Amenities_[0-9]+_[0-9]{14}$') {
        try {
            Invoke-SqlQuery -Database master -Query "ALTER DATABASE [$databaseName] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$databaseName];"
        } catch {
            Write-Warning "Could not drop disposable database $databaseName."
        }
    }
    if ($containerStarted -and $containerName -match '^luxestay-amenities-[0-9]+-[0-9]{14}$') {
        docker rm --force $containerName *> $null
    }
}

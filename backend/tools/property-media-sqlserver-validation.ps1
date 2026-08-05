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
$migrationPath = Join-Path $backendRoot 'src/main/resources/db/migration/V80__property_media_registry.sql'
$runId = '{0}-{1}' -f $PID, (Get-Date -Format 'yyyyMMddHHmmss')
$containerName = 'luxestay-property-media-{0}' -f $runId
$databaseName = 'PropertyMedia_{0}' -f ($runId -replace '-', '_')
$sqlPort = if ($Port -gt 0) { $Port } else { Get-Random -Minimum 16434 -Maximum 17434 }
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
$schemaPath = Join-Path ([IO.Path]::GetTempPath()) ('luxestay-property-media-{0}.sql' -f $runId)
$containerStarted = $false
$databaseCreated = $false

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

function Invoke-SqlFile {
    param(
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$Path
    )

    & sqlcmd -S $server -U sa -P $saPassword -C -b -d $Database -i $Path
    if ($LASTEXITCODE -ne 0) {
        throw "sqlcmd failed for $Path"
    }
}

try {
    if (-not $useExistingServer) {
        docker run --detach --name $containerName `
            --env ACCEPT_EULA=Y `
            --env MSSQL_PID=Developer `
            --env MSSQL_SA_PASSWORD=$saPassword `
            --publish "${sqlPort}:1433" `
            $Image | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw 'Could not start the isolated SQL Server container.'
        }
        $containerStarted = $true

        $ready = $false
        for ($attempt = 1; $attempt -le 60; $attempt++) {
            try {
                $null = & sqlcmd -S $server -U sa -P $saPassword -C -b -d master -Q 'SELECT 1' 2>&1
            } catch {
                # SQL Server emits connection errors while the container is starting.
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
    } else {
        Invoke-SqlQuery -Database master -Query 'SELECT 1;'
    }

    Invoke-SqlQuery -Database master -Query "CREATE DATABASE [$databaseName];"
    $databaseCreated = $true

    $schema = @'
CREATE TABLE dbo.hotels (
    id BIGINT NOT NULL PRIMARY KEY,
    name NVARCHAR(255) NULL,
    name_vi NVARCHAR(255) NULL
);
CREATE TABLE dbo.room_types (
    id BIGINT NOT NULL PRIMARY KEY,
    hotel_id BIGINT NOT NULL REFERENCES dbo.hotels(id),
    name_vi NVARCHAR(255) NULL
);
CREATE TABLE dbo.rooms (
    id BIGINT NOT NULL PRIMARY KEY,
    hotel_id BIGINT NOT NULL REFERENCES dbo.hotels(id),
    room_type_id BIGINT NOT NULL REFERENCES dbo.room_types(id),
    room_number NVARCHAR(50) NOT NULL
);
CREATE TABLE dbo.property_images (
    id BIGINT NOT NULL PRIMARY KEY,
    hotel_id BIGINT NOT NULL REFERENCES dbo.hotels(id),
    image_url NVARCHAR(1000) NOT NULL,
    is_primary BIT NOT NULL,
    sort_order INT NOT NULL,
    alt_text_vi NVARCHAR(255) NULL,
    alt_text_en NVARCHAR(255) NULL,
    is_demo BIT NOT NULL
);
CREATE TABLE dbo.room_type_images (
    id BIGINT NOT NULL PRIMARY KEY,
    room_type_id BIGINT NOT NULL REFERENCES dbo.room_types(id),
    image_url NVARCHAR(1000) NOT NULL,
    is_primary BIT NOT NULL,
    sort_order INT NOT NULL,
    alt_text_vi NVARCHAR(255) NULL,
    alt_text_en NVARCHAR(255) NULL,
    is_demo BIT NOT NULL
);
CREATE TABLE dbo.room_images (
    id BIGINT NOT NULL PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES dbo.rooms(id),
    image_url NVARCHAR(1000) NOT NULL,
    is_primary BIT NOT NULL
);

INSERT dbo.hotels(id, name, name_vi) VALUES (1, N'Legacy Hotel', N'Khách sạn cũ');
INSERT dbo.room_types(id, hotel_id, name_vi) VALUES (10, 1, N'Phòng đôi');
INSERT dbo.rooms(id, hotel_id, room_type_id, room_number) VALUES (100, 1, 10, N'101');
INSERT dbo.property_images(id, hotel_id, image_url, is_primary, sort_order, alt_text_vi, alt_text_en, is_demo)
VALUES (1000, 1, N'https://cdn.example.com/property.jpg', 1, 0, NULL, NULL, 0);
INSERT dbo.room_type_images(id, room_type_id, image_url, is_primary, sort_order, alt_text_vi, alt_text_en, is_demo)
VALUES (1001, 10, N'/assets/legacy-room-type.webp', 1, 0, NULL, NULL, 0);
INSERT dbo.room_images(id, room_id, image_url, is_primary)
VALUES (1002, 100, N'/api/public/uploads/property-1-legacy.png', 1);
'@
    [IO.File]::WriteAllText($schemaPath, $schema, [Text.UTF8Encoding]::new($false))
    Invoke-SqlFile -Database $databaseName -Path $schemaPath
    Invoke-SqlFile -Database $databaseName -Path $migrationPath
    Invoke-SqlFile -Database $databaseName -Path $migrationPath

    $assertions = @'
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
IF (SELECT COUNT(*) FROM dbo.property_media) <> 3
    THROW 51081, 'Legacy media backfill count is incorrect.', 1;
IF EXISTS (SELECT 1 FROM dbo.property_images WHERE media_id IS NULL)
    THROW 51082, 'Property image ownership was not backfilled.', 1;
IF EXISTS (SELECT 1 FROM dbo.room_type_images WHERE media_id IS NULL)
    THROW 51083, 'Room-type image ownership was not backfilled.', 1;
IF EXISTS (SELECT 1 FROM dbo.room_images WHERE media_id IS NULL OR alt_text_vi IS NULL OR sort_order < 0)
    THROW 51084, 'Room image metadata was not normalized.', 1;
IF (SELECT COUNT(*) FROM sys.foreign_keys WHERE name IN (
        'FK_property_images_media', 'FK_room_type_images_media', 'FK_room_images_media')) <> 3
    THROW 51085, 'Media ownership foreign keys are missing.', 1;

BEGIN TRY
    INSERT dbo.property_media(
        hotel_id, source_type, public_url, status, is_demo, created_at, updated_at)
    VALUES (1, 'MANAGED_UPLOAD', N'/api/public/uploads/invalid.png', 'ACTIVE', 0, SYSUTCDATETIME(), SYSUTCDATETIME());
    THROW 51086, 'Managed upload metadata constraint accepted an invalid row.', 1;
END TRY
BEGIN CATCH
    IF ERROR_NUMBER() = 51086 THROW;
END CATCH;
'@
    Invoke-SqlQuery -Database $databaseName -Query $assertions
    Write-Output 'Property media SQL Server migration/backfill/idempotence validation passed.'
} finally {
    $PSNativeCommandUseErrorActionPreference = $previousNativeErrorPreference
    if (Test-Path -LiteralPath $schemaPath) {
        Remove-Item -LiteralPath $schemaPath -Force
    }
    if ($databaseCreated -and $databaseName -match '^PropertyMedia_[0-9]+_[0-9]{14}$') {
        try {
            Invoke-SqlQuery -Database master -Query "ALTER DATABASE [$databaseName] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$databaseName];"
        } catch {
            Write-Warning "Could not drop disposable database $databaseName."
        }
    }
    if ($containerStarted -and $containerName -match '^luxestay-property-media-[0-9]+-[0-9]{14}$') {
        docker rm --force $containerName *> $null
    }
}

param([string]$Image = 'mcr.microsoft.com/mssql/server:2022-latest', [int]$Port = 0)

$ErrorActionPreference = 'Stop'
$previousNativeErrorPreference = $PSNativeCommandUseErrorActionPreference
$PSNativeCommandUseErrorActionPreference = $false
$backendRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$migrationPath = Join-Path $backendRoot 'src/main/resources/db/migration/V82__operational_policy_versions.sql'
$runId = '{0}-{1}' -f $PID, (Get-Date -Format 'yyyyMMddHHmmss')
$containerName = 'luxestay-policy-{0}' -f $runId
$databaseName = 'Policy_{0}' -f ($runId -replace '-', '_')
$sqlPort = if ($Port -gt 0) { $Port } else { Get-Random -Minimum 18436 -Maximum 19436 }
$saPassword = 'Lx!{0}aA9' -f ([guid]::NewGuid().ToString('N').Substring(0, 20))
$server = '127.0.0.1,{0}' -f $sqlPort
$containerStarted = $false
$databaseCreated = $false

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
    $containerStarted = $true
    $ready = $false
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        try {
            $null = & sqlcmd -S $server -U sa -P $saPassword -C -b -d master -Q 'SELECT 1' 2>&1
        } catch {
            # SQL Server emits handshake errors while the isolated container is starting.
        }
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw 'SQL Server did not become ready.' }

    Invoke-SqlQuery master "CREATE DATABASE [$databaseName];"
    $databaseCreated = $true
    Invoke-SqlQuery $databaseName @'
CREATE TABLE dbo.hotels(id BIGINT NOT NULL PRIMARY KEY);
CREATE TABLE dbo.reservations(id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY, hotel_id BIGINT NOT NULL REFERENCES dbo.hotels(id));
INSERT dbo.hotels(id) VALUES (1), (2);
INSERT dbo.reservations(hotel_id) VALUES (1);
'@
    Invoke-SqlFile $databaseName $migrationPath
    Invoke-SqlFile $databaseName $migrationPath
    Invoke-SqlQuery $databaseName @'
INSERT dbo.property_policy_versions(hotel_id, version_number, status, effective_from, check_in_vi, check_out_vi,
 cancellation_vi, child_policy_vi, pet_policy_vi, smoking_policy_vi, house_rules_vi)
VALUES (1, 1, 'PUBLISHED', '2026-08-01', N'Sau 14:00', N'Trước 12:00', N'Liên hệ cơ sở', N'Theo sức chứa', N'Không', N'Không', N'Giữ yên lặng');

DECLARE @policy_id BIGINT = SCOPE_IDENTITY();
UPDATE dbo.reservations SET operational_policy_id=@policy_id, operational_policy_version=1,
 operational_policy_effective_from='2026-08-01', operational_policy_snapshot=N'policy-version-1' WHERE id=1;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_property_policy_effective') THROW 51000, 'Missing effective index', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.reservations WHERE operational_policy_version=1 AND operational_policy_snapshot IS NOT NULL)
 THROW 51000, 'Snapshot columns are not writable', 1;
'@

    $duplicateRejected = $false
    try {
        Invoke-SqlQuery $databaseName "INSERT dbo.property_policy_versions(hotel_id,version_number,status,effective_from,check_in_vi,check_out_vi,cancellation_vi,child_policy_vi,pet_policy_vi,smoking_policy_vi,house_rules_vi) VALUES(1,1,'DRAFT','2026-09-01',N'a',N'b',N'c',N'd',N'e',N'f',N'g');"
    } catch { $duplicateRejected = $true }
    if (-not $duplicateRejected) { throw 'Duplicate property policy version was accepted.' }

    Write-Output 'Operational policy SQL Server validation passed: idempotent migration, indexes, snapshot columns and uniqueness.'
} finally {
    if ($databaseCreated) { try { Invoke-SqlQuery master "ALTER DATABASE [$databaseName] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$databaseName];" } catch {} }
    if ($containerStarted) { docker rm --force $containerName | Out-Null }
    $PSNativeCommandUseErrorActionPreference = $previousNativeErrorPreference
}

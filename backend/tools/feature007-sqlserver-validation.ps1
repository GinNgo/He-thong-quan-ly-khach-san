param(
    [string]$Server = '.\MSSQLSERVER01',
    [switch]$WithoutLegacyFinancialData,
    [switch]$KeepDatabase
)

$ErrorActionPreference = 'Stop'
$databaseName = 'Feature007Validation_{0}_{1}' -f $PID, (Get-Date -Format 'yyyyMMddHHmmss')
$migrationRoot = Join-Path $PSScriptRoot '..\src\main\resources\db\migration'
$preflight = Join-Path $PSScriptRoot '..\src\main\resources\db\preflight\feature007_financial_preflight.sql'
$tempFiles = [System.Collections.Generic.List[string]]::new()

function Invoke-SqlText {
    param([string]$Database, [string]$Sql)
    $tempFile = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), ('feature007-{0}.sql' -f [guid]::NewGuid()))
    $tempFiles.Add($tempFile)
    Set-Content -LiteralPath $tempFile -Value $Sql -Encoding UTF8
    & sqlcmd -S $Server -E -C -b -d $Database -i $tempFile
    if ($LASTEXITCODE -ne 0) { throw "sqlcmd failed for database $Database" }
}

function Invoke-SqlFile {
    param([string]$Database, [string]$Path)
    & sqlcmd -S $Server -E -C -b -d $Database -i $Path
    if ($LASTEXITCODE -ne 0) { throw "sqlcmd failed for $Path" }
}

try {
    Invoke-SqlText -Database 'master' -Sql "CREATE DATABASE [$databaseName];"
    Write-Output "Created isolated database $databaseName"

    $fixture = @'
CREATE TABLE dbo.hotels (id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY);
CREATE TABLE dbo.users (id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY);
CREATE TABLE dbo.reservations (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    hotel_id BIGINT NULL,
    CONSTRAINT FK_fixture_reservation_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id)
);
CREATE TABLE dbo.payments (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    reservation_id BIGINT NULL,
    amount DECIMAL(19,2) NOT NULL,
    payment_method VARCHAR(40) NULL,
    status VARCHAR(30) NOT NULL,
    transaction_id VARCHAR(200) NULL,
    payment_date DATETIME2 NULL,
    created_at DATETIME2 NULL
);
CREATE TABLE dbo.invoices (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    invoice_code VARCHAR(80) NOT NULL,
    total_amount DECIMAL(19,2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME2 NULL,
    updated_at DATETIME2 NULL
);
CREATE TABLE dbo.subscription_plans (id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY);
CREATE TABLE dbo.app_module (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    code VARCHAR(80) NOT NULL,
    name NVARCHAR(255) NOT NULL
);
CREATE TABLE dbo.app_function (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    name NVARCHAR(255) NOT NULL,
    url VARCHAR(500) NULL,
    icon VARCHAR(100) NULL,
    sort_order INT NULL,
    module_id BIGINT NOT NULL,
    CONSTRAINT FK_fixture_function_module FOREIGN KEY (module_id) REFERENCES dbo.app_module(id)
);
CREATE TABLE dbo.app_role (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE
);
CREATE TABLE dbo.app_role_permission (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    function_id BIGINT NOT NULL,
    action_mask INT NOT NULL,
    CONSTRAINT FK_fixture_permission_role FOREIGN KEY (role_id) REFERENCES dbo.app_role(id),
    CONSTRAINT FK_fixture_permission_function FOREIGN KEY (function_id) REFERENCES dbo.app_function(id)
);

INSERT INTO dbo.hotels DEFAULT VALUES;
INSERT INTO dbo.users DEFAULT VALUES;
INSERT INTO dbo.reservations(hotel_id) VALUES (1);
INSERT INTO dbo.subscription_plans DEFAULT VALUES;
INSERT INTO dbo.app_module(code, name) VALUES ('FINANCE', 'Finance');
INSERT INTO dbo.app_role(code) VALUES ('SUPER_ADMIN');
'@
    Invoke-SqlText -Database $databaseName -Sql $fixture
    if (-not $WithoutLegacyFinancialData) {
        Invoke-SqlText -Database $databaseName -Sql @'
INSERT INTO dbo.payments(reservation_id, amount, payment_method, status, transaction_id, created_at)
VALUES (1, 500000, 'SIMULATOR', 'SUCCEEDED', 'fixture-payment-1', SYSUTCDATETIME());
INSERT INTO dbo.invoices(reservation_id, invoice_code, total_amount, status, created_at, updated_at)
VALUES (1, 'fixture-invoice-1', 500000, 'PAID', SYSUTCDATETIME(), SYSUTCDATETIME());
'@
    }
    Invoke-SqlFile -Database $databaseName -Path $preflight
    Write-Output 'Positive preflight passed'

    $migrationNames = 21..29 | ForEach-Object {
        Get-ChildItem -LiteralPath $migrationRoot -Filter ("V{0}__*.sql" -f $_) | Select-Object -ExpandProperty FullName
    }
    foreach ($migration in $migrationNames) {
        Invoke-SqlFile -Database $databaseName -Path $migration
        Write-Output ("Applied {0}" -f (Split-Path $migration -Leaf))
    }

    foreach ($migration in $migrationNames) {
        Invoke-SqlFile -Database $databaseName -Path $migration
    }
    Write-Output 'Repeat execution passed'

    $expectedLegacyRows = if ($WithoutLegacyFinancialData) { 0 } else { 1 }
    $assertions = @'
IF OBJECT_ID('dbo.property_financial_transactions', 'U') IS NULL THROW 51101, 'Property ledger missing', 1;
IF OBJECT_ID('dbo.platform_financial_transactions', 'U') IS NULL THROW 51102, 'Platform ledger missing', 1;
IF OBJECT_ID('dbo.financial_idempotency_records', 'U') IS NULL THROW 51103, 'Idempotency table missing', 1;
IF (SELECT COUNT(*) FROM dbo.property_financial_transactions WHERE idempotency_identity = 'LEGACY:payments:1') <> __EXPECTED_LEGACY_ROWS__
    THROW 51104, 'Legacy payment backfill is not idempotent', 1;
IF (SELECT COUNT(*) FROM dbo.property_invoices WHERE invoice_number = 'LEGACY-fixture-invoice-1') <> __EXPECTED_LEGACY_ROWS__
    THROW 51105, 'Legacy invoice backfill is not idempotent', 1;
IF (SELECT COUNT(*) FROM dbo.app_function WHERE code IN (
    'PROPERTY_PAYMENT_CONFIG','PROPERTY_PAYMENT_CONFIRM_MANUAL','PROPERTY_REFUND','RESERVATION_SERVICE',
    'RESERVATION_SURCHARGE','RESERVATION_DEBT_OVERRIDE','INVOICE_ADJUST','PLATFORM_BILLING',
    'PLATFORM_REFUND','PLATFORM_REVENUE','PAYMENT_READINESS')) <> 11
    THROW 51106, 'Financial permissions are incomplete', 1;
SELECT
    (SELECT COUNT(*) FROM dbo.property_financial_transactions) AS property_ledger_rows,
    (SELECT COUNT(*) FROM dbo.property_invoices) AS property_invoice_rows,
    (SELECT COUNT(*) FROM dbo.app_function WHERE code LIKE 'PROPERTY_%' OR code LIKE 'PLATFORM_%' OR code = 'PAYMENT_READINESS') AS financial_functions;
'@
    $assertions = $assertions.Replace('__EXPECTED_LEGACY_ROWS__', [string]$expectedLegacyRows)
    Invoke-SqlText -Database $databaseName -Sql $assertions

    Invoke-SqlText -Database $databaseName -Sql @'
INSERT INTO dbo.payments(reservation_id, amount, payment_method, status, transaction_id, created_at)
VALUES (999999, 100000, 'SIMULATOR', 'SUCCEEDED', 'orphan-payment', SYSUTCDATETIME());
'@
    $negativeFailed = $false
    try {
        Invoke-SqlFile -Database $databaseName -Path $preflight
    } catch {
        $negativeFailed = $true
        Write-Output 'Negative orphan preflight failed as expected'
    }
    if (-not $negativeFailed) { throw 'Negative preflight unexpectedly passed' }
    Invoke-SqlText -Database $databaseName -Sql "DELETE FROM dbo.payments WHERE transaction_id = 'orphan-payment';"

    Write-Output "Feature 007 SQL Server validation passed for $databaseName"
} finally {
    if (-not $KeepDatabase -and $databaseName -match '^Feature007Validation_[0-9]+_[0-9]{14}$') {
        try {
            Invoke-SqlText -Database 'master' -Sql "ALTER DATABASE [$databaseName] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$databaseName];"
            Write-Output "Dropped isolated database $databaseName"
        } catch {
            Write-Warning "Could not drop isolated database ${databaseName}: $($_.Exception.Message)"
        }
    }
    foreach ($tempFile in $tempFiles) {
        if (Test-Path -LiteralPath $tempFile) { Remove-Item -LiteralPath $tempFile -Force }
    }
}

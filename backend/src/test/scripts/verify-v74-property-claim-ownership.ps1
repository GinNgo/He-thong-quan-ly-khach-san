param([string]$Image = "mcr.microsoft.com/mssql/server:2022-latest")

$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $false
$container = "luxestay-v74-$PID"
$password = "V74_Test!Pass_2026"
$migration = (Resolve-Path "$PSScriptRoot/../../main/resources/db/migration/V74__property_claim_ownership_uniqueness.sql").Path

function Invoke-Sql([string]$database, [string]$query, [switch]$AllowFailure) {
    $arguments = @("exec", $container, "/opt/mssql-tools18/bin/sqlcmd", "-C", "-S", "localhost", "-U", "sa", "-P", $password, "-d", $database, "-b", "-Q", $query)
    & docker @arguments
    if (-not $AllowFailure -and $LASTEXITCODE -ne 0) { throw "SQL command failed." }
    return $LASTEXITCODE
}

try {
    docker run -d --rm --name $container -e ACCEPT_EULA=Y -e "MSSQL_SA_PASSWORD=$password" -p 0:1433 $Image | Out-Null
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $previousPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        docker exec $container /opt/mssql-tools18/bin/sqlcmd -l 2 -C -S localhost -U sa -P $password -Q "SELECT 1" *> $null
        $ErrorActionPreference = $previousPreference
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Seconds 1
    }
    if ($LASTEXITCODE -ne 0) { throw "SQL Server did not become ready." }

    Invoke-Sql master "CREATE DATABASE V74Clean; CREATE DATABASE V74Duplicate;"
    foreach ($database in @("V74Clean", "V74Duplicate")) {
        Invoke-Sql $database "CREATE TABLE dbo.hotels(id BIGINT PRIMARY KEY); CREATE TABLE dbo.property_claim_requests(id BIGINT IDENTITY PRIMARY KEY, property_id BIGINT NOT NULL, requester_user_id BIGINT NOT NULL, [status] VARCHAR(30) NOT NULL); CREATE TABLE dbo.user_properties(id BIGINT IDENTITY PRIMARY KEY, user_id BIGINT NOT NULL, hotel_id BIGINT NOT NULL, relationship_type VARCHAR(30) NOT NULL, [status] VARCHAR(30) NOT NULL, is_primary_owner BIT NOT NULL);"
    }
    docker cp $migration "${container}:/tmp/V74.sql" | Out-Null

    foreach ($run in 1..2) {
        docker exec $container /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P $password -d V74Clean -b -i /tmp/V74.sql | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "V74 clean/idempotent run $run failed." }
    }
    $indexCount = docker exec $container /opt/mssql-tools18/bin/sqlcmd -C -h -1 -W -S localhost -U sa -P $password -d V74Clean -Q "SET NOCOUNT ON; SELECT COUNT(*) FROM sys.indexes WHERE name IN ('UX_property_claim_pending_requester','UX_user_property_owner','UX_user_property_primary_active_owner');"
    if (($indexCount | Out-String).Trim() -ne "3") { throw "Expected all three V74 indexes." }

    Invoke-Sql V74Duplicate "INSERT dbo.property_claim_requests(property_id,requester_user_id,[status]) VALUES(10,7,'PENDING'),(10,7,'PENDING'); INSERT dbo.user_properties(user_id,hotel_id,relationship_type,[status],is_primary_owner) VALUES(7,10,'OWNER','PENDING',0);"
    $before = docker exec $container /opt/mssql-tools18/bin/sqlcmd -C -h -1 -W -S localhost -U sa -P $password -d V74Duplicate -Q "SET NOCOUNT ON; SELECT CONCAT(COUNT_BIG(*),':',CHECKSUM_AGG(BINARY_CHECKSUM(*))) FROM dbo.property_claim_requests;"
    docker exec $container /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P $password -d V74Duplicate -b -i /tmp/V74.sql | Out-Null
    if ($LASTEXITCODE -eq 0) { throw "V74 must fail closed when duplicates exist." }
    $after = docker exec $container /opt/mssql-tools18/bin/sqlcmd -C -h -1 -W -S localhost -U sa -P $password -d V74Duplicate -Q "SET NOCOUNT ON; SELECT CONCAT(COUNT_BIG(*),':',CHECKSUM_AGG(BINARY_CHECKSUM(*))) FROM dbo.property_claim_requests;"
    if (($before | Out-String).Trim() -ne ($after | Out-String).Trim()) { throw "Duplicate preflight changed source rows." }

    Write-Output "V74_SQLSERVER_PASS clean=1 repeat=1 duplicate_fail_closed=1 checksum_unchanged=1"
} finally {
    docker rm -f $container 2>$null | Out-Null
}

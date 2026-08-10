SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;
DECLARE @table sysname, @cols nvarchar(max), @sql nvarchar(max);
DECLARE tables CURSOR LOCAL FAST_FORWARD FOR
    SELECT name FROM (VALUES (N'hotels'), (N'room_types'), (N'rooms'), (N'users')) v(name);
OPEN tables;
FETCH NEXT FROM tables INTO @table;
WHILE @@FETCH_STATUS = 0
BEGIN
    SELECT @cols = STRING_AGG(QUOTENAME(c.name), N',') WITHIN GROUP (ORDER BY c.column_id)
    FROM HotelDB.sys.columns c
    JOIN HotelDBCandidate.sys.columns s
      ON s.object_id = OBJECT_ID(N'HotelDBCandidate.dbo.' + @table)
     AND s.name = c.name
    WHERE c.object_id = OBJECT_ID(N'HotelDB.dbo.' + @table)
      AND c.is_identity = 0
      AND c.is_computed = 0
      AND c.system_type_id <> 189;

    DECLARE @sourceCols nvarchar(max) = @cols;
    IF @table = N'rooms' AND COL_LENGTH(N'HotelDB.dbo.rooms', 'version') IS NOT NULL
        AND COL_LENGTH(N'HotelDBCandidate.dbo.rooms', 'version') IS NULL
        SET @cols += N',[version]';
    IF @table = N'users' AND COL_LENGTH(N'HotelDB.dbo.users', 'failed_login_count') IS NOT NULL
        AND COL_LENGTH(N'HotelDBCandidate.dbo.users', 'failed_login_count') IS NULL
        SET @cols += N',[failed_login_count]';
    IF @table = N'users' AND COL_LENGTH(N'HotelDB.dbo.users', 'version') IS NOT NULL
        AND COL_LENGTH(N'HotelDBCandidate.dbo.users', 'version') IS NULL
        SET @cols += N',[version]';

    DECLARE @selectCols nvarchar(max) = @sourceCols;
    IF @table = N'rooms' AND COL_LENGTH(N'HotelDB.dbo.rooms', 'version') IS NOT NULL
        AND COL_LENGTH(N'HotelDBCandidate.dbo.rooms', 'version') IS NULL
        SET @selectCols += N',0';
    IF @table = N'users' AND COL_LENGTH(N'HotelDB.dbo.users', 'failed_login_count') IS NOT NULL
        AND COL_LENGTH(N'HotelDBCandidate.dbo.users', 'failed_login_count') IS NULL
        SET @selectCols += N',0';
    IF @table = N'users' AND COL_LENGTH(N'HotelDB.dbo.users', 'version') IS NOT NULL
        AND COL_LENGTH(N'HotelDBCandidate.dbo.users', 'version') IS NULL
        SET @selectCols += N',0';

    SET @sql = N'INSERT INTO HotelDB.dbo.' + QUOTENAME(@table) + N' (' + @cols + N') '
        + N'SELECT ' + @selectCols + N' FROM HotelDBCandidate.dbo.' + QUOTENAME(@table) + N' src '
        + N'WHERE NOT EXISTS (SELECT 1 FROM HotelDB.dbo.' + QUOTENAME(@table) + N' dst WHERE dst.id = src.id);';
    EXEC sp_executesql @sql;
    FETCH NEXT FROM tables INTO @table;
END
CLOSE tables;
DEALLOCATE tables;
SELECT N'hotels' AS table_name, COUNT_BIG(*) AS total FROM HotelDB.dbo.hotels
UNION ALL SELECT N'room_types', COUNT_BIG(*) FROM HotelDB.dbo.room_types
UNION ALL SELECT N'rooms', COUNT_BIG(*) FROM HotelDB.dbo.rooms
UNION ALL SELECT N'users', COUNT_BIG(*) FROM HotelDB.dbo.users;

IF OBJECT_ID('dbo.housekeeping_tasks', 'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.housekeeping_tasks', 'started_at') IS NULL
        ALTER TABLE dbo.housekeeping_tasks ADD started_at DATETIME2 NULL;
    IF COL_LENGTH('dbo.housekeeping_tasks', 'version') IS NULL
    BEGIN
        ALTER TABLE dbo.housekeeping_tasks ADD version BIGINT NOT NULL CONSTRAINT DF_housekeeping_tasks_version DEFAULT 0;
    END;
    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE object_id = OBJECT_ID('dbo.housekeeping_tasks')
          AND name = 'IX_housekeeping_tasks_hotel_status_assignee'
    )
        CREATE INDEX IX_housekeeping_tasks_hotel_status_assignee
            ON dbo.housekeeping_tasks(hotel_id, status, assigned_to_user_id, assigned_at);
END;
GO

IF OBJECT_ID('dbo.operational_tasks', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.operational_tasks (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        public_id VARCHAR(64) NOT NULL,
        hotel_id BIGINT NOT NULL,
        task_type VARCHAR(40) NOT NULL,
        function_code VARCHAR(60) NOT NULL,
        required_action INT NOT NULL,
        aggregate_type VARCHAR(60) NOT NULL,
        aggregate_id VARCHAR(100) NOT NULL,
        status VARCHAR(20) NOT NULL,
        assigned_to_user_id BIGINT NULL,
        assigned_by_user_id BIGINT NULL,
        assigned_at DATETIME2 NULL,
        started_at DATETIME2 NULL,
        completed_at DATETIME2 NULL,
        effect_key VARCHAR(160) NOT NULL,
        result_reference VARCHAR(160) NULL,
        note NVARCHAR(1000) NULL,
        version BIGINT NOT NULL CONSTRAINT DF_operational_task_version DEFAULT 0,
        created_at DATETIME2 NULL,
        updated_at DATETIME2 NULL,
        created_by VARCHAR(255) NULL,
        updated_by VARCHAR(255) NULL,
        CONSTRAINT FK_operational_task_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_operational_task_assignee FOREIGN KEY (assigned_to_user_id) REFERENCES dbo.users(id),
        CONSTRAINT FK_operational_task_assigner FOREIGN KEY (assigned_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT UQ_operational_task_public UNIQUE (public_id),
        CONSTRAINT UQ_operational_task_effect UNIQUE (hotel_id, effect_key),
        CONSTRAINT CK_operational_task_status CHECK (status IN ('OPEN','ASSIGNED','IN_PROGRESS','COMPLETED','CANCELLED','BLOCKED')),
        CONSTRAINT CK_operational_task_action CHECK (required_action > 0 AND (required_action & ~127) = 0)
    );
    CREATE INDEX IX_operational_task_queue ON dbo.operational_tasks(hotel_id, status, task_type, created_at);
END;

IF OBJECT_ID('dbo.operational_task_history', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.operational_task_history (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        task_id BIGINT NOT NULL,
        previous_status VARCHAR(20) NULL,
        new_status VARCHAR(20) NOT NULL,
        actor_user_id BIGINT NULL,
        reason NVARCHAR(500) NULL,
        result_reference VARCHAR(160) NULL,
        correlation_id VARCHAR(100) NULL,
        occurred_at DATETIME2 NOT NULL,
        CONSTRAINT FK_operational_task_history_task FOREIGN KEY (task_id) REFERENCES dbo.operational_tasks(id),
        CONSTRAINT FK_operational_task_history_actor FOREIGN KEY (actor_user_id) REFERENCES dbo.users(id)
    );
    CREATE INDEX IX_operational_task_history_task ON dbo.operational_task_history(task_id, occurred_at);
END;

IF OBJECT_ID('dbo.app_function', 'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM dbo.app_function WHERE code = 'OPERATIONAL_TASK')
BEGIN
    DECLARE @module_id BIGINT = (SELECT TOP 1 id FROM dbo.app_module WHERE code IN ('MANAGEMENT','HOTEL') ORDER BY id);
    IF @module_id IS NULL SELECT @module_id = MIN(id) FROM dbo.app_module;
    INSERT INTO dbo.app_function(module_id, code, name, url, icon, sort_order,
                                 supported_action_mask, scope_type, active, version)
    VALUES (@module_id, 'OPERATIONAL_TASK', N'Tác vụ vận hành', '/management/tasks', 'pi pi-list-check', 90,
            97, 'PROPERTY', 1, 0);
END;

IF OBJECT_ID('dbo.app_role', 'U') IS NOT NULL
   AND OBJECT_ID('dbo.app_role_permission', 'U') IS NOT NULL
   AND EXISTS (SELECT 1 FROM dbo.app_function WHERE code = 'OPERATIONAL_TASK')
BEGIN
    ;WITH defaults AS (
        SELECT role.id AS role_id, function_row.id AS function_id,
               CASE
                   WHEN role.code IN ('PROPERTY_OWNER','HOTEL_ADMIN','HOTEL_MANAGER') THEN 97
                   WHEN role.code IN ('RECEPTIONIST','HOUSEKEEPING') THEN 65
                   WHEN role.code = 'ACCOUNTANT' THEN 1
               END AS action_mask
        FROM dbo.app_role role
        CROSS JOIN dbo.app_function function_row
        WHERE function_row.code = 'OPERATIONAL_TASK'
          AND role.code IN ('PROPERTY_OWNER','HOTEL_ADMIN','HOTEL_MANAGER','RECEPTIONIST','HOUSEKEEPING','ACCOUNTANT')
    )
    MERGE dbo.app_role_permission AS target
    USING defaults AS source
    ON target.role_id = source.role_id AND target.function_id = source.function_id
    WHEN MATCHED THEN UPDATE SET action_mask = target.action_mask | source.action_mask
    WHEN NOT MATCHED THEN INSERT(role_id, function_id, action_mask)
        VALUES(source.role_id, source.function_id, source.action_mask);
END;

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

IF OBJECT_ID('dbo.maintenance_work_orders', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.maintenance_work_orders (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        hotel_id BIGINT NOT NULL,
        room_id BIGINT NOT NULL,
        reason NVARCHAR(1000) NOT NULL,
        priority VARCHAR(20) NOT NULL,
        assignee_user_id BIGINT NULL,
        scheduled_start DATETIME2 NULL,
        scheduled_end DATETIME2 NULL,
        status VARCHAR(20) NOT NULL,
        resolution_note NVARCHAR(2000) NULL,
        started_at DATETIME2 NULL,
        completed_at DATETIME2 NULL,
        version BIGINT NOT NULL CONSTRAINT DF_maintenance_work_orders_version DEFAULT 0,
        created_at DATETIME2 NULL,
        updated_at DATETIME2 NULL,
        created_by VARCHAR(255) NULL,
        updated_by VARCHAR(255) NULL,
        CONSTRAINT FK_maintenance_work_orders_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_maintenance_work_orders_room FOREIGN KEY (room_id) REFERENCES dbo.rooms(id),
        CONSTRAINT FK_maintenance_work_orders_assignee FOREIGN KEY (assignee_user_id) REFERENCES dbo.users(id),
        CONSTRAINT CK_maintenance_work_orders_priority CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
        CONSTRAINT CK_maintenance_work_orders_status CHECK (status IN ('OPEN','IN_PROGRESS','COMPLETED','CANCELLED')),
        CONSTRAINT CK_maintenance_work_orders_schedule CHECK (scheduled_end IS NULL OR scheduled_start IS NULL OR scheduled_end > scheduled_start)
    );
END;

IF OBJECT_ID('dbo.maintenance_work_order_history', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.maintenance_work_order_history (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        hotel_id BIGINT NOT NULL,
        work_order_id BIGINT NOT NULL,
        from_status VARCHAR(20) NULL,
        to_status VARCHAR(20) NOT NULL,
        reason NVARCHAR(1000) NOT NULL,
        created_at DATETIME2 NULL,
        updated_at DATETIME2 NULL,
        created_by VARCHAR(255) NULL,
        updated_by VARCHAR(255) NULL,
        CONSTRAINT FK_maintenance_history_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_maintenance_history_work_order FOREIGN KEY (work_order_id) REFERENCES dbo.maintenance_work_orders(id),
        CONSTRAINT CK_maintenance_history_from_status CHECK (from_status IS NULL OR from_status IN ('OPEN','IN_PROGRESS','COMPLETED','CANCELLED')),
        CONSTRAINT CK_maintenance_history_to_status CHECK (to_status IN ('OPEN','IN_PROGRESS','COMPLETED','CANCELLED'))
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_maintenance_work_orders_hotel_status')
    CREATE INDEX IX_maintenance_work_orders_hotel_status ON dbo.maintenance_work_orders(hotel_id, status, id DESC);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_maintenance_work_orders_active_room')
    CREATE UNIQUE INDEX UX_maintenance_work_orders_active_room ON dbo.maintenance_work_orders(room_id)
    WHERE status IN ('OPEN','IN_PROGRESS');

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_maintenance_history_order')
    CREATE INDEX IX_maintenance_history_order ON dbo.maintenance_work_order_history(hotel_id, work_order_id, id);

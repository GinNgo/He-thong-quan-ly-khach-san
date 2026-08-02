-- Add optimistic room versioning and database checks for canonical state ownership.
IF OBJECT_ID('dbo.rooms', 'U') IS NULL
    THROW 51000, 'Required rooms table is missing.', 1;

IF COL_LENGTH('dbo.rooms', 'version') IS NULL
BEGIN
    ALTER TABLE dbo.rooms ADD version BIGINT NOT NULL CONSTRAINT DF_rooms_version DEFAULT 0;
END;

-- Normalize legacy nulls and derive missing ownership before adding checks.
UPDATE dbo.rooms SET maintenance_status = 'NONE' WHERE maintenance_status IS NULL;
UPDATE dbo.rooms SET housekeeping_status = 'CLEAN' WHERE housekeeping_status IS NULL;
UPDATE dbo.rooms SET maintenance_status = 'MAINTENANCE'
WHERE status = 'MAINTENANCE' AND maintenance_status = 'NONE';
UPDATE dbo.rooms SET maintenance_status = 'OUT_OF_SERVICE'
WHERE status = 'OUT_OF_SERVICE' AND maintenance_status = 'NONE';
UPDATE dbo.rooms SET housekeeping_status = 'DIRTY'
WHERE status = 'DIRTY' AND maintenance_status = 'NONE';
UPDATE dbo.rooms SET housekeeping_status = 'CLEANING'
WHERE status = 'CLEANING' AND maintenance_status = 'NONE';

IF EXISTS (
    SELECT 1 FROM dbo.rooms
    WHERE status NOT IN ('AVAILABLE','RESERVED','OCCUPIED','DIRTY','CLEANING','MAINTENANCE','OUT_OF_SERVICE')
       OR maintenance_status NOT IN ('NONE','MAINTENANCE','OUT_OF_SERVICE')
       OR housekeeping_status NOT IN ('CLEAN','DIRTY','CLEANING','INSPECTED')
)
    THROW 51001, 'Rooms contain unsupported authoritative state values.', 1;

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_rooms_status_domain')
BEGIN
    ALTER TABLE dbo.rooms ADD CONSTRAINT CK_rooms_status_domain CHECK (
        status IN ('AVAILABLE','RESERVED','OCCUPIED','DIRTY','CLEANING','MAINTENANCE','OUT_OF_SERVICE')
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_rooms_housekeeping_domain')
BEGIN
    ALTER TABLE dbo.rooms ADD CONSTRAINT CK_rooms_housekeeping_domain CHECK (
        housekeeping_status IN ('CLEAN','DIRTY','CLEANING','INSPECTED')
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_rooms_maintenance_domain')
BEGIN
    ALTER TABLE dbo.rooms ADD CONSTRAINT CK_rooms_maintenance_domain CHECK (
        maintenance_status IN ('NONE','MAINTENANCE','OUT_OF_SERVICE')
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_rooms_maintenance_state')
BEGIN
    ALTER TABLE dbo.rooms ADD CONSTRAINT CK_rooms_maintenance_state CHECK (
        (maintenance_status = 'OUT_OF_SERVICE' AND status = 'OUT_OF_SERVICE') OR
        (maintenance_status = 'MAINTENANCE' AND status = 'MAINTENANCE') OR
        maintenance_status = 'NONE'
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_rooms_housekeeping_state')
BEGIN
    ALTER TABLE dbo.rooms ADD CONSTRAINT CK_rooms_housekeeping_state CHECK (
        maintenance_status <> 'NONE' OR
        (housekeeping_status = 'DIRTY' AND status = 'DIRTY') OR
        (housekeeping_status = 'CLEANING' AND status = 'CLEANING') OR
        (housekeeping_status IN ('CLEAN','INSPECTED') AND status IN ('AVAILABLE','RESERVED','OCCUPIED'))
    );
END;

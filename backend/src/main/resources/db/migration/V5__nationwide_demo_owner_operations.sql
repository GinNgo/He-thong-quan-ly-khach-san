SET XACT_ABORT ON;

ALTER TABLE hotels ALTER COLUMN address NVARCHAR(1000) NOT NULL;
IF COL_LENGTH('hotels', 'is_demo') IS NULL
    ALTER TABLE hotels ADD is_demo BIT NOT NULL CONSTRAINT DF_hotels_is_demo DEFAULT 0;
IF COL_LENGTH('hotels', 'data_source') IS NULL
    ALTER TABLE hotels ADD data_source NVARCHAR(50) NULL;
IF COL_LENGTH('hotels', 'seed_key') IS NULL
    ALTER TABLE hotels ADD seed_key NVARCHAR(255) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('hotels') AND name = 'UX_hotels_demo_seed_key')
    CREATE UNIQUE INDEX UX_hotels_demo_seed_key ON hotels(seed_key) WHERE seed_key IS NOT NULL;

ALTER TABLE property_images ALTER COLUMN image_url NVARCHAR(1000) NOT NULL;
IF COL_LENGTH('property_images', 'alt_text_vi') IS NULL
    ALTER TABLE property_images ADD alt_text_vi NVARCHAR(255) NULL;
IF COL_LENGTH('property_images', 'alt_text_en') IS NULL
    ALTER TABLE property_images ADD alt_text_en NVARCHAR(255) NULL;
IF COL_LENGTH('property_images', 'sort_order') IS NULL
    ALTER TABLE property_images ADD sort_order INT NOT NULL CONSTRAINT DF_property_images_sort_order DEFAULT 0;
IF COL_LENGTH('property_images', 'is_demo') IS NULL
    ALTER TABLE property_images ADD is_demo BIT NOT NULL CONSTRAINT DF_property_images_is_demo DEFAULT 0;
IF COL_LENGTH('property_images', 'created_at') IS NULL ALTER TABLE property_images ADD created_at DATETIME2 NULL;
IF COL_LENGTH('property_images', 'updated_at') IS NULL ALTER TABLE property_images ADD updated_at DATETIME2 NULL;
IF COL_LENGTH('property_images', 'created_by') IS NULL ALTER TABLE property_images ADD created_by VARCHAR(255) NULL;
IF COL_LENGTH('property_images', 'updated_by') IS NULL ALTER TABLE property_images ADD updated_by VARCHAR(255) NULL;
GO

IF COL_LENGTH('room_types', 'normalized_name') IS NULL
    ALTER TABLE room_types ADD normalized_name NVARCHAR(255) NULL;
IF COL_LENGTH('room_types', 'area') IS NULL
    ALTER TABLE room_types ADD area NUMERIC(10,2) NULL;
IF COL_LENGTH('room_types', 'is_demo') IS NULL
    ALTER TABLE room_types ADD is_demo BIT NOT NULL CONSTRAINT DF_room_types_is_demo DEFAULT 0;
GO

IF OBJECT_ID('room_type_images', 'U') IS NULL
BEGIN
    CREATE TABLE room_type_images (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        room_type_id BIGINT NOT NULL,
        image_url NVARCHAR(1000) NOT NULL,
        is_primary BIT NOT NULL CONSTRAINT DF_room_type_images_primary DEFAULT 0,
        sort_order INT NOT NULL CONSTRAINT DF_room_type_images_sort_order DEFAULT 0,
        alt_text_vi NVARCHAR(255) NULL,
        alt_text_en NVARCHAR(255) NULL,
        is_demo BIT NOT NULL CONSTRAINT DF_room_type_images_is_demo DEFAULT 0,
        created_at DATETIME2 NULL,
        updated_at DATETIME2 NULL,
        created_by VARCHAR(255) NULL,
        updated_by VARCHAR(255) NULL,
        CONSTRAINT FK_room_type_images_room_types FOREIGN KEY (room_type_id) REFERENCES room_types(id)
    );
END;

IF COL_LENGTH('rooms', 'housekeeping_status') IS NULL
    ALTER TABLE rooms ADD housekeeping_status VARCHAR(50) NOT NULL CONSTRAINT DF_rooms_housekeeping_status DEFAULT 'CLEAN';
IF COL_LENGTH('rooms', 'is_demo') IS NULL
    ALTER TABLE rooms ADD is_demo BIT NOT NULL CONSTRAINT DF_rooms_is_demo DEFAULT 0;
GO

IF COL_LENGTH('reservation_services', 'added_by_user_id') IS NULL
    ALTER TABLE reservation_services ADD added_by_user_id BIGINT NULL;
IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys
    WHERE parent_object_id = OBJECT_ID('reservation_services') AND name = 'FK_reservation_services_added_by'
)
    ALTER TABLE reservation_services ADD CONSTRAINT FK_reservation_services_added_by
        FOREIGN KEY (added_by_user_id) REFERENCES users(id);
GO

IF OBJECT_ID('housekeeping_tasks', 'U') IS NULL
BEGIN
    CREATE TABLE housekeeping_tasks (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        hotel_id BIGINT NOT NULL,
        room_id BIGINT NOT NULL,
        reservation_id BIGINT NULL,
        status VARCHAR(50) NOT NULL CONSTRAINT DF_housekeeping_tasks_status DEFAULT 'PENDING',
        assigned_to_user_id BIGINT NULL,
        assigned_at DATETIME2 NULL,
        completed_at DATETIME2 NULL,
        note NVARCHAR(1000) NULL,
        created_at DATETIME2 NULL,
        updated_at DATETIME2 NULL,
        created_by VARCHAR(255) NULL,
        updated_by VARCHAR(255) NULL,
        CONSTRAINT FK_housekeeping_tasks_hotels FOREIGN KEY (hotel_id) REFERENCES hotels(id),
        CONSTRAINT FK_housekeeping_tasks_rooms FOREIGN KEY (room_id) REFERENCES rooms(id),
        CONSTRAINT FK_housekeeping_tasks_reservations FOREIGN KEY (reservation_id) REFERENCES reservations(id),
        CONSTRAINT FK_housekeeping_tasks_users FOREIGN KEY (assigned_to_user_id) REFERENCES users(id)
    );
END;
GO

IF OBJECT_ID('demo_seed_progress', 'U') IS NULL
BEGIN
    CREATE TABLE demo_seed_progress (
        seed_key NVARCHAR(255) NOT NULL PRIMARY KEY,
        coverage_mode VARCHAR(30) NOT NULL,
        location_id BIGINT NULL,
        status VARCHAR(30) NOT NULL,
        attempt_count INT NOT NULL CONSTRAINT DF_demo_seed_progress_attempt DEFAULT 0,
        error_message NVARCHAR(1000) NULL,
        started_at DATETIME2 NULL,
        completed_at DATETIME2 NULL,
        updated_at DATETIME2 NOT NULL CONSTRAINT DF_demo_seed_progress_updated DEFAULT SYSDATETIME(),
        CONSTRAINT FK_demo_seed_progress_locations FOREIGN KEY (location_id) REFERENCES locations(id)
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('property_images') AND name = 'IX_property_images_hotel_sort')
    CREATE INDEX IX_property_images_hotel_sort ON property_images(hotel_id, is_primary, sort_order);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('room_type_images') AND name = 'IX_room_type_images_type_sort')
    CREATE INDEX IX_room_type_images_type_sort ON room_type_images(room_type_id, is_primary, sort_order);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('housekeeping_tasks') AND name = 'IX_housekeeping_tasks_hotel_status')
    CREATE INDEX IX_housekeeping_tasks_hotel_status ON housekeeping_tasks(hotel_id, status, room_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('rooms') AND name = 'IX_rooms_inventory_status')
    CREATE INDEX IX_rooms_inventory_status ON rooms(hotel_id, room_type_id, status, maintenance_status, housekeeping_status);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('user_properties') AND name = 'IX_user_properties_user_status')
    CREATE INDEX IX_user_properties_user_status ON user_properties(user_id, status, hotel_id);

UPDATE room_types
SET normalized_name = LOWER(LTRIM(RTRIM(name_vi)))
WHERE normalized_name IS NULL;

SET XACT_ABORT ON;

ALTER TABLE locations ALTER COLUMN name_vi NVARCHAR(255) NOT NULL;
ALTER TABLE locations ALTER COLUMN name_en NVARCHAR(255) NULL;
ALTER TABLE locations ALTER COLUMN normalized_name NVARCHAR(255) NULL;
ALTER TABLE locations ALTER COLUMN full_path NVARCHAR(1000) NULL;
ALTER TABLE locations ALTER COLUMN legacy_parent_name NVARCHAR(255) NULL;

ALTER TABLE hotels ALTER COLUMN name NVARCHAR(255) NOT NULL;
ALTER TABLE hotels ALTER COLUMN name_vi NVARCHAR(255) NULL;
ALTER TABLE hotels ALTER COLUMN name_en NVARCHAR(255) NULL;
ALTER TABLE hotels ALTER COLUMN address NVARCHAR(1000) NOT NULL;
ALTER TABLE hotels ALTER COLUMN city NVARCHAR(255) NOT NULL;
ALTER TABLE hotels ALTER COLUMN country NVARCHAR(255) NOT NULL;
ALTER TABLE hotels ALTER COLUMN description NVARCHAR(MAX) NULL;
ALTER TABLE hotels ALTER COLUMN description_vi NVARCHAR(MAX) NULL;
ALTER TABLE hotels ALTER COLUMN description_en NVARCHAR(MAX) NULL;
IF COL_LENGTH('hotels', 'normalized_name') IS NULL
    ALTER TABLE hotels ADD normalized_name NVARCHAR(255) NULL;
IF COL_LENGTH('hotels', 'normalized_address') IS NULL
    ALTER TABLE hotels ADD normalized_address NVARCHAR(1000) NULL;

ALTER TABLE room_types ALTER COLUMN name_vi NVARCHAR(255) NOT NULL;
ALTER TABLE room_types ALTER COLUMN name_en NVARCHAR(255) NOT NULL;
ALTER TABLE room_types ALTER COLUMN description_vi NVARCHAR(MAX) NULL;
ALTER TABLE room_types ALTER COLUMN description_en NVARCHAR(MAX) NULL;
IF COL_LENGTH('room_types', 'bed_type') IS NULL ALTER TABLE room_types ADD bed_type VARCHAR(50) NULL;
IF COL_LENGTH('room_types', 'bed_count') IS NULL ALTER TABLE room_types ADD bed_count INT NULL;
IF COL_LENGTH('room_types', 'max_adults') IS NULL ALTER TABLE room_types ADD max_adults INT NULL;
IF COL_LENGTH('room_types', 'max_children') IS NULL ALTER TABLE room_types ADD max_children INT NULL;
IF COL_LENGTH('room_types', 'max_guests') IS NULL ALTER TABLE room_types ADD max_guests INT NULL;
IF COL_LENGTH('room_types', 'hourly_price') IS NULL ALTER TABLE room_types ADD hourly_price NUMERIC(19,2) NULL;
IF COL_LENGTH('room_types', 'status') IS NULL ALTER TABLE room_types ADD status VARCHAR(50) NOT NULL CONSTRAINT DF_room_types_status DEFAULT 'ACTIVE';
GO
UPDATE room_types SET max_guests = COALESCE(max_guests, max_guest), max_adults = COALESCE(max_adults, max_guest), max_children = COALESCE(max_children, 0);

DECLARE @roomUniqueBeforeAlter sysname;
SELECT TOP 1 @roomUniqueBeforeAlter = kc.name FROM sys.key_constraints kc
JOIN sys.index_columns ic ON ic.object_id=kc.parent_object_id AND ic.index_id=kc.unique_index_id
JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
WHERE kc.parent_object_id=OBJECT_ID('rooms') AND kc.type='UQ' AND c.name='room_number';
IF @roomUniqueBeforeAlter IS NOT NULL EXEC('ALTER TABLE rooms DROP CONSTRAINT [' + @roomUniqueBeforeAlter + ']');

ALTER TABLE rooms ALTER COLUMN room_number NVARCHAR(50) NOT NULL;
ALTER TABLE rooms ALTER COLUMN description_vi NVARCHAR(MAX) NULL;
ALTER TABLE rooms ALTER COLUMN description_en NVARCHAR(MAX) NULL;
IF COL_LENGTH('rooms', 'hotel_id') IS NULL ALTER TABLE rooms ADD hotel_id BIGINT NULL;
IF COL_LENGTH('rooms', 'maintenance_status') IS NULL ALTER TABLE rooms ADD maintenance_status VARCHAR(50) NOT NULL CONSTRAINT DF_rooms_maintenance DEFAULT 'NONE';
IF COL_LENGTH('rooms', 'max_guests') IS NULL ALTER TABLE rooms ADD max_guests INT NULL;
GO
UPDATE r SET hotel_id = rt.hotel_id FROM rooms r JOIN room_types rt ON rt.id = r.room_type_id WHERE r.hotel_id IS NULL;
IF NOT EXISTS (SELECT 1 FROM rooms WHERE hotel_id IS NULL) ALTER TABLE rooms ALTER COLUMN hotel_id BIGINT NOT NULL;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('rooms') AND name='UX_rooms_hotel_room_number')
    CREATE UNIQUE INDEX UX_rooms_hotel_room_number ON rooms(hotel_id, room_number);
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id WHERE fk.parent_object_id=OBJECT_ID('rooms') AND COL_NAME(fkc.parent_object_id,fkc.parent_column_id)='hotel_id')
    ALTER TABLE rooms ADD CONSTRAINT FK_rooms_hotels FOREIGN KEY (hotel_id) REFERENCES hotels(id);

ALTER TABLE services ALTER COLUMN name_vi NVARCHAR(255) NOT NULL;
ALTER TABLE services ALTER COLUMN name_en NVARCHAR(255) NOT NULL;
ALTER TABLE services ALTER COLUMN description_vi NVARCHAR(MAX) NULL;
ALTER TABLE services ALTER COLUMN description_en NVARCHAR(MAX) NULL;
IF COL_LENGTH('services', 'hotel_id') IS NULL ALTER TABLE services ADD hotel_id BIGINT NULL;
IF COL_LENGTH('services', 'is_system') IS NULL ALTER TABLE services ADD is_system BIT NOT NULL CONSTRAINT DF_services_is_system DEFAULT 0;
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id WHERE fk.parent_object_id=OBJECT_ID('services') AND COL_NAME(fkc.parent_object_id,fkc.parent_column_id)='hotel_id')
    ALTER TABLE services ADD CONSTRAINT FK_services_hotels FOREIGN KEY (hotel_id) REFERENCES hotels(id);

ALTER TABLE reservations ALTER COLUMN special_requests NVARCHAR(MAX) NULL;
IF COL_LENGTH('reservation_details', 'room_type_id') IS NULL ALTER TABLE reservation_details ADD room_type_id BIGINT NULL;
IF COL_LENGTH('reservation_details', 'quantity') IS NULL ALTER TABLE reservation_details ADD quantity INT NOT NULL CONSTRAINT DF_reservation_details_quantity DEFAULT 1;
IF COL_LENGTH('reservation_details', 'adults') IS NULL ALTER TABLE reservation_details ADD adults INT NULL;
IF COL_LENGTH('reservation_details', 'children') IS NULL ALTER TABLE reservation_details ADD children INT NULL;
IF COL_LENGTH('reservation_details', 'unit_price') IS NULL ALTER TABLE reservation_details ADD unit_price NUMERIC(19,2) NULL;
IF COL_LENGTH('reservation_details', 'subtotal') IS NULL ALTER TABLE reservation_details ADD subtotal NUMERIC(19,2) NULL;
GO
UPDATE rd SET room_type_id=r.room_type_id, unit_price=rd.price, subtotal=rd.price*rd.quantity
FROM reservation_details rd JOIN rooms r ON r.id=rd.room_id WHERE rd.room_type_id IS NULL;
ALTER TABLE reservations ALTER COLUMN room_id BIGINT NULL;
ALTER TABLE reservation_details ALTER COLUMN room_id BIGINT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id WHERE fk.parent_object_id=OBJECT_ID('reservation_details') AND COL_NAME(fkc.parent_object_id,fkc.parent_column_id)='room_type_id')
    ALTER TABLE reservation_details ADD CONSTRAINT FK_reservation_details_room_types FOREIGN KEY (room_type_id) REFERENCES room_types(id);

IF OBJECT_ID('reservation_rooms','U') IS NULL
BEGIN
    CREATE TABLE reservation_rooms (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        reservation_detail_id BIGINT NOT NULL,
        room_id BIGINT NOT NULL,
        assigned_at DATETIME2 NULL,
        released_at DATETIME2 NULL,
        status VARCHAR(50) NOT NULL CONSTRAINT DF_reservation_rooms_status DEFAULT 'ASSIGNED',
        created_at DATETIME2 NULL,
        updated_at DATETIME2 NULL,
        created_by VARCHAR(255) NULL,
        updated_by VARCHAR(255) NULL,
        CONSTRAINT FK_reservation_rooms_detail FOREIGN KEY (reservation_detail_id) REFERENCES reservation_details(id),
        CONSTRAINT FK_reservation_rooms_room FOREIGN KEY (room_id) REFERENCES rooms(id)
    );
END;
GO

INSERT INTO reservation_rooms(reservation_detail_id,room_id,assigned_at,status,created_at)
SELECT rd.id,rd.room_id,r.created_at,'ASSIGNED',COALESCE(rd.created_at,SYSDATETIME())
FROM reservation_details rd JOIN reservations r ON r.id=rd.reservation_id
WHERE rd.room_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM reservation_rooms rr WHERE rr.reservation_detail_id=rd.id AND rr.room_id=rd.room_id);

IF COL_LENGTH('reservation_services', 'used_at') IS NULL ALTER TABLE reservation_services ADD used_at DATETIME2 NULL;
IF COL_LENGTH('reservation_services', 'status') IS NULL ALTER TABLE reservation_services ADD status VARCHAR(50) NOT NULL CONSTRAINT DF_reservation_services_status DEFAULT 'ACTIVE';

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('locations') AND name='IX_locations_type_parent_status')
    CREATE INDEX IX_locations_type_parent_status ON locations(location_type,parent_id,status);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('locations') AND name='IX_locations_normalized_name')
    CREATE INDEX IX_locations_normalized_name ON locations(normalized_name);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('hotels') AND name='IX_hotels_location_status')
    CREATE INDEX IX_hotels_location_status ON hotels(province_id,ward_id,approval_status,operation_status);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('hotels') AND name='IX_hotels_normalized_name')
    CREATE INDEX IX_hotels_normalized_name ON hotels(normalized_name);

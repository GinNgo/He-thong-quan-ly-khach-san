IF OBJECT_ID('dbo.rooms', 'U') IS NOT NULL
BEGIN
    IF EXISTS (
        SELECT hotel_id, UPPER(LTRIM(RTRIM(room_number))) normalized_number
        FROM dbo.rooms
        GROUP BY hotel_id, UPPER(LTRIM(RTRIM(room_number)))
        HAVING COUNT(*) > 1
    )
        THROW 51085, 'Duplicate normalized room numbers must be resolved before V85.', 1;

    UPDATE dbo.rooms SET room_number = UPPER(LTRIM(RTRIM(room_number)));

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.rooms') AND name = 'UX_rooms_hotel_room_number')
        CREATE UNIQUE INDEX UX_rooms_hotel_room_number ON dbo.rooms(hotel_id, room_number);

    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_rooms_floor_v85')
        ALTER TABLE dbo.rooms ADD CONSTRAINT CK_rooms_floor_v85 CHECK (floor BETWEEN -10 AND 500);

    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_rooms_number_trimmed_v85')
        ALTER TABLE dbo.rooms ADD CONSTRAINT CK_rooms_number_trimmed_v85
            CHECK (room_number = UPPER(LTRIM(RTRIM(room_number))) AND LEN(room_number) BETWEEN 1 AND 50);
END;

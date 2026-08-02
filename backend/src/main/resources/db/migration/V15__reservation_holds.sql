IF OBJECT_ID('dbo.reservation_holds', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.reservation_holds (
        id BIGINT IDENTITY(1,1) NOT NULL,
        reservation_id BIGINT NOT NULL,
        room_type_id BIGINT NOT NULL,
        hotel_id BIGINT NOT NULL,
        quantity INT NOT NULL,
        hold_key VARCHAR(120) NOT NULL,
        status VARCHAR(30) NOT NULL,
        expires_at DATETIME2 NOT NULL,
        released_at DATETIME2 NULL,
        created_at DATETIME2 NULL,
        updated_at DATETIME2 NULL,
        created_by NVARCHAR(255) NULL,
        updated_by NVARCHAR(255) NULL,
        CONSTRAINT PK_reservation_holds PRIMARY KEY (id),
        CONSTRAINT UQ_reservation_holds_hold_key UNIQUE (hold_key),
        CONSTRAINT CK_reservation_holds_quantity CHECK (quantity > 0),
        CONSTRAINT CK_reservation_holds_status CHECK (status IN ('ACTIVE','CONSUMED','RELEASED','EXPIRED')),
        CONSTRAINT FK_reservation_holds_reservation FOREIGN KEY (reservation_id) REFERENCES dbo.reservations(id),
        CONSTRAINT FK_reservation_holds_room_type FOREIGN KEY (room_type_id) REFERENCES dbo.room_types(id),
        CONSTRAINT FK_reservation_holds_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id)
    );
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.reservation_holds')
      AND name = 'IX_reservation_holds_hotel_status'
)
BEGIN
    CREATE INDEX IX_reservation_holds_hotel_status
        ON dbo.reservation_holds(hotel_id, status);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.reservation_holds')
      AND name = 'IX_reservation_holds_expiry'
)
BEGIN
    CREATE INDEX IX_reservation_holds_expiry
        ON dbo.reservation_holds(status, expires_at);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.reservation_holds')
      AND name = 'UX_reservation_holds_active_reservation'
)
BEGIN
    CREATE UNIQUE INDEX UX_reservation_holds_active_reservation
        ON dbo.reservation_holds(reservation_id)
        WHERE status = 'ACTIVE';
END;

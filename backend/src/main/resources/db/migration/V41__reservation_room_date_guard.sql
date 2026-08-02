IF COL_LENGTH('dbo.reservation_rooms', 'stay_start_date') IS NULL
BEGIN
    ALTER TABLE dbo.reservation_rooms ADD stay_start_date DATE NULL;
END;

IF COL_LENGTH('dbo.reservation_rooms', 'stay_end_date') IS NULL
BEGIN
    ALTER TABLE dbo.reservation_rooms ADD stay_end_date DATE NULL;
END;

UPDATE rr
SET stay_start_date = reservation.check_in_date,
    stay_end_date = reservation.check_out_date
FROM dbo.reservation_rooms rr
JOIN dbo.reservation_details detail ON detail.id = rr.reservation_detail_id
JOIN dbo.reservations reservation ON reservation.id = detail.reservation_id
WHERE rr.stay_start_date IS NULL
   OR rr.stay_end_date IS NULL;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.reservation_rooms')
      AND name = 'IX_reservation_rooms_room_dates'
)
BEGIN
    CREATE INDEX IX_reservation_rooms_room_dates
        ON dbo.reservation_rooms(room_id, stay_start_date, stay_end_date, status);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.reservation_rooms')
      AND name = 'UX_reservation_rooms_active_date_guard'
)
BEGIN
    CREATE UNIQUE INDEX UX_reservation_rooms_active_date_guard
        ON dbo.reservation_rooms(room_id, stay_start_date, stay_end_date)
        WHERE status = 'ASSIGNED'
          AND stay_start_date IS NOT NULL
          AND stay_end_date IS NOT NULL;
END;

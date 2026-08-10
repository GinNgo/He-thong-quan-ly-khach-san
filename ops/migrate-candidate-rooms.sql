SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;

INSERT INTO HotelDB.dbo.rooms
    (created_at, created_by, updated_at, updated_by, description_en, description_vi,
     floor, housekeeping_status, is_demo, maintenance_status, max_guests, note,
     room_number, status, version, hotel_id, room_type_id)
SELECT r.created_at, r.created_by, r.updated_at, r.updated_by, r.description_en, r.description_vi,
       r.floor, r.housekeeping_status, r.is_demo, r.maintenance_status, r.max_guests, r.note,
       r.room_number, r.status, 0, r.hotel_id, target_type.id
FROM HotelDBCandidate.dbo.rooms r
JOIN HotelDBCandidate.dbo.room_types source_type ON source_type.id = r.room_type_id
JOIN HotelDB.dbo.room_types target_type
  ON target_type.hotel_id = source_type.hotel_id
 AND target_type.code = source_type.code
WHERE NOT EXISTS (
    SELECT 1 FROM HotelDB.dbo.rooms existing
    WHERE existing.hotel_id = r.hotel_id
      AND existing.room_number = r.room_number
);

SELECT COUNT_BIG(*) AS rooms_total FROM HotelDB.dbo.rooms;

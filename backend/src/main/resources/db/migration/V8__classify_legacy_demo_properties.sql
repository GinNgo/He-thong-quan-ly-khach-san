/* Classify only the legacy records that have both a DEMO code and demo.local email. */

UPDATE hotels
SET is_demo = 1,
    data_source = N'DEMO',
    seed_key = COALESCE(seed_key, CONCAT(N'LEGACY-', code)),
    external_provider = NULL,
    average_rating = NULL,
    review_count = 0
WHERE code LIKE 'DEMO-%' AND email LIKE '%@demo.local';

UPDATE rt
SET is_demo = 1
FROM room_types rt
JOIN hotels h ON h.id = rt.hotel_id
WHERE h.is_demo = 1 AND h.seed_key LIKE 'LEGACY-%';

UPDATE r
SET is_demo = 1
FROM rooms r
JOIN hotels h ON h.id = r.hotel_id
WHERE h.is_demo = 1 AND h.seed_key LIKE 'LEGACY-%';

UPDATE h
SET main_image = CASE ABS(CONVERT(BIGINT, CHECKSUM(h.seed_key, h.id))) % 12
    WHEN 0 THEN '/assets/properties/hotel-city-01.webp'
    WHEN 1 THEN '/assets/properties/hotel-city-02.webp'
    WHEN 2 THEN '/assets/properties/motel-01.webp'
    WHEN 3 THEN '/assets/properties/homestay-01.webp'
    WHEN 4 THEN '/assets/properties/hostel-01.webp'
    WHEN 5 THEN '/assets/properties/apartment-01.webp'
    WHEN 6 THEN '/assets/properties/villa-01.webp'
    WHEN 7 THEN '/assets/properties/resort-01.webp'
    WHEN 8 THEN '/assets/properties/guest-house-01.webp'
    WHEN 9 THEN '/assets/properties/hotel-beach-01.webp'
    WHEN 10 THEN '/assets/properties/hotel-room-01.webp'
    ELSE '/assets/properties/hotel-room-02.webp'
END
FROM hotels h
WHERE h.is_demo = 1 AND h.seed_key LIKE 'LEGACY-%';

;WITH image_slots AS (
    SELECT 0 sort_order UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
)
INSERT INTO property_images (image_url, is_primary, hotel_id, alt_text_vi, alt_text_en,
                             sort_order, is_demo, created_at, updated_at)
SELECT CASE (ABS(CONVERT(BIGINT, CHECKSUM(h.seed_key, h.id))) + s.sort_order) % 12
        WHEN 0 THEN '/assets/properties/hotel-city-01.webp'
        WHEN 1 THEN '/assets/properties/hotel-city-02.webp'
        WHEN 2 THEN '/assets/properties/motel-01.webp'
        WHEN 3 THEN '/assets/properties/homestay-01.webp'
        WHEN 4 THEN '/assets/properties/hostel-01.webp'
        WHEN 5 THEN '/assets/properties/apartment-01.webp'
        WHEN 6 THEN '/assets/properties/villa-01.webp'
        WHEN 7 THEN '/assets/properties/resort-01.webp'
        WHEN 8 THEN '/assets/properties/guest-house-01.webp'
        WHEN 9 THEN '/assets/properties/hotel-beach-01.webp'
        WHEN 10 THEN '/assets/properties/hotel-room-01.webp'
        ELSE '/assets/properties/hotel-room-02.webp'
    END,
    CASE WHEN s.sort_order = 0 THEN 1 ELSE 0 END,
    h.id,
    CONCAT(N'Ảnh ', s.sort_order + 1, N' của ', COALESCE(h.name_vi, h.name)),
    CONCAT('Image ', s.sort_order + 1, ' of ', COALESCE(h.name_en, h.name)),
    s.sort_order, 1, SYSUTCDATETIME(), SYSUTCDATETIME()
FROM hotels h
CROSS JOIN image_slots s
WHERE h.is_demo = 1 AND h.seed_key LIKE 'LEGACY-%'
  AND NOT EXISTS (SELECT 1 FROM property_images pi WHERE pi.hotel_id = h.id AND pi.sort_order = s.sort_order);

;WITH image_slots AS (SELECT 0 sort_order UNION ALL SELECT 1)
INSERT INTO room_type_images (room_type_id, image_url, is_primary, sort_order,
                              alt_text_vi, alt_text_en, is_demo, created_at, updated_at)
SELECT rt.id,
    CASE
        WHEN rt.code = 'SINGLE' THEN '/assets/room-types/single-room-01.webp'
        WHEN rt.code = 'DOUBLE' AND s.sort_order = 0 THEN '/assets/room-types/double-room-01.webp'
        WHEN rt.code = 'DOUBLE' THEN '/assets/room-types/double-room-02.webp'
        WHEN rt.code = 'TWIN' THEN '/assets/room-types/twin-room-01.webp'
        WHEN rt.code = 'FAMILY' THEN '/assets/room-types/family-room-01.webp'
        WHEN rt.code = 'SUITE' AND s.sort_order = 0 THEN '/assets/room-types/suite-room-01.webp'
        ELSE '/assets/room-types/suite-room-02.webp'
    END,
    CASE WHEN s.sort_order = 0 THEN 1 ELSE 0 END,
    s.sort_order,
    CONCAT(N'Ảnh ', s.sort_order + 1, N' của ', rt.name_vi),
    CONCAT('Image ', s.sort_order + 1, ' of ', COALESCE(rt.name_en, rt.name_vi)),
    1, SYSUTCDATETIME(), SYSUTCDATETIME()
FROM room_types rt
JOIN hotels h ON h.id = rt.hotel_id
CROSS JOIN image_slots s
WHERE h.is_demo = 1 AND h.seed_key LIKE 'LEGACY-%'
  AND NOT EXISTS (SELECT 1 FROM room_type_images rti WHERE rti.room_type_id = rt.id AND rti.sort_order = s.sort_order);

UPDATE rt
SET base_price = CASE rt.code
    WHEN 'SINGLE' THEN 350000 + (h.id % 5) * 50000
    WHEN 'DOUBLE' THEN 500000 + (h.id % 6) * 75000
    WHEN 'TWIN' THEN 550000 + (h.id % 6) * 80000
    WHEN 'FAMILY' THEN 850000 + (h.id % 6) * 125000
    WHEN 'SUITE' THEN 1200000 + (h.id % 7) * 250000
    ELSE 450000 + (h.id % 8) * 70000
END
FROM room_types rt
JOIN hotels h ON h.id = rt.hotel_id
WHERE h.is_demo = 1 AND h.seed_key LIKE 'LEGACY-%';

UPDATE h
SET min_price = p.min_price, max_price = p.max_price
FROM hotels h
CROSS APPLY (
    SELECT MIN(rt.base_price) min_price, MAX(rt.base_price) max_price
    FROM room_types rt WHERE rt.hotel_id = h.id AND rt.status = 'ACTIVE'
) p
WHERE h.is_demo = 1 AND h.seed_key LIKE 'LEGACY-%';

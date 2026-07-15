/* Repair demo-only media and pricing. User-uploaded and real property data are untouched. */

UPDATE hotels
SET main_image = CASE ABS(CONVERT(BIGINT, CHECKSUM(seed_key, id))) % 12
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
average_rating = NULL,
review_count = 0
WHERE is_demo = 1;

UPDATE pi
SET image_url = CASE (ABS(CONVERT(BIGINT, CHECKSUM(h.seed_key, h.id))) + pi.sort_order) % 12
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
alt_text_vi = CONCAT(N'Ảnh ', pi.sort_order + 1, N' của ', COALESCE(h.name_vi, h.name)),
alt_text_en = CONCAT('Image ', pi.sort_order + 1, ' of ', COALESCE(h.name_en, h.name))
FROM property_images pi
JOIN hotels h ON h.id = pi.hotel_id
WHERE pi.is_demo = 1 AND h.is_demo = 1;

UPDATE rti
SET image_url = CASE
    WHEN rt.code = 'SINGLE' THEN '/assets/room-types/single-room-01.webp'
    WHEN rt.code = 'DOUBLE' AND rti.sort_order % 2 = 0 THEN '/assets/room-types/double-room-01.webp'
    WHEN rt.code = 'DOUBLE' THEN '/assets/room-types/double-room-02.webp'
    WHEN rt.code = 'TWIN' THEN '/assets/room-types/twin-room-01.webp'
    WHEN rt.code = 'FAMILY' THEN '/assets/room-types/family-room-01.webp'
    WHEN rt.code = 'SUITE' AND rti.sort_order % 2 = 0 THEN '/assets/room-types/suite-room-01.webp'
    ELSE '/assets/room-types/suite-room-02.webp'
END,
alt_text_vi = CONCAT(N'Ảnh ', rti.sort_order + 1, N' của ', rt.name_vi),
alt_text_en = CONCAT('Image ', rti.sort_order + 1, ' of ', COALESCE(rt.name_en, rt.name_vi))
FROM room_type_images rti
JOIN room_types rt ON rt.id = rti.room_type_id
JOIN hotels h ON h.id = rt.hotel_id
WHERE rti.is_demo = 1 AND h.is_demo = 1;

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
WHERE h.is_demo = 1;

UPDATE h
SET min_price = prices.min_price,
    max_price = prices.max_price
FROM hotels h
CROSS APPLY (
    SELECT MIN(rt.base_price) min_price, MAX(rt.base_price) max_price
    FROM room_types rt
    WHERE rt.hotel_id = h.id AND rt.status = 'ACTIVE'
) prices
WHERE h.is_demo = 1;

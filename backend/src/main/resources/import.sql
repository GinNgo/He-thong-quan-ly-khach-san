-- Seed Provinces
INSERT INTO provinces (id, code, name_en, name_vi) VALUES (1, 'DN', 'Da Nang', 'Đà Nẵng');
INSERT INTO provinces (id, code, name_en, name_vi) VALUES (2, 'HCM', 'Ho Chi Minh', 'Hồ Chí Minh');
INSERT INTO provinces (id, code, name_en, name_vi) VALUES (3, 'HN', 'Ha Noi', 'Hà Nội');

-- Seed Wards
INSERT INTO wards (id, code, name_en, name_vi, province_id) VALUES (1, 'HC', 'Hai Chau', 'Hải Châu', 1);
INSERT INTO wards (id, code, name_en, name_vi, province_id) VALUES (2, 'ST', 'Son Tra', 'Sơn Trà', 1);

-- Seed Hotels
INSERT INTO hotels (id, name, address, description, latitude, longitude, status, property_type, average_rating, review_count, star_rating, province_id, ward_id, main_image)
VALUES (1, 'LuxeStay Da Nang', '123 Bach Dang, Hai Chau, Da Nang', 'A luxury resort.', 16.0668, 108.2230, 'ACTIVE', 'HOTEL', 4.8, 150, 5, 1, 1, 'https://images.unsplash.com/photo-1551882547-ff40c0d129df?w=800&q=80');

INSERT INTO hotels (id, name, address, description, latitude, longitude, status, property_type, average_rating, review_count, star_rating, province_id, ward_id, main_image)
VALUES (2, 'Ocean View Hotel', '45 Vo Nguyen Giap, Son Tra, Da Nang', 'Beautiful ocean view.', 16.0680, 108.2430, 'ACTIVE', 'RESORT', 4.5, 85, 4, 1, 2, 'https://images.unsplash.com/photo-1571003123894-1f0594d2b5d9?w=800&q=80');

INSERT INTO hotels (id, name, address, description, latitude, longitude, status, property_type, average_rating, review_count, star_rating, province_id, ward_id, main_image)
VALUES (3, 'Hanoi Grand', '10 Hoan Kiem, Ha Noi', 'In the heart of the city.', 21.0285, 105.8542, 'ACTIVE', 'HOTEL', 4.9, 320, 5, 3, NULL, 'https://images.unsplash.com/photo-1564501049412-61c2a3083791?w=800&q=80');

-- Seed Room Types
INSERT INTO room_types (id, hotel_id, name_en, name_vi, base_price, max_guest, description, size_sqm, status)
VALUES (1, 1, 'Deluxe Ocean View', 'Phòng Deluxe Hướng Biển', 1500000, 2, 'Spacious room with ocean view', 45.0, 'ACTIVE');

INSERT INTO room_types (id, hotel_id, name_en, name_vi, base_price, max_guest, description, size_sqm, status)
VALUES (2, 1, 'Presidential Suite', 'Phòng Tổng Thống', 5000000, 4, 'Top tier suite', 120.0, 'ACTIVE');

INSERT INTO room_types (id, hotel_id, name_en, name_vi, base_price, max_guest, description, size_sqm, status)
VALUES (3, 2, 'Standard Room', 'Phòng Tiêu Chuẩn', 800000, 2, 'Cozy and simple', 25.0, 'ACTIVE');

-- Seed Rooms
INSERT INTO rooms (id, room_type_id, hotel_id, room_number, floor, status) VALUES (1, 1, 1, '101', 1, 'AVAILABLE');
INSERT INTO rooms (id, room_type_id, hotel_id, room_number, floor, status) VALUES (2, 1, 1, '102', 1, 'AVAILABLE');
INSERT INTO rooms (id, room_type_id, hotel_id, room_number, floor, status) VALUES (3, 2, 1, '901', 9, 'AVAILABLE');
INSERT INTO rooms (id, room_type_id, hotel_id, room_number, floor, status) VALUES (4, 3, 2, '201', 2, 'AVAILABLE');

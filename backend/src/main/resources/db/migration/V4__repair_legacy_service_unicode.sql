SET XACT_ABORT ON;

UPDATE services
SET name_vi=N'Ăn sáng Buffet', name_en=N'Ăn sáng Buffet',
    description_vi=N'Buffet sáng tiêu chuẩn quốc tế', description_en=N'Buffet sáng tiêu chuẩn quốc tế'
WHERE code='SVC_BREAKFAST';

UPDATE services
SET name_vi=N'Dịch vụ giặt ủi', name_en=N'Dịch vụ giặt ủi',
    description_vi=N'Giặt sấy lấy ngay trong ngày', description_en=N'Giặt sấy lấy ngay trong ngày'
WHERE code='SVC_LAUNDRY';

UPDATE services
SET name_vi=N'Đưa đón sân bay', name_en=N'Đưa đón sân bay',
    description_vi=N'Xe 4 chỗ đưa đón sân bay 2 chiều', description_en=N'Xe 4 chỗ đưa đón sân bay 2 chiều'
WHERE code='SVC_TRANSFER';

# Báo cáo Unicode, tìm kiếm và tồn phòng - 2026-07-15

Trạng thái: **chưa tuyên bố hoàn thành toàn bộ**, vì browser E2E chưa chạy được do cửa sổ trình duyệt trong Codex không attach được tab. Migration, import, SQL validation, API test, backend test và frontend build đã chạy thực tế.

1. **Cột VARCHAR đã phát hiện**
   - `locations`: `name_vi`, `name_en`, `normalized_name`, `full_path`, `legacy_parent_name`.
   - `hotels`: `name_vi`, `name_en`.
   - `room_types`, `rooms`, `services`: các tên/mô tả tiếng Việt còn `VARCHAR/TEXT`.
   - `notifications`, `subscription_plans`, `property_claim_requests`, `property_import_items`: các trường nội dung người dùng còn `VARCHAR/TEXT`.

2. **Cột đã chuyển sang NVARCHAR**
   - Toàn bộ các cột trên đã chuyển sang `NVARCHAR` hoặc `NVARCHAR(MAX)` bằng Flyway V1 và V3.
   - JPA entity khai báo `columnDefinition` Unicode cho các trường tương ứng.

3. **Cách đọc JSON UTF-8**
   - `LocationImportService` mở `InputStream`, xử lý BOM bằng `PushbackInputStream`, sau đó để Jackson đọc byte UTF-8 trực tiếp; không dùng charset mặc định Windows.

4. **Số location trước/sau import**
   - Trước: 6.363.
   - Sau canonical reimport: 6.317 = 34 Province + 6.283 Ward.
   - Backup: `locations_backup_20260715_001` (6.363 dòng), `hotels_backup_20260715_001` (1 dòng).

5. **Bản ghi lỗi encoding đã sửa**
   - Location lỗi `?` trước import: 4.336; sau import: 0.
   - Sửa theo source/code chính xác: 3 subscription plans và 3 dịch vụ legacy; không suy đoán từ chuỗi hỏng.

6. **Province**: 34.

7. **Ward**: 6.283; Ward không có tỉnh cha: 0; District: 0.

8. **Hotel demo**: 10, chỉ tạo khi profile development/demo/test và `app.demo-data.enabled=true`.

9. **RoomType**: 33 tổng, trong đó 30 RoomType demo.

10. **Room**: 69 tổng, trong đó 60 phòng vật lý demo. Số phòng unique trong từng hotel.

11. **Index đã tạo**
    - `IX_locations_type_parent_status`, `IX_locations_normalized_name`.
    - `IX_hotels_location_status`, `IX_hotels_normalized_name`.
    - `UX_locations_type_source_code`, `UK_room_types_hotel_code`, `UX_rooms_hotel_room_number`.

12. **Search có dấu**
    - `Cao Bằng`, `Bình Minh` trả kết quả đúng; JSON UTF-8 giữ nguyên tiếng Việt.

13. **Search không dấu**
    - `cao bang`, `phuc xa`, `binh minh` trả đúng Province/Ward/Property.

14. **Search địa chỉ**
    - `le loi` và `123 le loi` trả `Khách sạn Ánh Dương Mỹ Tho`.

15. **Availability**
    - Integration test: 3 phòng đôi, 1 bảo trì, 1 reservation giao nhau => còn 1.
    - API SQL Server: RoomType DOUBLE từ 3 còn 1 sau booking quantity=2; overbook trả HTTP 409.

16. **Booking**
    - Booking theo RoomType + quantity + adults + children; chưa gán phòng vật lý lúc tạo.
    - API thực tế trả HTTP 201 và `ReservationDTO`; tổng tiền snapshot 3.910.000 VND.

17. **Check-in và gán phòng**
    - Super Admin gán phòng 201, 202 đúng RoomType; check-in HTTP 200.
    - Chặn thiếu số lượng, trùng phòng, sai RoomType và phòng thuộc cơ sở khác.
    - Check-out giải phóng assignment/phòng.

18. **Service và invoice**
    - Thêm 2 dịch vụ x 120.000; tổng reservation tăng đúng lên 4.150.000.
    - Generate invoice HTTP 200, snapshot tổng 4.150.000, trạng thái PAID.

19. **Backend build/test**
    - Flyway đã chạy đến version 4 trên SQL Server.
    - `mvn test`: 30 tests, 0 failure, 0 error, 0 skipped.
    - Owner scope thực tế: manager của hotel khác tạo chéo trả HTTP 403.

20. **Frontend build/E2E**
    - `npm run build`: pass; còn warning bundle budget và CommonJS hiện hữu.
    - Popup dùng API hợp nhất, bỏ hard-code, random count, ảnh remote/fallback giả.
    - Browser E2E: **chưa chạy được** vì in-app browser không attach được tab; localhost:4200 vẫn trả HTTP 200.

21. **Phần chưa hoàn thành**
    - Chưa có browser E2E cho chuỗi Home -> autocomplete -> search -> hotel -> booking -> admin check-in -> service -> checkout.
    - Chưa mở rộng booking nhiều RoomType trong cùng một reservation; phase hiện tại hỗ trợ một RoomType với quantity > 1 theo yêu cầu tối thiểu.
    - UI quản lý gán nhiều phòng/check-in hiện chưa được kiểm chứng bằng trình duyệt trong lần chạy này.

## Xác nhận cuối database

- `locations.name_vi LIKE N'%?%'`: 0.
- `locations.normalized_name IS NULL`: 0.
- Hotel, RoomType, Room, Service, SubscriptionPlan và Notification còn lỗi `?`: 0.
- Test reservation tạo trong quá trình xác minh đã được xóa; dữ liệu demo vẫn được giữ.
- Source Java/Angular/docs: 0 file UTF-8 lỗi, 0 ký tự replacement, 0 dấu hiệu mojibake.

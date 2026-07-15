# Báo cáo phase dữ liệu demo toàn quốc và Owner Portal

Ngày xác minh: 2026-07-15. Database: SQL Server `HotelDB`. Cấu hình đã chạy: `STANDARD`, 5 cơ sở/tỉnh, batch 50, tối đa 5.000.

## Kết quả dữ liệu

1. **Coverage**: 34/34 Province có đúng 5 cơ sở demo, tổng 170 cơ sở. STANDARD chạm 170/6.283 Ward; không tuyên bố bao phủ toàn bộ Ward.
2. **Cơ sở theo Ward**: 170 Ward có một cơ sở demo; các Ward còn lại không được seed trong STANDARD.
3. **Owner**: 170 tài khoản, mỗi cơ sở có một mapping OWNER active.
4. **Subscription**: FREE 29; NO_PLAN 29; STANDARD active 28; BUSINESS 28; LIFETIME 28; STANDARD expired 28.
5. **RoomType**: 645 bản ghi demo, mỗi cơ sở có 3-5 loại.
6. **Room vật lý**: 1.232 bản ghi demo; không trùng `(hotel_id, room_number)`.
7. **Ảnh**: 510 ảnh cơ sở + 1.290 ảnh RoomType = 1.800 ảnh local/demo.
8. **Property type**: HOTEL 25, MOTEL 27, HOMESTAY 10, HOSTEL 23, APARTMENT 32, VILLA 22, RESORT 10, GUEST_HOUSE 21.

## Migration và an toàn dữ liệu

- Backup: `hotels_backup_20260715_demo_phase`, `property_images_backup_20260715_demo_phase`, `room_types_backup_20260715_demo_phase`, `rooms_backup_20260715_demo_phase`, `user_properties_backup_20260715_demo_phase`, `account_subscriptions_backup_20260715_demo_phase`.
- Flyway V5 `nationwide demo owner operations` thành công; schema history success=1.
- Trước phase có 11 cơ sở; sau phase vẫn có đúng 11 cơ sở `is_demo=0` và thêm 170 cơ sở demo.
- Seed chạy lại giữ nguyên count: duplicate seed key=0, duplicate room number theo cơ sở=0, failed progress=0.
- Location chứa ký tự `?` do encoding=0; Province=34, Ward=6.283.

## API và nghiệp vụ đã kiểm tra trực tiếp

9. Search tên/địa điểm có dấu `Phúc Xá`: 3 suggestion, gồm 2 PROPERTY.
10. Search không dấu `phuc xa`: 3 suggestion, gồm 2 PROPERTY.
11. Search tên `Ocean Pearl`: có PROPERTY; search địa chỉ `21 duong vuon xanh`: 1 kết quả.
12. Search Province ID 1: 9 kết quả hợp lệ gồm dữ liệu hiện hữu và demo; search Ward ID 6364: 1 kết quả.
13. Admin `/api/admin/property-owners`: 170 owner; Owner FREE login nhận role `PROPERTY_OWNER`, plan FREE và đúng 1 property.
14. NO_PLAN: `upgradeRequired=true`, fallback `MAX_ROOMS=5`; EXPIRED: status EXPIRED, `upgradeRequired=true`, fallback `MAX_ROOMS=5`.
15. Owner truy cập booking cơ sở khác: HTTP 403. Đặt 3 phòng khi chỉ có 2: HTTP 409.
16. Booking thực tế: quantity=2, available=2, assigned=2, check-in `CHECKED_IN`.
17. Dịch vụ: unit price snapshot 110.000, quantity 2, amount 220.000.
18. Check-out: reservation `CHECKED_OUT`, invoice `PAID`, total 2.750.000, hai phòng chuyển `DIRTY/DIRTY`.
19. Housekeeping: tạo 2 task; hoàn tất đưa cả hai phòng về `AVAILABLE/CLEAN`.

## Build và phần chưa hoàn thành

- Backend `mvnw.cmd test`: 30/30 test pass, 0 failure, 0 error; Maven `BUILD SUCCESS` ngày 2026-07-15.
- Frontend `npm run build`: pass; còn warning bundle budget và CommonJS hiện hữu.
- Browser E2E: chưa chạy được vì in-app browser không attach được tab. API E2E và SQL validation đã chạy thật nhưng không thay thế browser E2E.
- FULL_COVERAGE chưa chạy trên 6.283 Ward; chỉ code path/batch/progress đã triển khai. Không báo bao phủ toàn bộ Ward.
- UI Owner đã có dashboard, RoomType, Room/bulk và billing; UI ảnh, dịch vụ, staff, booking assignment/check-in/service/check-out chưa hoàn thiện đầy đủ theo workflow trình duyệt.
- Admin hiện có màn hình bảng theo dõi; thao tác activate/renew/upgrade/downgrade/revoke có lịch sử chưa hoàn thiện.
- Booking hiện hỗ trợ một RoomType với quantity > 1, chưa hỗ trợ nhiều RoomType trong cùng reservation.

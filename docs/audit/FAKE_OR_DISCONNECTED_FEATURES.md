# Báo cáo chức năng giả hoặc chưa kết nối (Fake/Disconnected Features)

1. **Test Automation Config (Frontend & E2E):**
   - **Tình trạng:** Vitest cấu hình thiếu JIT provider (lỗi Angular 18 decorator). Playwright cấu hình mix versions, `test.describe` crash.
   - **Mức độ:** Blocker cho CI/CD và Regression.

2. **Chức năng đặt nhiều loại phòng (Multiple RoomTypes):**
   - **Tình trạng:** UI có thể cho chọn số lượng theo từng RoomType ở màn Search, nhưng API `ReservationRequestDTO` chỉ nhận 1 `roomTypeId` và 1 `quantity`.
   - **Mức độ:** Lỗi nghiệp vụ High. Sẽ crash hoặc mất data khi khách book giỏ hàng hỗn hợp.

3. **Chức năng Subscription/Quota Limit:**
   - **Tình trạng:** Có UI cho Admin quản lý gói. Có bảng DB. Nhưng Backend thiếu Interceptor/Aspect chặn thao tác khi Owner hết Quota.
   - **Mức độ:** Lỗi bảo mật/nghiệp vụ Medium (Bypass business rules).

4. **Quản lý Review:**
   - **Tình trạng:** Có trong Mockup/Tài liệu nhưng không có Entity trong Backend.
   - **Mức độ:** Feature missing.

5. **Dịch vụ đi kèm (Services) của Customer:**
   - **Tình trạng:** Thiếu UI ở Client site cho phép add service, dù Backend đã có API hỗ trợ Receptionist.
   - **Mức độ:** Feature gap.
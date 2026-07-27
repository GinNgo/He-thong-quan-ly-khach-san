# Báo cáo lỗ hổng Bảo mật & Data integrity

1. **Idempotency Payment**
   - **Tình trạng:** Khách nhấn thanh toán nhiều lần hoặc network lag có thể sinh nhiều Payment record cho cùng 1 Reservation (do chưa force Idempotency key phía Database).
   - **Rủi ro:** Ghi nhận thanh toán lặp, sai lệch doanh thu.

2. **Property Data Isolation (IDOR)**
   - **Tình trạng:** `UserProperty` (chứa mapping Owner -> Property) có thể chưa được check chặt ở cấp độ Repository/Service ở tất cả các entity liên quan (VD: sửa Room, xem Booking).
   - **Rủi ro:** Owner A có thể sửa Room hoặc xem Booking của Owner B nếu đoán được ID. (Cần verify mask trong `PropertySecurity`).

3. **Booking Item Pricing Snapshot**
   - **Tình trạng:** Reservation đang lưu `totalPrice`, cần xác minh xem khi admin đổi giá `RoomType` thì Booking cũ có bị tính lại khi Checkout không.

4. **Secret Exposure**
   - Mật khẩu DB `sa`/`123456`, JWT secret đang nằm trong `application.properties` và `.env` commit thẳng lên Git. Cần `.env.example` và remove secret thật.

5. **API Bypass Role**
   - Các API `GET` có thể bị hở nếu Spring Security config matcher sai thứ tự hoặc thiếu `@PreAuthorize`.
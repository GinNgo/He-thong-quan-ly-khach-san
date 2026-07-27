# Hotel Business Research & Domain Model

Tài liệu này mô hình hóa các nghiệp vụ cốt lõi của hệ thống quản lý khách sạn LuxeStay, đóng vai trò là "nguồn sự thật" về logic nghiệp vụ khi triển khai code.

## 1. Cấu trúc khách sạn (Property Structure)
- **Chuỗi/Hệ thống (Platform):** Quản lý bởi Super Admin. Chứa nhiều Property.
- **Cơ sở lưu trú (Property):** Thuộc sở hữu của Owner. Có thông tin cơ bản (tên, địa chỉ, mô tả, chính sách chung).
- **Loại phòng (RoomType):** Đại diện cho một nhóm phòng giống nhau (VD: Standard Double, Deluxe Twin). Định nghĩa sức chứa (Người lớn, Trẻ em), tiện nghi, diện tích, giá cơ bản.
- **Phòng vật lý (Room):** Các phòng thực tế được đánh số (VD: 101, 102). Thuộc về một RoomType.
- **Inventory (Tồn phòng):** Tổng số phòng vật lý khả dụng của một RoomType trong một ngày cụ thể (trừ đi phòng hỏng/bảo trì).

## 2. Trạng thái phòng (Room Status)
Phòng vật lý có các chiều trạng thái độc lập nhưng liên đới:
- **Trạng thái bán phòng (Inventory Status):** AVAILABLE (Trống), RESERVED (Đã đặt nhưng chưa đến), OCCUPIED (Đang ở), BLOCKED (Khóa không bán).
- **Trạng thái dọn phòng (Housekeeping Status):** CLEAN (Sạch), DIRTY (Bẩn - tự động set sau checkout), INSPECTED (Đã kiểm tra).
- **Trạng thái bảo trì (Maintenance Status):** OPERATIONAL (Hoạt động tốt), OUT_OF_ORDER (Hỏng hóc, không thể cho thuê), MAINTENANCE (Đang bảo trì định kỳ).

*Quy tắc:* Khách chỉ được Check-in vào phòng `AVAILABLE` & `CLEAN` & `OPERATIONAL`.

## 3. Vòng đời Booking (Reservation Lifecycle)
- **PENDING:** Booking vừa tạo, đang chờ thanh toán (giữ phòng mềm).
- **CONFIRMED:** Đã thanh toán hoặc không cần thanh toán trước (giữ phòng cứng).
- **CHECKED_IN:** Khách đã nhận phòng.
- **CHECKED_OUT:** Khách đã trả phòng, chu kỳ lưu trú kết thúc.
- **CANCELLED:** Hủy bởi khách hoặc Admin (trước check-in).
- **NO_SHOW:** Khách không đến sau giờ quy định.
- **EXPIRED:** Quá hạn thanh toán (từ PENDING chuyển sang).

*Quy tắc tồn phòng:* PENDING, CONFIRMED, CHECKED_IN làm giảm Inventory. CANCELLED, NO_SHOW, EXPIRED, CHECKED_OUT hoàn trả hoặc giải phóng Inventory.

## 4. Tồn phòng & Chống Overbooking
- **Transaction & Locking:** Phải dùng Optimistic Locking (versioning) hoặc Pessimistic Locking (Database lock) khi giảm Inventory để tránh 2 người cùng đặt phòng cuối cùng.
- **Idempotency:** Request thanh toán/đặt phòng phải có Idempotency Key để tránh trừ tiền/đặt trùng khi network chập chờn.

## 5. Giá phòng (Pricing)
- **Snapshot Giá:** Giá phòng lúc khách đặt (Base Price) phải được copy vào `reservation_details` (Booking Item). Không được link trực tiếp ID giá, để tránh lịch sử bị đổi khi admin đổi giá RoomType.
- **Extra:** Phí thêm người, giường phụ, thuế, phí dịch vụ.

## 6. Vận hành lưu trú (Operations)
- **Gán phòng (Room Assignment):** Receptionist gán phòng vật lý (Room) cho Booking trước hoặc trong lúc Check-in.
- **Check-in:** Xác minh giấy tờ -> Thu tiền cọc (Deposit) -> Nhận chìa khóa. Chuyển trạng thái Booking -> CHECKED_IN. Trạng thái Room -> OCCUPIED.
- **Check-out:** Thu tiền phát sinh -> Trả cọc -> Trả chìa khóa. Chuyển trạng thái Booking -> CHECKED_OUT. Trạng thái Room -> DIRTY (Cần dọn dẹp).

## 7. Thanh toán & Hóa đơn (Payment & Invoice)
- Một Booking có thể có nhiều Payment (Cọc, Thanh toán đợt 1, Phụ thu lúc về).
- Payment Status: PENDING, SUCCESS, FAILED, REFUNDED.
- Hóa đơn (Invoice): Tạo ra khi hoàn thành nghĩa vụ tài chính hoặc khi Check-out. Invoice bất biến sau khi xuất.

## 8. Phân quyền (RBAC/ABAC)
- **Super Admin:** Quản lý toàn hệ thống, quản lý Owner, Subscriptions.
- **Owner:** Quản lý các Property của mình (Data isolation dựa trên `property_id`).
- **Receptionist/Manager:** Thao tác trên Property được phân công.
- **Customer:** Chỉ xem và quản lý Booking của chính mình.
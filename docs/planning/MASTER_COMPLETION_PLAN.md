# Kế hoạch Hoàn thiện Dự án (Master Completion Plan)

Cập nhật: 24/07/2026

## P0 — Bảo vệ & Sửa lỗi hạ tầng (Blocker)
1. Fix Angular 18 Vitest config (JIT decorators) để Unit Test FE chạy được.
2. Fix Playwright E2E configuration (`test.describe` conflict) để phục vụ test hồi quy.
3. Remove secrets/passwords khỏi codebase, tạo `.env.example`.

## P1 — Ổn định nghiệp vụ Cốt lõi (Critical)
1. **Multiple RoomTypes Booking:** Thiết kế lại DTO và Entity `Reservation` để hỗ trợ 1 Booking chứa nhiều loại phòng.
2. **Payment Idempotency:** Thêm Unique Constraint và Idempotency Key cho API Payment.
3. **Security Masking (IDOR):** Review và áp dụng Aspect chặn truy cập xuyên Property (Owner A xem Data Owner B).
4. **Subscription Quota:** Implement chặn API backend khi Property vượt quá giới hạn phòng/nhân viên của gói cước.

## P2 — Hoàn thiện Vận hành Khách sạn (High)
1. **Dịch vụ khách hàng (Services):** Bổ sung UI Client cho phép khách chọn dịch vụ đi kèm khi book hoặc khi đang ở.
2. **Review System:** Tạo Entity `Review` và UI cho Guest đánh giá khách sạn sau Checkout.
3. **Quy trình Housekeeping:** Bổ sung rule: Checkout -> DIRTY -> Clean -> AVAILABLE. UI map chặt với trạng thái.

## P3 — UI/UX & Quality (Medium)
1. Thêm loading states, error boundaries ở Frontend.
2. Fix warning chunk size (Tách lazy module StompJS/ChartJS).

## Đề xuất Batch triển khai đầu tiên (Batch 1)
**Target:** P0 (Testing Infrastructure) & P1 (Multiple RoomTypes API).
- Sửa file config Vite/Playwright để test runner hoạt động.
- Sửa DTO backend để nhận List RoomType, update logic trừ Inventory.
- Vì sao: Test là lưới bảo vệ bắt buộc trước khi sửa logic Booking phức tạp. Booking nhiều RoomType là core requirement bị missing.
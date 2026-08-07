# Nguồn sơ đồ Mermaid

Thư mục này chứa source Mermaid có thể chỉnh sửa độc lập. Bản trình bày kèm mô tả actor/phạm vi nằm trong `../06-diagrams.md`.

| File | Sơ đồ |
|---|---|
| `D01-use-case-overview.mmd` | Use case tổng quan |
| `D02-auth-sequence.mmd` | Sequence đăng ký/đăng nhập/refresh/logout |
| `D03-search-availability-activity.mmd` | Activity tìm kiếm/chi tiết/phòng trống |
| `D04-booking-payment-sequence.mmd` | Sequence booking/payment/callback |
| `D05-cancellation-refund-activity.mmd` | Activity hủy/hoàn tiền |
| `D06-stay-lifecycle.mmd` | State diagram booking/check-in/check-out |
| `D07-property-room-management.mmd` | Activity quản lý property/phòng |
| `D08-role-permission-sequence.mmd` | Sequence role/permission |
| `D09-dashboard-reporting-sequence.mmd` | Sequence dashboard/reporting |

Chưa xuất PNG/SVG vì Mermaid CLI (`mmdc`) không được cài trong workspace tại thời điểm audit. Mermaid được render trực tiếp bởi Markdown viewer hỗ trợ Mermaid; có thể xuất trong pipeline tài liệu sau khi chốt theme/font.

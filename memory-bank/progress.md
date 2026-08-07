# Progress

## Trạng thái sản phẩm từ source hiện tại

| Khu vực | Bằng chứng source | Trạng thái |
|---|---|---|
| Xác thực và JWT | `AuthController`, `AuthService`, `SecurityConfig`, `JwtAuthFilter` | Có triển khai; chưa chạy test trong task này |
| Role và permission | Role/permission controllers, services, repositories, Angular guards | Có triển khai; chưa chạy test trong task này |
| Quản lý khách sạn/phòng/dịch vụ | Controllers, services, repositories, entities và Angular admin features | Có triển khai; chưa chạy test trong task này |
| Public discovery/search | Public/search controllers, services, Angular home/search features | Có triển khai; chưa chạy test trong task này |
| Reservation | `ReservationController`, `ReservationService`, reservation entities/repositories | Có triển khai; chưa chạy test trong task này |
| Invoice/payment | Controllers, services, entities, repositories và frontend services | Có triển khai; mức hoàn thiện nghiệp vụ production chưa xác minh |
| Subscription | Controller, billing/feature services, scheduler, entities và tests | Có triển khai; chưa chạy test trong task này |
| Chat/realtime | Chat controller/service/repository, WebSocket config, Angular chat features | Có triển khai; chưa chạy test trong task này |
| Analytics | Analytics controller/service và Angular dashboard/charts | Có triển khai; chưa chạy test trong task này |

## Test đã thấy

- Backend integration tests cho auth, admin user, hotel, property search, public discovery, subscription và inventory/unicode.
- Backend service tests cho auth, hotel service, reservation và subscription feature.
- Frontend component specs trong nhiều shared/admin/client feature.
- Playwright E2E specs cho public, customer, owner, admin, search, booking và payment flows.

## Task Memory Bank — 2026-07-23

- Global Rule: đã backup tại `00-shared-memory-policy.md.bak-20260723-124958`, sau đó cập nhật file yêu cầu với policy bắt đầu task, ưu tiên source/yêu cầu, chuyển model, cập nhật cuối task, bảo mật và cô lập dự án.
- Global Skill: đã backup tại `SKILL.md.bak-20260723-124958`, sau đó cập nhật file yêu cầu; YAML front matter có `name: workspace-memory-manager`, trùng tên thư mục.
- Project Memory Bank: có đủ đúng chín file chuẩn từ source đã đọc.
- `sourceIndex.md`: có bảng tám cột yêu cầu và chỉ mục thành phần quan trọng đã đọc.
- Các file Rule, Skill và Memory Bank đã đọc thành công dưới dạng UTF-8; không ghi credential hoặc dữ liệu thật.
- Source nghiệp vụ, test, migration, manifest và cấu hình ứng dụng: không dùng công cụ chỉnh sửa.
- Cấu hình Cline/9Router: không dùng công cụ chỉnh sửa; không thay đổi Provider, Base URL, API Key, Model ID hay router.
- Migration: không chạy.
- Build/test: không chạy theo yêu cầu task.
- Trạng thái Git đầy đủ: `TBD - Chưa xác định từ source`; Git status/diff tiếp tục timeout, không trả output tin cậy.

## Quy tắc cập nhật

Chỉ chuyển trạng thái sang “đã kiểm chứng” sau khi đọc source hiện tại và chạy build/test cần thiết theo phạm vi task. Nếu task cấm build/test, ghi rõ giới hạn thay vì suy diễn.
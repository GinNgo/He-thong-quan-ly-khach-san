# Workspace Rules for Hotel Management System

Khi thực hiện tạo chức năng mới hoặc thay đổi code trong dự án này, AI Agent **BẮT BUỘC** phải tuân thủ các quy tắc sau:

## 1. Ưu tiên cập nhật tài liệu (Documentation-First)
Luôn luôn cập nhật tài liệu thiết kế TRƯỚC KHI viết code. 
Thứ tự ưu tiên cập nhật tài liệu:
1. `docs/ERD.md` (Cấu trúc CSDL)
2. `docs/UML.md` (Luồng hoạt động, Class Diagram, Use Case)
3. `docs/API_SPEC.md` (Đặc tả API)
4. `docs/THESIS.md` (Tài liệu báo cáo/khóa luận)

**KHÔNG BAO GIỜ** được phép viết code mà chưa cập nhật tài liệu. Hãy sử dụng Mermaid (diagram) trong tài liệu để mô tả luồng bất cứ khi nào có thể.

## 2. Tiêu chuẩn Frontend (Angular 20+ & PrimeNG)
- Khi code UI/Frontend, luôn đọc và sử dụng các Design Token, Component Class từ file **`docs/DESIGN.md`**.
- Tuyệt đối dùng CSS Variables đã định nghĩa (ví dụ: `var(--hotel-primary)`), không hardcode mã màu Hex.
- Cấu trúc Standalone Components, Core, Shared, Features. Luôn áp dụng Lazy Loading.
- Đảm bảo tuân theo `docs/FRONTEND_STANDARDS.md`.

## 3. Tiêu chuẩn Backend (Java 21 & Spring Boot 3)
- Code theo kiến trúc: `Controller` -> `Service` -> `Repository` -> `Entity` / `DTO`.
- Mọi logic nghiệp vụ phải đặt ở Service, KHÔNG đặt ở Controller.
- Dùng `AuditableEntity` (hoặc đảm bảo bảng có `created_at`, `updated_at`, `created_by`, `updated_by`) cho mọi table.

## 4. Kiến trúc và Giao tiếp
- Tuân thủ nguyên tắc SOLID và Clean Architecture.
- Sử dụng Swagger để generate document cho API.
- Luôn chú ý tham chiếu đến `docs/DEVELOPMENT_RULES.md` nếu cần tra cứu thêm.

# Active Context

## Task hiện tại

Thiết lập hệ thống bộ nhớ độc lập với model phía sau 9Router bằng Global Rule, Global Skill và Project Memory Bank.

## Đã xác minh

- Repository root theo workspace: `DoAN/He-thong-quan-ly-khach-san`.
- Backend là Spring Boot monolith, package gốc `com.hotel`.
- Frontend là Angular standalone application.
- Backend entrypoint: `backend/src/main/java/com/hotel/BackendApplication.java`.
- Frontend entrypoint: `frontend/src/main.ts`.
- Backend manifest: `backend/pom.xml`.
- Frontend manifest: `frontend/package.json`.
- Backend có controller/service/repository/entity/security packages.
- Frontend có routes, guards, interceptors, core services, feature components và E2E specs.
- Project Memory Bank tồn tại trong `memory-bank/` với đủ chín file chuẩn.
- Repository Git ở branch `main`; remote name `origin`.

## Phạm vi thay đổi của task

- Global Rule: `%USERPROFILE%/Documents/Cline/Rules/00-shared-memory-policy.md`.
- Global Skill: `%USERPROFILE%/.cline/skills/workspace-memory-manager/SKILL.md`.
- Project Memory Bank: `memory-bank/`.
- Không sửa source nghiệp vụ, manifest, cấu hình ứng dụng hoặc cấu hình 9Router.

## Kiểm tra

- Đã đọc README, backend `pom.xml`, frontend `package.json`, cấu trúc source Java/TypeScript và source chọn lọc.
- Global Rule đã được backup tại `00-shared-memory-policy.md.bak-20260723-124958`, sau đó cập nhật policy bắt đầu task, ưu tiên bằng chứng, chuyển model, cập nhật cuối task, bảo mật và cô lập dự án.
- Global Skill đã được backup tại `SKILL.md.bak-20260723-124958`, sau đó cập nhật; YAML front matter có `name: workspace-memory-manager`, trùng tên thư mục.
- `memory-bank/` có đủ đúng chín file chuẩn; `sourceIndex.md` có bảng tám cột yêu cầu.
- Global Rule, Global Skill và các file Memory Bank đã đọc thành công dưới dạng UTF-8.
- Không ghi API key, token, password, connection string hoặc dữ liệu thật vào file bộ nhớ.
- Không dùng công cụ sửa source nghiệp vụ, test, migration, manifest, cấu hình ứng dụng, cấu hình Cline hoặc cấu hình 9Router.
- Không chạy build, test hoặc migration theo yêu cầu thiết lập.
- Các lệnh kiểm tra tự động bằng Python và Git status/diff tiếp tục timeout hoặc không trả output tin cậy; trạng thái Git đầy đủ: `TBD - Chưa xác định từ source`.
- Xem `knownIssues.md` mục `KI-001` cho giới hạn kiểm tra Git.

## Bước tiếp theo

- Khi bắt đầu task mới, đọc lại Memory Bank, source liên quan, `git status` và Git diff.
- Chạy build/test phù hợp trước khi ghi chức năng là đã kiểm chứng.

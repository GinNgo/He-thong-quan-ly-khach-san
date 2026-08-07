# Screenshot evidence manifest

## Current capture update (2026-07-28)

The following files were captured from the running local application and visually inspected:

| File | Route | Result | Use |
| --- | --- | --- | --- |
| `docs/screenshots/home-search-after-desktop.png` | `/` at 1440x900 | CURRENT/PASS | Public search screen, demo-safe data |
| `docs/screenshots/home-search-after-mobile.png` | `/` at 390x844 | CURRENT/PASS | Responsive public search screen |
| `docs/screenshots/search-result-current-blocked.png` | `/search?...` | CURRENT/BLOCKED | Search error state; backend service was unavailable in the browser session |
| `docs/screenshots/admin-roles-current-blocked.png` | `/admin/roles` | CURRENT/BLOCKED | Role screen with `Failed to fetch`; not evidence of completed data loading |
| `docs/screenshots/admin-rooms-current-blocked.png` | `/admin/rooms` | CURRENT/BLOCKED | Room screen with `Failed to fetch`; not evidence of completed data loading |

The original `*-after.png` admin/search/room files remain HISTORICAL candidates and are not relabeled as CURRENT without a successful data-backed retest. The two home files above were refreshed in place.

Ngày lập: 2026-07-28

Ảnh hiện có được xem là evidence lịch sử/ứng viên cho tới khi kiểm tra lại route, dữ liệu hiển thị và privacy trên worktree đã chốt.

| ID | File | Capability | Route/Role dự kiến | Caption dự kiến | Freshness | Privacy |
| --- | --- | --- | --- | --- | --- | --- |
| SS-001 | docs/screenshots/home-search-after-desktop.png | SEARCH-01 | /, Guest desktop | Giao diện tìm kiếm cơ sở trên desktop | HISTORICAL | PASS_CANDIDATE |
| SS-002 | docs/screenshots/home-search-after-mobile.png | SEARCH-01 | /, Guest mobile | Giao diện tìm kiếm cơ sở trên thiết bị di động | HISTORICAL | PASS_CANDIDATE |
| SS-003 | docs/screenshots/search-result-after.png | SEARCH-01 | /search, Guest | Kết quả tìm kiếm và bộ lọc | HISTORICAL | PASS_CANDIDATE |
| SS-004 | docs/screenshots/room-selection-after.png | BOOK-01 | /hotel/:id hoặc booking flow, Guest/Customer | Chọn một RoomType và số lượng phòng | HISTORICAL | PASS_CANDIDATE |
| SS-005 | docs/screenshots/public-home-quality-after.png | SEARCH-01 | /, Guest | Trang chủ public sau cải tiến chất lượng | HISTORICAL | PASS_CANDIDATE |
| SS-006 | docs/screenshots/public-profile-menu-after.png | AUTH-01 | Client layout, Customer desktop | Menu tài khoản khách hàng trên desktop | HISTORICAL | PASS_CANDIDATE_DEMO_EMAIL |
| SS-007 | docs/screenshots/public-profile-menu-mobile-after.png | AUTH-01 | Client layout, Customer mobile | Menu tài khoản khách hàng trên mobile | HISTORICAL | PASS_CANDIDATE_DEMO_EMAIL |
| SS-008 | docs/screenshots/admin-roles-after.png | RBAC-01 | /admin/roles, Admin | Quản lý vai trò sau khi tải dữ liệu | HISTORICAL | PASS_CANDIDATE |
| SS-009 | docs/screenshots/admin-roles-loading-before.png | RBAC-01 | /admin/roles, Admin | Trạng thái loading dùng làm bằng chứng audit | HISTORICAL | PASS_CANDIDATE |
| SS-010 | docs/screenshots/admin-rooms-after.png | ROOM-01 | /admin/rooms, Owner/Admin | Quản lý phòng vật lý | HISTORICAL | PASS_CANDIDATE |

## Quy tắc chọn ảnh cho bản nộp

- Chụp lại ảnh CURRENT nếu UI/route/data đã thay đổi.
- Ghi role, route, ngày chụp và capabilityId trong caption source.
- Không để token, email/số điện thoại thật, secret, local path hoặc dữ liệu khách hàng.
- Ảnh “before/loading” chỉ đưa vào phần đánh giá/cải tiến, không dùng làm hình chức năng hoàn thành.
- Mỗi ảnh trong Word/PDF phải có caption Hình x.y và được tham chiếu trong văn bản.

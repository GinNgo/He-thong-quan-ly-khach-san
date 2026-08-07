# Change impact register báo cáo

Mục đích: ghi thay đổi source/feature và các artifact cần rà soát trước khi claim được dùng lại trong báo cáo.

## Quy tắc

1. Ghi trigger ngay khi có diff.
2. Gắn capabilityId ổn định; nếu chưa có thì tạo record trước.
3. Cập nhật ERD/UML/API trước THESIS.
4. Chạy test hoặc ghi BLOCKED/HISTORICAL.
5. Ghi verifiedAt, reviewer và artifact đã kiểm tra.

## Ma trận trigger

| Trigger | Capability thường bị ảnh hưởng | Artifact bắt buộc | Verification |
| --- | --- | --- | --- |
| ROUTE/menu/actor | Auth, public search, admin/management | THESIS_ROUTE_EVIDENCE, UML Use Case, API_SPEC, Chapter 3-4, screenshot | route scan + backend permission test |
| Permission/security | Auth/RBAC, tenant, notification/chat | code evidence, UML sequence, API_SPEC, Chapter 4, security tests | endpoint/WebSocket authorization |
| API/service | Booking, payment, refund, stay operations | API_SPEC, Sequence/Activity, FEATURE_SUMMARY, Chapter 4 | service/integration test |
| Entity/migration | Inventory, reservation, invoice, subscription | ERD, Class Diagram, API_SPEC, Chapter 3-4 | migration + repository/entity review |
| UI/screenshot | Search, checkout, admin, operations | screenshot manifest, captions, Chapter 4, rubric | browser/manual check + privacy |
| Test/config | Any capability | THESIS_TEST_EVIDENCE, Chapter 4.8-4.10, limitation | rerun command or BLOCKED record |
| New feature | New capability | all affected docs, UML/ERD/API, rubric | converge source-to-report |
| Removed/deferred feature | Existing capability | FEATURE_SUMMARY, limitations, UML, Chapter 5, rubric | confirm no claim remains |

## Active entries

| Change ID | Trigger | Capability | Scope | Status | Next check |
| --- | --- | --- | --- | --- | --- |
| CHG-2026-07-28-01 | SECURITY/WEBSOCKET | Notification/support chat | Handshake, channel authorization, notification controller/service/test | PARTIAL/CURRENT | Backend 123/123 và frontend unit 73/73 pass; chốt authenticated E2E và release workstream |
| CHG-2026-07-28-02 | DOCUMENTATION | Thesis maintenance | Spec Kit plan/tasks/evidence manifests | CURRENT | D01-D08 và rubric đã cố định; tiếp tục render REVIEW/FINAL |
| CHG-2026-07-29-01 | ADMIN_E2E | 29 route Admin | Static route/API/permission inventory, shell smoke và data-backed core smoke | BLOCKED/PARTIAL | Cô lập backend LuxeStay, cấp `LUXESTAY_E2E_*`, chạy read/mutation/authorization; xử lý backlog ADM-FIX-001..010 |
| CHG-2026-07-29-02 | DOCX_DIAGRAM | Báo cáo và 24 sơ đồ | Raster PNG, panel (a)/(b), caption/alt text, package structural QA | CURRENT/BLOCKED_VISUAL | Mở bằng Word hoặc renderer, kiểm tra 100% trang trước REVIEW/FINAL |

## Release gate

Không đóng một entry thành VERIFIED nếu artifact liên quan chưa có current evidence. Entry BLOCKED phải có owner hoặc bước mở khóa; không được che bằng cách xóa capability khỏi báo cáo.

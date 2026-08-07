# Ma trận truy vết chức năng

Ngày rà soát source: 2026-07-29

Đây là baseline ưu tiên các capability cốt lõi. Status dùng COMPLETE, PARTIAL, MISSING, BLOCKED hoặc DEFERRED. Backend đã có run CURRENT 123/123 và frontend unit 73/73; các capability giao diện/E2E vẫn giữ PARTIAL hoặc BLOCKED khi chưa có verification data-backed tương ứng.

| ID | Actor | Nghiệp vụ | UI/Route | API/Service/Data | Status | Evidence freshness | VerifiedAt | Report sections | Giới hạn/việc cần xác minh |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| AUTH-01 | Guest/Customer/Admin | Đăng ký, đăng nhập JWT và tải menu/quyền | /login, /register, /admin/login | AuthController, UserController, security, users | COMPLETE | CURRENT | 2026-07-28 | 2.3, 3.3, 4.2 | Backend AuthService/AuthController/AuthException tests pass; frontend E2E còn pending |
| RBAC-01 | Admin/Staff | Role, permission/action mask và backend authorization | /admin/roles, /admin/role-permissions, guarded admin routes | RoleController, RolePermissionController, PermissionInterceptor | COMPLETE | CURRENT | 2026-07-28 | 3.3, 4.2 | Endpoint/security tests pass; UI guard không thay thế backend |
| PROP-01 | Owner/Admin | Quản lý Property và phạm vi multi-property | /admin/properties, /management/properties | HotelController, ManagementPortalController, properties, user_properties | PARTIAL | SOURCE_ONLY | 2026-07-28 | 3.4, 3.8, 4.6 | Cần xác minh permission từng route và E2E owner |
| ROOM-01 | Owner/Admin/Staff | Quản lý RoomType và phòng vật lý | /admin/room-types, /admin/rooms, /management/room-types, /management/rooms | RoomTypeController, RoomController, ManagementPortalController | PARTIAL | SOURCE_ONLY | 2026-07-28 | 3.4, 4.3, 4.5 | Cần current service/integration/E2E evidence |
| SEARCH-01 | Guest | Tìm địa điểm/cơ sở, autocomplete và availability | /, /search, /hotel/:id | LocationController, PublicDiscoveryController, PropertySearchController, HotelController | PARTIAL | CURRENT | 2026-07-28 | 3.7, 4.3 | Backend integration/Unicode tests pass; frontend E2E chưa chạy lại |
| BOOK-01 | Customer | Đặt một RoomType với quantity lớn hơn một | /booking/:roomTypeId, /booking-history | ReservationController, ReservationService, reservations/reservation details | PARTIAL | CURRENT | 2026-07-28 | 3.5, 4.3 | Reservation/payment tests pass; frontend/E2E flow còn pending |
| BOOK-02 | Customer | Đặt nhiều RoomType trong một booking/cart | Không có route/cart đầy đủ | Chưa có aggregate/request contract đầy đủ | DEFERRED | CURRENT_SCOPE | 2026-07-28 | 5.2, 5.3 | Không được mô tả như chức năng đã hoàn thành |
| PAY-01 | Customer/Test | Thanh toán VNPay/simulator, callback và kết quả | /payment-simulator, /payment-result | PaymentController, MockPaymentController, PaymentService, payments | PARTIAL | CURRENT | 2026-07-28 | 3.5, 4.4 | Payment service/controller tests pass; frontend/E2E callback flow còn pending |
| INV-01 | Customer/Admin | Xem và tạo hóa đơn theo reservation | /my-invoices, /admin/invoices | InvoiceController, invoices | PARTIAL | CURRENT | 2026-07-28 | 4.4, 4.7 | Backend suite pass; ownership/UI evidence cần bổ sung |
| OPS-01 | Receptionist/Manager | Gán phòng, check-in, dịch vụ, check-out, housekeeping | /admin/reservations và management routes liên quan | ReservationController, HotelServiceController, ManagementPortalController | PARTIAL | CURRENT | 2026-07-28 | 3.6, 4.5 | Backend suite pass; E2E/state audit còn pending |
| SUB-01 | Owner/Admin | Xem gói, feature limit và billing context | /admin/plans, /management/billing | SubscriptionController, SubscriptionFeatureService, subscription data | PARTIAL | CURRENT | 2026-07-28 | 3.8, 4.6, 5.2 | Backend feature/controller tests pass; full lifecycle/history chưa hoàn thiện |
| IMPORT-01 | Admin/Partner | Import, deduplicate và claim property | /admin/property-imports, /admin/property-claims, /partner/register | PropertyImportController, PropertyClaimController, PropertyRegistrationController | PARTIAL | CURRENT | 2026-07-28 | 3.9, 4.6 | Import source/test hiện có; claim controller còn requester/reviewer ID cố định, UI/E2E chưa xác minh |
| CHAT-01 | Customer/Support/Admin | Central support chat có xác thực | /admin/chat; customer client integration | ChatController, ChatService, WebSocket security/config | PARTIAL | CURRENT | 2026-07-28 | 3.3, 4.6 | Controller/service/channel tests pass; frontend authenticated E2E còn pending |
| NOTIF-01 | Authenticated user | Nhận và đánh dấu thông báo | Notification service/UI integration | NotificationController, NotificationService, WebSocket config | PARTIAL | CURRENT | 2026-07-28 | 4.6, 4.7 | Controller/service/channel tests pass; frontend delivery/E2E còn pending |
| REVIEW-01 | Customer/Admin | Review submission, moderation và aggregation | Chưa có route hoàn chỉnh | Chưa có contract end-to-end | DEFERRED | CURRENT_SCOPE | 2026-07-28 | 5.2, 5.3 | Không dùng điểm đánh giá UI làm evidence review thật |
| FAV-01 | Customer | Favorites | Chưa có route hoàn chỉnh | Chưa có contract end-to-end | DEFERRED | CURRENT_SCOPE | 2026-07-28 | 5.2, 5.3 | Không mô tả là đã triển khai |
| REPORT-01 | Owner/Admin | Analytics/doanh thu/công suất nâng cao | Dashboard/admin shell | AnalyticsController và dữ liệu liên quan | PARTIAL | SOURCE_ONLY | 2026-07-28 | 4.7, 5.2, 5.3 | Advanced reconciliation/reporting chưa hoàn chỉnh |

## Quy tắc cập nhật

- COMPLETE cần SOURCE/API evidence và CURRENT verification evidence.
- SOURCE_ONLY giữ PARTIAL dù đã có test file nhưng chưa chạy.
- PARALLEL_WORK chỉ giữ BLOCKED khi chưa có verification; sau current backend test, capability vẫn PARTIAL nếu frontend/E2E hoặc release stability chưa được kiểm tra.
- CURRENT_SCOPE dùng cho giới hạn đã xác nhận; DEFERRED chỉ xuất hiện ở hạn chế/hướng phát triển.
- Khi thêm capability, cập nhật THESIS_ROUTE_EVIDENCE, THESIS_CODE_EVIDENCE, THESIS_TEST_EVIDENCE và reportSections cùng một lần.

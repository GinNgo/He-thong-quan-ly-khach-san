# Báo cáo xung đột giữa tài liệu và source code

Ngày rà soát: 2026-07-29

Nguồn đối chiếu: THESIS_ROUTE_EVIDENCE.md, THESIS_CODE_EVIDENCE.md, THESIS_TEST_EVIDENCE.md và FEATURE_TRACEABILITY_MATRIX.md.

| Nghiệp vụ | Claim/tài liệu cũ | Evidence source hiện tại | Kết luận dùng cho báo cáo |
| --- | --- | --- | --- |
| Subscription/feature limit | Báo cáo cũ ghi backend chưa chặn chặt hoặc CODE_ONLY | Có SubscriptionFeatureService, ManagementPortalService và test source; full lifecycle/history chưa đầy đủ | PARTIAL; mô tả feature limit hiện có nhưng không nhận activate/renew/upgrade/downgrade/revoke đầy đủ |
| Dịch vụ lưu trú | Một số nội dung dễ hiểu là Customer chọn dịch vụ tại checkout | ReservationController/Service hỗ trợ staff thêm dịch vụ trong thời gian lưu trú | Staff services PARTIAL/implemented source; customer add-on checkout DEFERRED |
| Mixed RoomType | Roadmap/ý tưởng có thể bị đọc như đã hỗ trợ | UI route /booking/:roomTypeId và request/service hiện theo một RoomType với quantity | BOOK-02 DEFERRED; không trình bày như chức năng hiện tại |
| Payment provider | Ma trận cũ ghi MoMo | Source hiện có PaymentController, VNPay callback và payment simulator | Sửa mọi claim MoMo thành VNPay/simulator theo contract hiện hành |
| Payment idempotency/refund | Báo cáo cũ ghi thiếu idempotency chặt ở backend | Source/migration/test file có callback deduplication, refund ledger/idempotency; Maven 123/123 CURRENT | PARTIAL cho tới khi frontend/E2E callback flow được xác minh; không giữ gap cũ như sự thật hiện hành |
| Role & Permission | Tài liệu khẳng định hoạt động/pass | Có route guard, role/permission controller, PermissionInterceptor và security tests trong Maven 123/123 CURRENT | COMPLETE cho backend contract; UI/E2E vẫn là phạm vi cần xác minh riêng |
| Tenant isolation | Constitution yêu cầu data isolation mạnh và Hibernate filter | Source có PropertyAccessService và repository/service filtering; cần kiểm tra mức tuân thủ Hibernate filter toàn diện | PARTIAL; không tuyên bố hoàn toàn tuân thủ constitution khi chưa audit entity/repository |
| Review/Favorites | Roadmap/summary có tên chức năng | Chưa có route/contract end-to-end đã xác minh | DEFERRED; chỉ đưa Chapter 5 |
| Chat hỗ trợ | THESIS/UML đã mô tả central support chat | Có ChatController/Service/WebSocket security source; ChatController, ChatService và channel tests hiện hành pass | PARTIAL/CURRENT; frontend authenticated E2E và release stability còn pending |
| Notification | API/docs có contract shell hoặc code mới | NotificationController/Service và channel/handshake security source; notification tests hiện hành pass | PARTIAL/CURRENT; frontend delivery/E2E còn pending |
| Frontend/E2E tests | Tài liệu có các số 20/20, 10/10, 5/5... và audit cũ ghi runner crash | Frontend unit 73/73 CURRENT; Playwright discovery 71 test nhưng run timeout và artifact redirect/search | Unit CURRENT; Playwright BLOCKED; số cũ giữ HISTORICAL |
| Backend test count | THESIS có 49/49, 86/86 và 122/122; FEATURE_SUMMARY có 60/60 | Maven `test` ngày 29/07/2026 chạy 123/123, 0 failure/error/skipped | Số cũ HISTORICAL; 123/123 là CURRENT |
| Admin smoke | 17 route smoke pass có thể bị hiểu là toàn bộ Admin hoạt động | Assertions chủ yếu `body visible`; core data-backed run 1 fail/2 không chạy do login fixture | 29 route giữ BLOCKED_RUNTIME; `/admin/plans` purchase PARTIAL; không dùng smoke shell làm bằng chứng hoàn thành |
| Analytics/reporting | Có dashboard/controller và roadmap báo cáo | AnalyticsController tồn tại; advanced reconciliation/công suất/doanh thu chưa có evidence đầy đủ | PARTIAL; báo cáo nâng cao giữ ở hạn chế/hướng phát triển |

## Quy tắc xử lý

- Xung đột claim-provider được sửa theo source hiện hành, ví dụ MoMo -> VNPay/simulator.
- Xung đột test count được giải bằng nhãn HISTORICAL, không chọn một con số cũ làm CURRENT.
- Capability của workstream song song dùng BLOCKED khi chưa có verification; sau khi backend test pass vẫn dùng PARTIAL nếu UI/E2E/release chưa chốt.
- Feature không có contract end-to-end dùng DEFERRED/MISSING và không xuất hiện trong phần chức năng đã cài đặt.
- Sau mỗi current test run, cập nhật THESIS_TEST_EVIDENCE, FEATURE_TRACEABILITY_MATRIX, FEATURE_SUMMARY và Chapter 4 cùng một lần.

## Cross-check 2026-07-29

- API/UML/THESIS không còn dùng các endpoint subscription mutation chưa tồn tại, `/checkin`, `/checkout` hoặc `/invoices/generate` như contract hiện hành.
- UML/ERD đã loại các class/field cũ như `SubscriptionService`, `FeatureInterceptor`, `districtId`, `@PostConstruct` import và `LOCATION_JSON_PATH`.
- ERD rút gọn trong Chương 3 đã sửa quan hệ `ReservationDetail -> ReservationRoom`.
- Backend 123/123, frontend 73/73 và build CURRENT thống nhất giữa test evidence, feature summary, capability matrix và Chương 4; Playwright/Admin data-backed giữ BLOCKED.
- Property claim ID cố định được ghi đồng nhất trong API, UML, code evidence, capability matrix, rubric gap và Chương 3-4.
- 24/24 sơ đồ render thành PNG thành công; asset cao được tách panel và DOCX không còn SVG media. Visual Word/PDF vẫn chờ render từng trang.

# Ma trận coverage sơ đồ khóa luận

**Ngày lập:** 2026-07-29  
**Nguồn:** `docs/UML.md`, `docs/ERD.md`, `docs/audit/FEATURE_TRACEABILITY_MATRIX.md`, source route/API/entity/test hiện hành.  
**Quy tắc:** SVG là source để diff; PNG là asset nhúng DOCX. `REQUIRED` nghĩa capability cần được nhìn thấy trong loại sơ đồ đó; `NOT_APPLICABLE` phải có lý do.

**Flowchart policy:** Không tạo flowchart trùng với Activity Diagram. FLOW-01..FLOW-04 được biểu diễn lần lượt bằng UML-16..UML-19; các CRUD đơn giản không cần flowchart riêng.

## 1. Capability -> diagram coverage

| Capability | Use Case | Class | Sequence | Activity | ERD | Figure/source hiện có | Evidence hiện tại | Việc còn thiếu |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| AUTH-01 Đăng ký/đăng nhập JWT | REQUIRED: UML-01 | REQUIRED: UML-04 | REQUIRED: UML-08 | NOT_APPLICABLE: không có state nghiệp vụ dài | REQUIRED: ERD-01 | UML-01/04/08, ERD-01 | Backend CURRENT; UI/E2E pending | PNG, Word visual, login E2E |
| RBAC-01 Role/permission/action mask | REQUIRED: UML-01 | REQUIRED: UML-04 | REQUIRED: UML-08 | NOT_APPLICABLE: authorization cross-cutting | REQUIRED: ERD-01 | UML-01/04/08, ERD-01 | Backend security tests CURRENT | Permission matrix E2E, PNG |
| PROP-01 Property/multi-property | REQUIRED: UML-01 | REQUIRED: UML-05 | REQUIRED: UML-08 hoặc UML-12 khi vận hành | REQUIRED: UML-18 khi gắn vòng đời phòng | REQUIRED: ERD-02 | UML-01/05/12/18, ERD-02 | SOURCE_ONLY/PARTIAL | Admin route authorization + current E2E |
| ROOM-01 RoomType/phòng vật lý | REQUIRED: UML-01 | REQUIRED: UML-05 | REQUIRED: UML-12 khi gán phòng | REQUIRED: UML-18 | REQUIRED: ERD-02 | UML-01/05/12/18, ERD-02 | SOURCE_ONLY/PARTIAL | CRUD/bulk/maintenance E2E, PNG tách rộng |
| SEARCH-01 Tìm kiếm/availability | REQUIRED: UML-01 | NOT_APPLICABLE: không thêm entity quản trị trong flow tổng quát | REQUIRED: UML-09 | REQUIRED: UML-16 (nhánh availability) | REQUIRED: ERD-02/03 | UML-01/09/16, ERD-02/03 | Backend CURRENT; frontend pending | Current search/availability E2E |
| BOOK-01 Đặt một RoomType, quantity > 1 | REQUIRED: UML-01 | REQUIRED: UML-06 | REQUIRED: UML-10 | REQUIRED: UML-16 | REQUIRED: ERD-03 | UML-01/06/10/16, ERD-03 | Backend CURRENT; UI/E2E pending | Booking E2E và screenshot |
| BOOK-02 Nhiều RoomType/cart | NOT_APPLICABLE: DEFERRED | NOT_APPLICABLE: contract chưa có | NOT_APPLICABLE: contract chưa có | NOT_APPLICABLE: contract chưa có | NOT_APPLICABLE: mô hình mục tiêu ERD-05 riêng | ERD-05 only (DEFERRED) | DEFERRED | Chỉ giữ trong hạn chế/roadmap |
| PAY-01 Thanh toán/callback/refund | REQUIRED: UML-01 | REQUIRED: UML-06 | REQUIRED: UML-10/11 | REQUIRED: UML-17 | REQUIRED: ERD-03 | UML-01/06/10/11/17, ERD-03 | Backend CURRENT; frontend pending | Payment callback/idempotency E2E |
| INV-01 Hóa đơn | REQUIRED: UML-01 | REQUIRED: UML-06 | REQUIRED: UML-12 hoặc UML-10 | NOT_APPLICABLE: state nằm trong reservation/payment | REQUIRED: ERD-03 | UML-01/06/10/12, ERD-03 | Backend CURRENT; UI pending | Generate/list/duplicate E2E |
| OPS-01 Stay/check-in/service/check-out | REQUIRED: UML-01 | REQUIRED: UML-06 | REQUIRED: UML-12 | REQUIRED: UML-18 | REQUIRED: ERD-03 | UML-01/06/12/18, ERD-03 | Backend CURRENT; E2E pending | State/tenant/action E2E |
| SUB-01 Subscription/feature limit | REQUIRED: UML-01 | REQUIRED: UML-07 | REQUIRED: UML-08 hoặc UML-14 khi chat/notification liên quan | NOT_APPLICABLE: chưa có lifecycle workflow riêng | REQUIRED: ERD-01 | UML-01/07/08, ERD-01 | Backend CURRENT; full lifecycle partial | `/admin/plans` purchase phải ghi PARTIAL |
| IMPORT-01 Import/dedup/claim | REQUIRED: UML-02 | REQUIRED: UML-07 | REQUIRED: UML-13 | REQUIRED: UML-19 | REQUIRED: ERD-04 | UML-02/07/13/19, ERD-04 | Backend import CURRENT; claim partial | Requester/reviewer ID gap + E2E |
| CHAT-01 Support chat | REQUIRED: UML-03 | REQUIRED: UML-07 | REQUIRED: UML-14 | NOT_APPLICABLE: session/state mô tả trong sequence | REQUIRED: ERD-04 | UML-03/07/14, ERD-04 | Backend/channel tests CURRENT; frontend pending | Authenticated WebSocket E2E |
| NOTIF-01 Notification | REQUIRED: UML-03 | REQUIRED: UML-07 | REQUIRED: UML-15 | NOT_APPLICABLE: delivery state cross-cutting | REQUIRED: ERD-04 | UML-03/07/15, ERD-04 | Backend CURRENT; frontend pending | Delivery/read E2E |
| REVIEW-01 Review | NOT_APPLICABLE: DEFERRED | NOT_APPLICABLE: contract chưa có | NOT_APPLICABLE | NOT_APPLICABLE | NOT_APPLICABLE: không có schema hiện hành | Không đưa vào figure chức năng | DEFERRED | Chỉ roadmap, không vẽ như complete |
| FAV-01 Favorites | NOT_APPLICABLE: DEFERRED | NOT_APPLICABLE | NOT_APPLICABLE | NOT_APPLICABLE | NOT_APPLICABLE | Không đưa vào figure chức năng | DEFERRED | Chỉ roadmap, không vẽ như complete |
| REPORT-01 Analytics nâng cao | REQUIRED: UML-01 | REQUIRED: UML-07 khi có domain riêng | REQUIRED: UML-08 cho dashboard query | NOT_APPLICABLE: query/report không có workflow | REQUIRED: ERD-03 nếu dùng payment/reservation | UML-01/07/08, ERD-03 | SOURCE_ONLY/PARTIAL; work-order đang mô phỏng | Data-backed dashboard audit |

## 2. Figure asset checklist

| Mã nhóm | Figure | Panel/khổ đề xuất | PNG | Caption/alt/reference | Word status |
| --- | --- | --- | --- | --- | --- |
| UML-01 | Use Case tổng quát | Tách actor nếu quá cao; dọc | `docs/thesis-assets/diagrams/png/uml-01.png` | Caption/alt/reference đã gắn trong builder | `PASS_STRUCTURAL/BLOCKED_VISUAL` |
| UML-02..03 | Use Case import/chat | Dọc | PNG tương ứng 2 source | Caption/alt/reference đã gắn trong builder | `PASS_STRUCTURAL/BLOCKED_VISUAL` |
| UML-04..07 | Class | UML-06/UML-07 có thể tách domain | PNG tương ứng 4 source | Alt text theo domain đã gắn | `PASS_STRUCTURAL/BLOCKED_VISUAL` |
| UML-08..15 | Sequence | UML-09/UML-13 landscape hoặc panel | PNG tương ứng 8 source | Nhánh lỗi/partial được giữ | `PASS_STRUCTURAL/BLOCKED_VISUAL` |
| UML-16..19 | Activity | Dọc riêng; không co chữ dưới ngưỡng đọc | PNG tương ứng 4 source | Caption/reference đã gắn | `PASS_STRUCTURAL/BLOCKED_VISUAL` |
| ERD-01..04 | Schema hiện hành | ERD-01/03 landscape hoặc panel `(a)/(b)` | PNG tương ứng 4 source | Alt text entity/relationship đã gắn | `PASS_STRUCTURAL/BLOCKED_VISUAL` |
| ERD-05 | Mô hình mục tiêu | Dọc/lưu appendix | `docs/thesis-assets/diagrams/png/erd-05.png` | Bắt buộc nhãn `DEFERRED` và caption | `PASS_STRUCTURAL/BLOCKED_VISUAL` |
| ARCH-01 | Kiến trúc tổng thể/flow triển khai | Dọc hoặc landscape | `docs/thesis-assets/diagrams/png/architecture-01.png` | Hình 3.2; caption và alt text đã gắn trong builder | `PASS_STRUCTURAL/BLOCKED_VISUAL` |

## 3. Gate phát hành

1. Không có capability COMPLETE/PARTIAL hiện hành nào thiếu Use Case hoặc quyết định coverage cho bốn loại sơ đồ còn lại.
2. Không đưa BOOK-02, REVIEW-01, FAV-01 hoặc ERD-05 vào phần “đã cài đặt”.
3. Mỗi source SVG có PNG tương ứng được nhúng trong DOCX; SVG không được xuất hiện như relationship media chính.
4. Mỗi figure có caption duy nhất, alt text, tên file ổn định và tham chiếu trong `docs/THESIS.md`.
5. Sơ đồ rộng được kiểm tra ở 100% zoom trong Microsoft Word và bản render PNG/PDF; nếu chữ khó đọc thì tách panel, không chỉ thu nhỏ.

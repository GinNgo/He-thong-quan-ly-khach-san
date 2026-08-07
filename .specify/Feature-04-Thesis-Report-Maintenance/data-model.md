# Data Model: Report Maintenance

## 1. Report Capability

Đại diện cho một chức năng hoặc một luồng nghiệp vụ được mô tả trong báo cáo.

| Trường | Kiểu | Bắt buộc | Quy tắc |
| --- | --- | --- | --- |
| capabilityId | string | Có | Ổn định, dạng DOMAIN-NN, không đổi khi đổi câu chữ |
| name | string | Có | Tên nghiệp vụ tiếng Việt, có tên kỹ thuật nếu cần |
| actor | enum/list | Có | Guest, Customer, Receptionist, Owner, Admin, Support hoặc hệ thống tích hợp |
| scope | string | Có | Mô tả ngắn phạm vi hiện tại |
| uiRoutes | list | Không | Route/menu/screen đã kiểm tra |
| apiEndpoints | list | Không | Method + endpoint thực tế |
| services | list | Không | Controller/service/component liên quan |
| dataObjects | list | Không | Entity/table/migration liên quan |
| status | enum | Có | COMPLETE, PARTIAL, MISSING, BLOCKED, DEFERRED |
| evidenceIds | list | Có | Ít nhất một source evidence; COMPLETE cần test/screenshot hoặc lý do kiểm thử |
| verifiedAt | date | Có | Ngày xác minh gần nhất, ISO-8601 |
| reportSections | list | Có | Chapter/section/figure/table chịu ảnh hưởng |
| limitations | string | Không | Giới hạn hoặc nhánh chưa có |
| changeLog | list | Không | Ngày, người sửa, lý do và artifact đã cập nhật |

## 2. Evidence Record

| Trường | Kiểu | Quy tắc |
| --- | --- | --- |
| evidenceId | string | Dạng EVID-NN hoặc loại + ngày |
| kind | enum | SOURCE, MIGRATION, TEST, SCREENSHOT, LOG, API, UML, ERD, HISTORICAL |
| pathOrCommand | string | Path repo tương đối hoặc lệnh tái lập; không chứa path máy cá nhân |
| claim | string | Claim mà bằng chứng hỗ trợ |
| result | string | Kết quả quan sát được |
| capturedAt | date | Ngày chạy/chụp/đọc |
| freshness | enum | CURRENT, HISTORICAL, BLOCKED |
| privacyReviewed | boolean | Bắt buộc true trước bản nộp |
| notes | string | Điều kiện, giới hạn, môi trường |

## 3. Diagram Specification

| Trường | Kiểu | Quy tắc |
| --- | --- | --- |
| diagramId | string | Dạng UC-NN, ACT-NN, SEQ-NN, CLS-NN hoặc ERD-NN |
| type | enum | USE_CASE, ACTIVITY, SEQUENCE, CLASS, ERD |
| capabilityIds | list | Capability mà sơ đồ minh họa |
| sourcePath | string | docs/UML.md hoặc docs/ERD.md |
| sourceSyntax | enum | MERMAID hoặc PLANTUML |
| caption | string | Theo quy tắc Hình x.y |
| figureAssetIds | list | Một hoặc nhiều PNG/panel được nhúng trong Word |
| coverageDecision | enum | REQUIRED hoặc NOT_APPLICABLE |
| coverageReason | string | Bắt buộc khi NOT_APPLICABLE |
| purpose | string | Vì sao cần sơ đồ |
| description | string | Thành phần và luồng chính |
| analysis | string | Nhận xét thiết kế, ràng buộc, nhánh lỗi |
| conclusion | string | Kết luận phạm vi hiện tại |
| lastVerifiedAt | date | Ngày đối chiếu source |

## 4. Thesis Section

| Trường | Kiểu | Quy tắc |
| --- | --- | --- |
| sectionId | string | Ví dụ CH3-3.5 |
| title | string | Khớp tiêu đề trong THESIS.md |
| chapter | integer | 1 đến 5 |
| objective | string | Mục tiêu học thuật của mục |
| capabilityIds | list | Không để section không có nguồn chức năng |
| diagramIds | list | Sơ đồ được tham chiếu trong mục |
| tableIds | list | Bảng được tham chiếu trong mục |
| screenshotIds | list | Ảnh và ngữ cảnh vai trò |
| evidenceIds | list | Evidence hiện hành |
| references | list | Tài liệu đã trích dẫn |
| status | enum | DRAFT, REVIEW, VERIFIED, RELEASED |

## 5. Rubric Criterion

| Trường | Kiểu | Quy tắc |
| --- | --- | --- |
| criterionId | string | Dùng trace ID D03-C1..C7 hoặc D04-C1..C7 vì bản export không có mã riêng; giữ nguyên STT nguồn |
| rubricName | string | Tên rubric và phiên bản/ngày |
| weight | number | Lấy nguyên văn từ rubric D03/D04 đã checksum-verified; không để TBD trong artifact phát hành |
| requirement | string | Diễn giải tiêu chí |
| answer | string | Câu trả lời ngắn khi bảo vệ |
| sectionIds | list | Vị trí trong báo cáo |
| evidenceIds | list | Bằng chứng trực tiếp |
| limitation | string | Phần chưa đạt hoặc điều kiện |
| readiness | enum | READY, NEEDS_EVIDENCE, BLOCKED, NOT_APPLICABLE |

## 6. Template Slot

| Trường | Kiểu | Quy tắc |
| --- | --- | --- |
| slotNo | integer | 1 đến 13 |
| title | string | Tên nhóm biểu mẫu |
| required | boolean | Theo hướng dẫn trường và danh sách người dùng |
| sourceTemplate | string | File export hoặc link Drive |
| status | enum | MISSING, DRAFT, RECEIVED, VERIFIED, FINAL |
| notes | string | Chữ ký/điểm/nhận xét còn thiếu |

## 7. Figure Asset

| Trường | Kiểu | Quy tắc |
| --- | --- | --- |
| figureAssetId | string | Dạng FIG-CHAPTER-NN hoặc FIG-CHAPTER-NN-A/B |
| diagramId | string | Sơ đồ nguồn tương ứng |
| sourceSvgPath | string | Path repo tương đối, không nhúng trực tiếp vào DOCX |
| embeddedPngPath | string | PNG độ phân giải cao dùng trong DOCX |
| panel | enum | NONE, A hoặc B |
| caption | string | Duy nhất; panel dùng Hình x.y(a)/(b) |
| altText | string | Mô tả actor/domain/flow chính |
| pixelWidth | integer | Đủ để chữ đọc được khi đặt trên A4 |
| pixelHeight | integer | Giữ đúng tỷ lệ source |
| wordStatus | enum | NOT_CHECKED, PASS, FAIL, BLOCKED |
| renderStatus | enum | NOT_CHECKED, PASS, FAIL |
| referencedBy | list | Section/đoạn văn tham chiếu hình |

## 8. Admin Verification Record

| Trường | Kiểu | Quy tắc |
| --- | --- | --- |
| adminFunctionId | string | Dạng ADM-NN, ổn định theo route/chức năng |
| route | string | Route Admin hiện hành |
| menuLabel | string | Nhãn menu hoặc `DIRECT_ROUTE` |
| roles | list | Role được phép truy cập |
| permission | string/list | Guard/action mask/backend permission thực tế |
| component | string | Component route đích |
| apiEndpoints | list | API dùng để load và mutation/action |
| readChecks | list | Loading, data, empty, error cần kiểm tra |
| mutationChecks | list | Create/update/delete/approve/import/payment/... phù hợp |
| unitEvidence | list | Spec hiện có hoặc task cần bổ sung |
| e2eEvidence | list | Test data-backed hiện có hoặc task cần bổ sung |
| status | enum | PASS, PARTIAL, FAIL, BLOCKED, NOT_APPLICABLE |
| blocker | string | Bắt buộc khi BLOCKED |
| completionTaskIds | list | Bắt buộc khi PARTIAL/FAIL/BLOCKED có gap cần xử lý |
| verifiedAt | date | Ngày xác minh gần nhất |

## 9. Scope Decision

| Trường | Kiểu | Quy tắc |
| --- | --- | --- |
| scopeDecisionId | string | Dạng SCOPE-NN |
| requirement | string | Nội dung dự kiến đưa vào báo cáo |
| sourceFiles | list | D01-D08 hoặc capability/evidence liên quan |
| classification | enum | REQUIRED_BY_D01_D08, REQUIRED_FOR_TRUTHFUL_EVIDENCE, OPTIONAL_SKIP |
| decision | enum | INCLUDE hoặc SKIP |
| rationale | string | Giải thích ngắn, kiểm chứng được |
| impactedSections | list | Slot/chapter/appendix bị ảnh hưởng |
| reviewedAt | date | Ngày rà soát |

## 10. Documentation Change

| Trường | Kiểu | Quy tắc |
| --- | --- | --- |
| changeId | string | Liên kết commit/issue hoặc ngày |
| trigger | enum | ROUTE, API, DATA, UI, TEST, SECURITY, NEW_FEATURE, REMOVED_FEATURE |
| capabilityIds | list | Capability bị ảnh hưởng |
| impactedArtifacts | list | UML, ERD, API, THESIS, screenshot, rubric |
| verificationCommands | list | Lệnh hoặc bước kiểm tra |
| recordedAt | date | Ngày cập nhật |
| reviewer | string | Người kiểm tra |

## State transitions

COMPLETE -> PARTIAL khi một phần contract/UI/test bị bỏ hoặc thay đổi chưa kiểm tra.

PARTIAL -> COMPLETE khi mọi artifact bắt buộc và bằng chứng hiện hành đã được cập nhật.

MISSING -> COMPLETE chỉ sau khi có implementation, contract, kiểm thử và mô tả báo cáo.

BLOCKED -> COMPLETE/PARTIAL sau khi môi trường hoặc quyền truy cập được khôi phục và đã xác minh.

DEFERRED chỉ chuyển sang các trạng thái khác khi có quyết định mở rộng phạm vi và task implementation tương ứng.

Admin `BLOCKED` -> `PASS/PARTIAL/FAIL` chỉ sau khi môi trường LuxeStay cô lập, fixture và credential đã sẵn sàng và route được chạy lại.

Admin `PASS` -> `PARTIAL/FAIL` khi API, permission hoặc mutation thay đổi mà chưa được xác minh lại.

Figure `NOT_CHECKED/FAIL` -> `PASS` chỉ sau khi PNG được nhúng, caption/alt text tồn tại và hình hiển thị đúng trong Microsoft Word cùng bản render QA.

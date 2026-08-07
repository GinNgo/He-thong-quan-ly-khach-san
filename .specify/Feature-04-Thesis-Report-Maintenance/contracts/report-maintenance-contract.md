# Contract: Report Maintenance

Contract này quy định dữ liệu tối thiểu để một thay đổi code có thể cập nhật báo cáo mà không bỏ sót artifact.

## 1. Capability record

Mỗi record phải cung cấp các trường:

    capabilityId, name, actor, scope, uiRoutes, apiEndpoints,
    services, dataObjects, status, evidenceIds, verifiedAt,
    reportSections, limitations

Validation:

1. capabilityId là duy nhất trong toàn bộ ma trận.
2. status chỉ nhận COMPLETE, PARTIAL, MISSING, BLOCKED hoặc DEFERRED.
3. status COMPLETE phải có source/API evidence và test, screenshot, log hoặc lý do kiểm thử rõ ràng.
4. status DEFERRED không được xuất hiện trong phần “chức năng đã cài đặt”; chỉ xuất hiện ở giới hạn/roadmap.
5. uiRoutes, apiEndpoints, services và dataObjects phải để [] khi không tồn tại, không được điền tên giả.
6. reportSections phải trỏ tới heading hiện có hoặc một task cập nhật heading.

## 2. Evidence record

Một evidence record có dạng logic:

    evidenceId:
    kind: SOURCE | MIGRATION | TEST | SCREENSHOT | LOG | API | UML | ERD | HISTORICAL
    pathOrCommand:
    claim:
    result:
    capturedAt:
    freshness: CURRENT | HISTORICAL | BLOCKED
    privacyReviewed: true | false
    notes:

Validation:

- Không dùng đường dẫn tuyệt đối của máy phát triển.
- HISTORICAL không được dùng làm bằng chứng duy nhất cho COMPLETE.
- BLOCKED phải có nguyên nhân và bước mở khóa.
- Screenshot phải ghi vai trò, route và dữ liệu demo.

## 3. Change impact record

Mỗi thay đổi source phải tạo danh sách:

    trigger -> capabilityIds -> impactedArtifacts -> verificationCommands

Bảng tối thiểu:

| Trigger | Artifact bắt buộc |
| --- | --- |
| ROUTE/permission | Use Case, traceability, API, Chapter 3-4 |
| API/service | Sequence/Activity, API_SPEC, Chapter 3-4, tests |
| Entity/migration | Class/ERD, data-model, Chapter 3-4 |
| UI/screen | screenshot, caption, Chapter 4, privacy review |
| TEST/config | Chapter 4 results, audit report, status |
| New/removed feature | FEATURE_SUMMARY, limitations, all affected diagrams |

## 4. Diagram contract

Mỗi diagram specification phải có diagramId, type, capabilityIds, sourcePath, sourceSyntax, caption, figureAssetIds, coverageDecision, purpose, description, analysis, conclusion và lastVerifiedAt.

Acceptance:

- Actor, state, endpoint, class và relationship phải tồn tại trong source tương ứng.
- Nhánh lỗi và trạng thái nghiệp vụ quan trọng phải được thể hiện hoặc ghi chú.
- Sơ đồ không được gắn một capability DEFERRED như luồng hoàn thành.
- Mọi capability đã triển khai phải có Use Case và quyết định REQUIRED/NOT_APPLICABLE cho Class, Sequence, Activity, ERD.
- Hình đưa vào Word/PDF phải là PNG độ phân giải cao, có caption, alt text và được tham chiếu trong văn bản.
- SVG chỉ là source bên ngoài DOCX; release contract cấm media relationship chỉ trỏ tới SVG.
- Sơ đồ quá rộng phải tách theo domain hoặc panel `(a)/(b)` có tên, không thu nhỏ chữ tới mức khó đọc.

## 5. Rubric response contract

Mỗi tiêu chí rubric phải có:

    criterionId, rubricName, weight, requirement, answer,
    sectionIds, evidenceIds, limitation, readiness

Cấu trúc câu trả lời vấn đáp:

1. Claim: hệ thống đã làm gì.
2. Evidence: chỉ ra mục báo cáo, source/API/class/test/screenshot.
3. Boundary: nói rõ chưa làm hoặc điều kiện chưa xác minh.
4. Value: nêu lợi ích/đóng góp đo được.
5. Next step: hướng phát triển nếu tiêu chí liên quan phần mở rộng.

## 6. Whole-document slot contract

Toàn văn phải kiểm tra tuần tự 13 slot:

1. Tờ bìa
2. Biên bản chấm/bảng điểm
3. Phiếu nhận xét GV phản biện
4. Biên bản chỉnh sửa nếu có
5. Phiếu nhận xét GV hướng dẫn
6. Lời cảm ơn nếu có
7. Lời cam đoan nếu có
8. Tóm tắt
9. Abstract nếu có
10. Mục lục
11. Nội dung khóa luận
12. Tài liệu tham khảo
13. Phụ lục nếu có

Một slot chưa có bản chính thức phải có status và owner; trước khi nộp không được để placeholder không giải thích.

## 7. Figure asset contract

Mỗi ảnh sơ đồ nhúng phải có:

    figureAssetId, diagramId, sourceSvgPath, embeddedPngPath,
    panel, caption, altText, pixelWidth, pixelHeight,
    wordStatus, renderStatus, referencedBy

Validation:

1. `embeddedPngPath` tồn tại, là PNG và được dùng bởi relationship trong DOCX.
2. `caption` là duy nhất; panel A/B dùng hậu tố `(a)/(b)` và tên riêng.
3. `altText` mô tả domain/actor/flow, không chỉ lặp tên file.
4. `wordStatus=PASS` chỉ sau khi mở/kiểm tra bằng Microsoft Word; `renderStatus=PASS` sau khi kiểm tra PNG từng trang.
5. Không còn placeholder 32x32, khung rỗng, broken-image icon hoặc nhãn `foreignObject` bị mất.

## 8. Admin verification contract

Mỗi route/chức năng Admin phải có:

    adminFunctionId, route, menuLabel, roles, permission, component,
    apiEndpoints, readChecks, mutationChecks, unitEvidence,
    e2eEvidence, status, blocker, completionTaskIds, verifiedAt

Validation:

1. `PASS` cần data load thành công và bằng chứng cho thao tác chính cùng authorization phù hợp.
2. Assertion chỉ kiểm tra `body visible`, component tồn tại hoặc route không crash không đủ để gắn `PASS`.
3. `BLOCKED` phải ghi đúng blocker môi trường và bước mở khóa; không quy lỗi môi trường thành lỗi chức năng.
4. `PARTIAL`, `FAIL` và gap có thể sửa phải có completion task với tiêu chí chấp nhận cùng unit/integration/E2E cần bổ sung.
5. Route không có permission guard phải được rà soát backend authorization; nếu không có căn cứ công khai rõ ràng thì ghi gap bảo mật.

## 9. Scope decision contract

Mỗi yêu cầu nội dung phải có classification:

    REQUIRED_BY_D01_D08 | REQUIRED_FOR_TRUTHFUL_EVIDENCE | OPTIONAL_SKIP

`OPTIONAL_SKIP` có thể bỏ khi rationale và impactedSections đã được ghi. Không được bỏ nội dung cần chứng minh chức năng thực tế chỉ vì D01-D08 không nêu chi tiết kỹ thuật.

## 10. Release contract

Bản release chỉ đạt READY khi:

- mọi capability trong THESIS có record và evidence;
- không còn mâu thuẫn chưa giải thích giữa source, test, API, ERD, UML và THESIS;
- rubric đã mapping hoặc có biên bản chờ bản export;
- 13 slot được kiểm tra theo thứ tự;
- mọi yêu cầu ngoài source đã qua scope gate D01-D08;
- 100% route Admin có verification record và mọi gap có completion task;
- privacy scan không phát hiện secret/PII/path cục bộ;
- DOCX chỉ nhúng PNG cho sơ đồ, không có media SVG-only hoặc placeholder;
- mọi hình có tên/caption/alt text/reference và diagram coverage đầy đủ;
- DOCX đã kiểm tra bằng Microsoft Word, DOCX/PDF đã render và kiểm tra từng trang;
- manifest ghi version, ngày xác minh và lệnh kiểm thử.

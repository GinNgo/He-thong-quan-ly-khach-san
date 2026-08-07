# KẾ HOẠCH HOÀN THIỆN VÀ DUY TRÌ BÁO CÁO KHÓA LUẬN

Ngày lập: 29/07/2026

Spec Kit feature: .specify/Feature-04-Thesis-Report-Maintenance/

## 1. Mục tiêu

Hoàn thiện docs/THESIS.md thành báo cáo khóa luận đúng mẫu trường, phản ánh đúng chức năng hệ thống LuxeStay tại thời điểm chốt bản và có thể cập nhật khi code thay đổi.

Kết quả cuối cần đạt:

- Toàn văn có đúng thứ tự 13 nhóm biểu mẫu.
- Nội dung năm chương dài khoảng 50-100 trang, không tính bìa và phụ lục.
- Use Case, Class Diagram, Sequence Diagram, Activity Diagram và ERD khớp source.
- Mỗi chức năng trong báo cáo có actor, route/UI, API, service, dữ liệu, test/evidence và trạng thái.
- Kết quả kiểm thử phân biệt hiện hành, lịch sử và bị chặn.
- Rubric được trả lời theo bằng chứng, không tự nhận vượt quá chức năng đã có.
- DOCX/PDF tuân thủ định dạng và được render kiểm tra từng trang.

## 2. Phạm vi hai công việc song song

### Công việc A - Báo cáo khóa luận

Feature 04 chỉ tạo/cập nhật tài liệu trong .specify/Feature-04-Thesis-Report-Maintenance và docs. Không sửa implementation runtime khi chưa có yêu cầu riêng.

### Công việc B - Notification/security và các thay đổi code khác

Các thay đổi này được giữ nguyên và tiếp tục độc lập. Khi công việc B hoàn tất một capability, công việc A chạy quy trình update trigger để rà soát API_SPEC, UML, ERD, FEATURE_SUMMARY, THESIS, test evidence và rubric. Không revert hoặc chỉnh sửa chồng lên code của công việc B.

## 3. Thứ tự nguồn sự thật

Khi có mâu thuẫn, dùng thứ tự:

1. Mã nguồn, migration và cấu hình hiện hành.
2. Kiểm thử vừa chạy trên worktree hiện hành.
3. docs/FEATURE_SUMMARY.md đã được cập nhật.
4. docs/API_SPEC.md, docs/ERD.md và docs/UML.md.
5. Báo cáo/audit có ngày trong quá khứ.
6. PROJECT_CONTEXT và FEATURE_ROADMAP.

THESIS.md là đầu ra cần đồng bộ, không phải bằng chứng cao nhất. Roadmap/mockup không được dùng để khẳng định đã triển khai.

## 4. Trạng thái chức năng

| Trạng thái | Khi sử dụng | Cách viết trong báo cáo |
| --- | --- | --- |
| COMPLETE | Contract và hành trình chính có evidence hiện hành | “Hệ thống đã triển khai...” kèm bằng chứng |
| PARTIAL | Có một phần nhưng thiếu UI/API/permission/test/nhánh vận hành | Nêu rõ phần có và phần thiếu |
| MISSING | Chưa có implementation/contract đáng tin cậy | Không đưa vào chức năng đã cài đặt |
| BLOCKED | Không thể xác minh do môi trường, quyền hoặc cấu hình | Nêu lệnh, lỗi, tác động và cách mở khóa |
| DEFERRED | Chủ động để ngoài phạm vi hiện tại | Đưa vào giới hạn/hướng phát triển |

Một chức năng chỉ được ghi COMPLETE sau khi đối chiếu lại source và evidence hiện hành.

## 5. Baseline chức năng cần phản ánh

Các nhóm sau thuộc phạm vi hiện có nhưng vẫn phải tái kiểm chứng trước bản nộp:

- Xác thực JWT, đăng ký và đăng nhập.
- RBAC/action mask và kiểm tra quyền ở backend.
- Public search, location normalization tiếng Việt và availability.
- Quản lý property, RoomType và phòng vật lý.
- Booking một RoomType với quantity lớn hơn một.
- Thanh toán simulator/VNPay callback theo contract hiện có.
- Hủy, refund ledger/idempotency và invoice.
- Assignment, check-in, services during stay, checkout, housekeeping.
- Multi-property context và quản lý subscription/feature liên quan.
- Property import và claim.
- Central customer support chat qua STOMP/SockJS đã xác thực, sau khi công việc notification/security được chốt và test.

Các nhóm không được mô tả là hoàn thành nếu chưa có implementation end-to-end:

- Mixed RoomType trong một booking/cart.
- Customer tự chọn add-on service trong checkout.
- Customer review submission/moderation/aggregation.
- Favorites.
- Full subscription lifecycle/history.
- Advanced financial reconciliation/reporting.

## 6. Kế hoạch thực hiện theo workstream

### WS-0 - Thu thập mẫu chính thức

Đầu vào:

- BanHanhQuyDinh-KLTN-CNTT.
- Mau-0-QuyTrinh-KLTN.
- Mau-1-Rubric-Project Mon Hoc.
- Mau-2-Rubric-KLTN-Edit.
- Mau-3-HuongDanLam-LuanVan.
- Mau-4-DeCuong-KLTN.
- Mau-5-TrinhBay-KLTN.
- Mau-6-Phieu_nhan_xet_HD_PB.

Thực hiện:

1. Export từng file từ Google Drive sang PDF hoặc DOCX.
2. Ghi tên file, ngày export và phiên bản.
3. Trích các quy định có tính bắt buộc: bố cục, số trang, heading, font, rubric code/trọng số, chữ ký.
4. Nếu mẫu chính thức khác THESIS_FORMAT_RULES.md, cập nhật quy tắc cục bộ và ghi lý do.

Đạt khi: Không còn mã rubric/trọng số/format bắt buộc nào phải suy đoán.

### WS-1 - Chốt baseline code và test

1. Lập danh sách frontend routes/menu/role.
2. Lập danh sách backend controllers/endpoints và permission.
3. Đối chiếu services, entities, migrations và business states.
4. Rà soát backend tests, frontend unit tests và Playwright E2E.
5. Chạy lại các test khả dụng; ghi ngày, lệnh, số pass/fail/blocked.
6. Chuyển số liệu test cũ thành HISTORICAL nếu không vừa tái lập.

Đầu ra: capability/evidence baseline và danh sách conflict cần sửa.

### WS-2 - Hoàn thiện ma trận truy vết

Mỗi dòng phải có:

    capability ID, actor, nghiệp vụ, route/UI, API, service,
    entity/table, test/evidence, trạng thái, ngày xác minh,
    UML/ERD, mục THESIS, tiêu chí rubric

Quy tắc:

- Không để “Có” chung chung nếu có thể ghi path/endpoint cụ thể.
- Route có UI nhưng không có API/mutation an toàn thì PARTIAL.
- API có nhưng actor không có route/UI theo phạm vi mô tả thì PARTIAL hoặc code-only.
- COMPLETE cần ít nhất source evidence và verification evidence.

### WS-3 - Chuẩn hóa sơ đồ

#### Use Case Diagram

Tối thiểu cần:

1. Use Case tổng quát theo actor Guest, Customer, Receptionist, Owner, Admin và Support.
2. Use Case public/customer: search, availability, booking, payment, cancellation/refund, invoice, support chat.
3. Use Case owner/operations: property, RoomType, room, assignment, check-in, services, checkout, housekeeping.
4. Use Case admin/platform: RBAC, subscription/features, import/claim, multi-property.

Kiểm chứng bằng app.routes.ts, controllers, permission codes và capability matrix.

#### Class Diagram

Tối thiểu cần:

1. Auth/RBAC: User, Role, Permission/AppFunction, user-property access và security services.
2. Property/inventory: Property, RoomType, Room và availability-related services.
3. Reservation/payment: Reservation, Payment, Refund/Ledger, Invoice và services.
4. Stay operations: assignment, check-in/out, service usage, housekeeping.
5. Subscription/import/claim hoặc chat nếu đưa vào phần đóng góp.

Chỉ vẽ lớp/entity/quan hệ có thật. Nếu diagram dùng lớp phân tích thay vì lớp code, phải ghi rõ mức trừu tượng.

#### Sequence Diagram

Ưu tiên:

1. JWT authentication và permission check.
2. Search location/property và availability.
3. Booking -> payment/callback -> confirmation.
4. Cancellation -> refund -> invoice/ledger update.
5. Assignment -> check-in -> service -> checkout -> housekeeping.
6. Import/deduplicate/claim.
7. Authenticated support chat, sau khi notification/security test ổn định.

Mỗi sequence cần nhánh lỗi/permission/not-found/conflict quan trọng.

#### Activity Diagram

Ưu tiên:

1. Đặt phòng và xử lý thất bại.
2. Hủy và hoàn tiền.
3. Vòng đời lưu trú từ assignment tới housekeeping.
4. Import/claim property.
5. Subscription/feature authorization nếu contract đủ rõ.

#### ERD

Đối chiếu JPA entity, migration và tenant key. Không dùng ERD mục tiêu như schema đã triển khai nếu migration chưa có.

Mỗi hình cần:

- Mục đích.
- Mô tả.
- Phân tích.
- Kết luận.
- Caption “Hình x.y”.
- Ít nhất một câu tham chiếu trong nội dung.

### WS-4 - Viết và sửa năm chương

#### Chương 1

- Cập nhật lý do, mục tiêu, đối tượng, phạm vi, phương pháp.
- Thêm bảng phạm vi hiện tại và quy ước status.
- Không mô tả feature dự kiến như kết quả.

#### Chương 2

- Chỉ giữ lý thuyết thực sự dùng trong hệ thống.
- Liên kết mỗi nội dung lý thuyết với quyết định triển khai ở Chapter 3-4.
- Bổ sung nguồn tham khảo có thể kiểm tra.

#### Chương 3

- Dùng ma trận actor/capability làm đầu vào.
- Đưa các UML/ERD đã xác minh, không copy sơ đồ cũ chưa review.
- Mô tả business states, boundary, permission và tenant isolation.

#### Chương 4

- Trình bày implementation theo hành trình nghiệp vụ, không chỉ liệt kê framework.
- Mỗi screenshot có route, role, chức năng và dữ liệu demo.
- Test result chia CURRENT/HISTORICAL/BLOCKED.
- Nêu conflict đã khắc phục và gap còn lại.

#### Chương 5

- Kết luận theo capability thực tế.
- Hạn chế phải khớp PARTIAL/MISSING/BLOCKED/DEFERRED.
- Hướng phát triển có task/contract dự kiến, không dùng câu “hệ thống đã hỗ trợ” cho phần chưa làm.

### WS-5 - Ảnh, bảng và bằng chứng kiểm thử

1. Chụp các màn hình đại diện, không cần lặp mọi biến thể nhỏ.
2. Dùng dữ liệu demo, che PII, token, secret và URL/path nhạy cảm.
3. Tạo bảng kết quả test có ngày, môi trường, lệnh, pass/fail/blocked và kết luận.
4. Không dùng log dài trong nội dung chính; chuyển phần chi tiết vào phụ lục.
5. Tạo danh mục hình và bảng khi mẫu yêu cầu.

### WS-6 - Rubric

1. Import rubric chính thức, giữ nguyên mã và trọng số.
2. Tạo một dòng cho 100% tiêu chí.
3. Viết câu trả lời 30-60 giây theo Claim -> Evidence -> Design reason -> Boundary -> Value.
4. Liên kết tới section, figure/table, source/test/screenshot.
5. Tự đánh giá READY/NEEDS_EVIDENCE/BLOCKED/NOT_APPLICABLE.
6. Luyện câu hỏi phản biện bằng docs/RUBRIC_RESPONSE_GUIDE.md.

### WS-7 - Ghép toàn văn và release QA

Thứ tự bắt buộc:

1. Tờ bìa.
2. Biên bản chấm/bảng điểm.
3. Phiếu nhận xét GV phản biện.
4. Biên bản chỉnh sửa nếu có.
5. Phiếu nhận xét GV hướng dẫn.
6. Lời cảm ơn nếu có.
7. Lời cam đoan nếu có.
8. Tóm tắt.
9. Abstract nếu có.
10. Mục lục.
11. Nội dung khóa luận.
12. Tài liệu tham khảo.
13. Phụ lục nếu có.

QA nội dung:

- Không còn conflict chưa giải thích.
- Không có claim không có evidence.
- Không có số test cũ được gọi là hiện hành.
- Không có tài liệu tham khảo không được trích.

QA định dạng:

- A4; lề trái 3 cm, phải/trên/dưới 2 cm.
- Times New Roman 13; giãn dòng 1,5; canh đều; thụt đầu dòng 1 cm.
- Heading, caption, số trang giữa chân trang và mục lục chính xác.
- Hình/bảng không bị cắt; caption không tách sai trang.
- Render DOCX/PDF thành PNG và kiểm tra toàn bộ trang.

## 7. Quy trình cập nhật khi code thay đổi

1. Ghi trigger và capabilityId.
2. Đọc diff, route/API/entity/test bị ảnh hưởng.
3. Cập nhật ERD/UML/API trước.
4. Cập nhật FEATURE_SUMMARY và traceability matrix.
5. Cập nhật THESIS, screenshot và rubric evidence.
6. Chạy test phù hợp; ghi CURRENT/HISTORICAL/BLOCKED.
7. Chạy consistency/privacy/format checks.
8. Ghi ngày và phạm vi vào changelog/report manifest.

Mục tiêu: một thay đổi điển hình được truy vết và cập nhật artifact trong tối đa 60 phút, chưa tính test dài.

## 8. Definition of Done

- Mọi chức năng được nêu có capabilityId và evidence.
- Mọi COMPLETE có source và verification evidence hiện hành.
- Use Case, Class, Sequence, Activity và ERD khớp implementation.
- 100% rubric criterion được mapping sau khi có bản export.
- 13 slot được kiểm tra và không còn placeholder không giải thích.
- Test, screenshot và privacy review có ngày.
- DOCX/PDF đạt render QA.

## 9. Đầu vào còn cần bổ sung

Tám file mẫu/rubric chính thức đã được export và checksum-verified tại `docs/thesis-assets/` ngày 28/07/2026; mã truy vết D01-D08, rubric D03/D04 và thứ tự 13 slot đã được cố định trong manifest. Phần còn thiếu trước bản nộp là các biểu mẫu có chữ ký/điểm thực tế (nếu nhà trường yêu cầu), bản PDF/DOCX render được để kiểm tra từng trang, cùng credential/fixture cho Admin E2E. Không còn để `TBD` cho mã tiêu chí hoặc trọng số đã có trong export.

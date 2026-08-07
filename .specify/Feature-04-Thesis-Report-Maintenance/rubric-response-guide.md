# Khung trả lời rubric khóa luận/môn học

Tài liệu này là khung chuẩn bị đã đối chiếu bản rubric export chính thức. Hai rubric không có mã riêng, vì vậy dùng trace ID nội bộ D03-C1..C7 và D04-C1..C7, đồng thời giữ STT và trọng số nguyên bản.

## 1. Bảng mapping đã chốt

| Mã tiêu chí | Trọng số | Yêu cầu | Câu trả lời 30 giây | Vị trí báo cáo | Bằng chứng | Giới hạn | Trạng thái |
| --- | --- | --- | --- | --- | --- | --- | --- |
| D03-C1..C7 | 10/10/10/15/15/10/30% | Nguyên văn trong `docs/thesis-assets/RUBRIC_MATRIX.md` | Claim + evidence + boundary | Chapter/section/figure | Source + test + screenshot | Nếu có | READY đến NEEDS_EVIDENCE |
| D04-C1..C7 | 10/40/10/5/10/5/20 | Nguyên văn trong `docs/thesis-assets/RUBRIC_MATRIX.md` | Claim + evidence + boundary | Chapter/section/figure | Source + test + screenshot | Nếu có | READY/PARTIAL/NEEDS_EVIDENCE |

Trạng thái dùng READY, NEEDS_EVIDENCE, BLOCKED hoặc NOT_APPLICABLE. Không đánh dấu READY khi chỉ có mô tả giao diện.

## 2. Công thức trả lời

Mỗi câu trả lời nên có năm phần:

1. Claim: hệ thống đang làm gì và cho ai.
2. Evidence: chỉ ra route/API/service/entity/test hoặc ảnh.
3. Design reason: vì sao chọn cách thiết kế đó.
4. Boundary: chức năng nào chưa hoàn tất hoặc điều kiện nào chưa xác minh.
5. Value/next step: lợi ích đo được hoặc kế hoạch tiếp theo.

Mẫu ngắn:

“Đối với [tiêu chí], hệ thống đã triển khai [chức năng] cho [actor]. Bằng chứng là [route/API/service/test/ảnh] tại [vị trí]. Thiết kế này giải quyết [vấn đề]. Phạm vi hiện tại chưa bao gồm [giới hạn], vì vậy phần đó được ghi là [PARTIAL/DEFERRED].”

## 3. Nhóm tiêu chí và bằng chứng

### Phân tích yêu cầu

- Trả lời: nêu bài toán, actor, phạm vi và quy tắc nghiệp vụ.
- Bằng chứng: Chapter 1, BUSINESS_REQUIREMENTS.md, capability matrix, use case.
- Kiểm tra: không lấy feature roadmap làm yêu cầu đã thực hiện.

### Thiết kế hệ thống

- Trả lời: giải thích kiến trúc frontend/backend/database, multi-property và RBAC.
- Bằng chứng: ARCHITECTURE.md, UML, ERD, API_SPEC, source package.
- Kiểm tra: actor/quyền trong sơ đồ phải khớp interceptor/permission thực tế.

### Cơ sở dữ liệu

- Trả lời: mô tả entity, khóa, quan hệ, tenant key, inventory và ràng buộc.
- Bằng chứng: migration, JPA entity, ERD, integration/service test.
- Kiểm tra: không vẽ bảng hoặc quan hệ chưa tồn tại chỉ vì roadmap.

### Cài đặt chức năng

- Trả lời: mô tả luồng end-to-end từ UI/route tới API/service/database.
- Bằng chứng: source, API contract, screenshot, test.
- Kiểm tra: phân biệt COMPLETE và PARTIAL; customer add-on/review/mixed RoomType phải nói đúng giới hạn nếu chưa có.

### Kiểm thử

- Trả lời: nêu loại test, lệnh, ngày chạy, kết quả và lỗi còn lại.
- Bằng chứng: test file, CI/log, BASELINE_TEST_REPORT.
- Kiểm tra: số liệu cũ ghi HISTORICAL; lỗi runner ghi BLOCKED thay vì PASS.

### Giao diện và trải nghiệm

- Trả lời: nêu role, mục tiêu màn hình, trạng thái loading/error/empty và khả năng sử dụng.
- Bằng chứng: screenshot sạch PII, route, component, manual checklist/E2E khi có.
- Kiểm tra: route có thể hiển thị nhưng mutation vẫn phải qua backend authorization.

### Đóng góp và hướng phát triển

- Trả lời: tổng hợp kết quả thực tế, giới hạn và tác động.
- Bằng chứng: capability summary, test matrix, Chapter 5.
- Kiểm tra: hướng phát triển không được diễn đạt như tính năng đã hoàn thành.

## 4. Câu hỏi phản biện dự kiến

| Câu hỏi | Khung trả lời trung thực |
| --- | --- |
| Chức năng nào là đóng góp chính? | Nêu 2-3 capability COMPLETE có evidence mạnh nhất và giá trị cụ thể. |
| Làm sao chống overbooking? | Giải thích inventory giao cắt thời gian, transaction/lock/constraint thực tế; chỉ khẳng định phần có trong source/test. |
| Vì sao có PARTIAL? | Nêu phần đã chạy, phần thiếu, bằng chứng và kế hoạch; không che giấu khoảng trống. |
| Quyền có an toàn không? | Chỉ ra JWT, PermissionInterceptor/action mask, tenant context và test endpoint; UI guard không được xem là lớp bảo mật duy nhất. |
| Tại sao số test trong báo cáo khác hiện tại? | Gắn nhãn HISTORICAL, chỉ ra ngày/lệnh; cập nhật lại CURRENT khi runner đã được sửa. |
| Có hỗ trợ đặt nhiều loại phòng không? | Nói đúng contract hiện tại: một booking gắn một RoomType; mixed RoomType là DEFERRED/MISSING nếu chưa triển khai. |
| Customer tự chọn add-on/review/favorite được không? | Chỉ khẳng định khi có route, API, service, dữ liệu và test end-to-end; nếu chưa thì trình bày là giới hạn. |

## 5. Checklist trước bảo vệ

- Mỗi tiêu chí có một câu trả lời dưới 60 giây.
- Mỗi claim có ít nhất một evidence trực tiếp và một vị trí trong báo cáo.
- Các trạng thái PARTIAL/MISSING/BLOCKED/DEFERRED được giải thích bằng nguyên nhân.
- Không đọc secret, token, PII hoặc đường dẫn máy cá nhân.
- Có thể mở nhanh source, test, sơ đồ và screenshot khi bị hỏi sâu.
- Đã mapping 14/14 dòng chính thức; chỉ cập nhật lại khi trường phát hành rubric mới hoặc có evidence mới làm thay đổi readiness.

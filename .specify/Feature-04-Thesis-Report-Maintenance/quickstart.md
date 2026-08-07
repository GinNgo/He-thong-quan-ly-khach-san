# Quickstart: Kiểm tra và cập nhật báo cáo

## Prerequisites

- Làm việc tại thư mục gốc repository.
- Có Java 21, Maven wrapper, Node/npm, Chromium của Playwright, Python runtime cho tài liệu và công cụ render DOCX/PDF.
- Có dữ liệu demo hoặc môi trường test; không dùng dữ liệu khách hàng thật trong ảnh.
- D01-D08 phải tồn tại trong docs/thesis-assets/templates/ và khớp registry checksum.
- Admin E2E cần backend LuxeStay ở cổng riêng, SQL Server/fixture và đủ biến `LUXESTAY_E2E_*`; không dùng dịch vụ khác đang chiếm cổng `8080`.

## Bước 1 - Xác định thay đổi

1. Đọc git diff và xác định trigger: route, API, data, UI, test, security, feature mới hoặc feature bị loại.
2. Tìm capability hiện có bằng capabilityId trong FEATURE_TRACEABILITY_MATRIX và FEATURE_SUMMARY.
3. Nếu chưa có capabilityId, tạo record trước khi viết prose.

## Bước 2 - Đối chiếu implementation

1. Kiểm tra route/menu tại frontend/src/app/app.routes.ts và các component liên quan.
2. Kiểm tra controller, service, entity và migration trong backend/src/main/.
3. Kiểm tra API_SPEC, ERD, UML có phản ánh cùng actor, endpoint, trạng thái và quan hệ hay không.
4. Gắn evidence source/API/migration và ngày xác minh.

## Bước 3 - Kiểm thử bằng chứng

Backend:

    .\mvnw.cmd clean test

Frontend unit (chỉ dùng khi cấu hình runner hiện hành đã sửa):

    npm --prefix frontend run test -- --watch=false

Frontend E2E (chỉ dùng khi Playwright config đã hoạt động):

    npx --prefix frontend playwright test

Nếu lệnh bị chặn hoặc lỗi cấu hình, giữ status BLOCKED, ghi nguyên nhân và không gọi kết quả là pass.

## Bước 4 - Xác minh Admin theo route

1. Inventory 100% route dưới `/admin` từ `frontend/src/app/app.routes.ts`.
2. Với mỗi route, ghi menu, role, permission guard/action mask, component và API data/mutation.
3. Chạy unit/build trước; sau đó chạy E2E data-backed trên backend LuxeStay cô lập.
4. Kiểm tra tối thiểu loading/data/empty/error, thao tác chính và authorization. Kiểm tra `body visible` chỉ được ghi là smoke.
5. Gắn `PASS`, `PARTIAL`, `FAIL`, `BLOCKED` hoặc `NOT_APPLICABLE`; mọi gap có completion task.

## Bước 5 - Cập nhật artifact theo thứ tự

1. Sửa docs/ERD.md và docs/UML.md khi mô hình/luồng thay đổi.
2. Sửa docs/API_SPEC.md và FEATURE_TRACEABILITY_MATRIX khi contract hoặc route thay đổi.
3. Sửa docs/FEATURE_SUMMARY.md với status mới và evidence.
4. Sửa docs/THESIS.md: mục phân tích, cài đặt, kiểm thử, giới hạn và hướng phát triển.
5. Cập nhật screenshot, caption, rubric mapping và changelog.

## Bước 6 - Raster và kiểm tra sơ đồ

1. Đối chiếu `docs/audit/THESIS_DIAGRAM_COVERAGE.md`; mọi capability phải có figure hoặc lý do `NOT_APPLICABLE`.
2. Raster SVG thành PNG độ phân giải cao bằng Chromium:

       node docs/tools/render_diagrams_png.js

3. Tách sơ đồ quá rộng theo domain hoặc panel `(a)/(b)` và đặt caption riêng.
4. Dựng DOCX; xác minh `word/media/` dùng PNG cho sơ đồ, không có quan hệ SVG-only hoặc placeholder 32x32.
5. Kiểm tra mọi hình có caption, alt text, tên duy nhất và tham chiếu trong nội dung.

## Bước 7 - Kiểm tra toàn văn

- Kiểm tra đủ 13 slot theo report-outline.md.
- Kiểm tra các chương 1-5, tóm tắt, tài liệu tham khảo và phụ lục.
- Kiểm tra mỗi hình/bảng được caption và tham chiếu.
- Mở bản REVIEW trong Microsoft Word để kiểm tra không có ảnh lỗi/khung rỗng; sau đó render DOCX/PDF và xem từng trang.
- Kiểm tra font, lề, giãn dòng, số trang và mục lục sau khi xuất Word/PDF.

## Bước 8 - Release evidence

Tạo một manifest gồm:

    version, verifiedAt, changedCapabilities, testCommands,
    currentResults, historicalResults, blockedResults,
    adminCoverage, diagramCoverage, wordVisualReview,
    privacyReview, reviewer

Chỉ phát hành khi release contract đạt đầy đủ. Nếu mẫu hành chính chưa ký, Admin E2E còn BLOCKED hoặc Word visual review chưa PASS, phát hành bản DRAFT/REVIEW chứ không gọi là bản nộp cuối.

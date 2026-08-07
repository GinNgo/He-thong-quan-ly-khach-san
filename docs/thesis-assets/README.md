# Thesis Assets

Thư mục này lưu các đầu vào cần để lắp toàn văn khóa luận, tách khỏi nội dung source trong docs/THESIS.md.

## Quy ước thư mục

- templates/: PDF/DOCX mẫu chính thức từ trường; không sửa trực tiếp bản gốc.
- front-matter/: lời cảm ơn, lời cam đoan, tóm tắt và abstract bản nháp.
- rubrics/: bản export rubric và phiên bản đã trích tiêu chí.
- diagrams/: SVG đã render từ Mermaid, tên khớp mã hình trong UML/ERD.
- screenshots/: ảnh evidence có mã capability, vai trò, ngày chụp và privacy review.
- ../export/: DOCX/PDF draft, review, final và release manifest.

## Quy tắc đặt tên

- Tên file dùng ASCII, ngày theo YYYY-MM-DD và version nếu có.
- Không lưu secret, token, dữ liệu khách thật hoặc đường dẫn máy cục bộ.
- Mỗi artifact phải ghi source, capturedAt/verifiedAt và status.
- Bản chính thức không sửa trực tiếp; tạo bản review riêng và ghi checksum trong manifest.

## Trạng thái

RECEIVED, DRAFT, REVIEW, VERIFIED, FINAL, BLOCKED, DEFERRED.

Nguồn sự thật về chức năng vẫn là source/migration và test hiện hành; assets chỉ là bằng chứng/đầu vào xuất bản.

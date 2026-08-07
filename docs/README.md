# Tài liệu dự án LuxeStay

Thư mục chứa tài liệu của hệ thống quản lý khách sạn và đặt phòng trực tuyến LuxeStay. Tài liệu được phân loại theo mục đích sử dụng; các báo cáo theo ngày được giữ nguyên làm hồ sơ lịch sử.

## 1. Tiểu luận và tài liệu tổng hợp

- [THESIS.md](THESIS.md): Báo cáo tiểu luận chính, tổ chức theo năm chương.
- [THESIS_FORMAT_RULES.md](THESIS_FORMAT_RULES.md): Quy định ngôn ngữ, định dạng, bảng, hình, UML và cấu trúc tiểu luận.
- [THESIS_REPORT_PLAN.md](THESIS_REPORT_PLAN.md): Kế hoạch Spec Kit để hoàn thiện, kiểm chứng và duy trì toàn văn khóa luận.
- [RUBRIC_RESPONSE_GUIDE.md](RUBRIC_RESPONSE_GUIDE.md): Khung mapping bằng chứng và luyện trả lời rubric môn học/khóa luận.
- [FEATURE_SUMMARY.md](FEATURE_SUMMARY.md): Trạng thái chức năng, giới hạn và kết quả kiểm thử gần nhất.
- [audit/THESIS_DIAGRAM_QA.md](audit/THESIS_DIAGRAM_QA.md): Kết quả render và layout QA cho 24 sơ đồ.
- [audit/THESIS_TEMPLATE_EVIDENCE.md](audit/THESIS_TEMPLATE_EVIDENCE.md): Yêu cầu trích từ D01-D08 và quyết định áp dụng vào toàn văn.
- [audit/RUBRIC_GAP_REPORT.md](audit/RUBRIC_GAP_REPORT.md): Gap rubric và điều kiện mở khóa evidence.
- [audit/THESIS_PRIVACY_QA.md](audit/THESIS_PRIVACY_QA.md): Privacy scan sơ bộ cho tài liệu và screenshot.
- [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md): Bối cảnh và yêu cầu ban đầu; có thể chứa chức năng dự kiến chưa được triển khai.
- [LOGBOOK.md](LOGBOOK.md): Nhật ký tiến độ khóa luận.
- [CHANGELOG.md](CHANGELOG.md): Lịch sử thay đổi dự án.

## 2. Phân tích và thiết kế

- [BUSINESS_REQUIREMENTS.md](BUSINESS_REQUIREMENTS.md): Yêu cầu nghiệp vụ và tác nhân hệ thống.
- [ARCHITECTURE.md](ARCHITECTURE.md): Mô tả cấu trúc backend, frontend, dữ liệu và tích hợp.
- [authorization_architecture.md](authorization_architecture.md): Kiến trúc xác thực, phân quyền và Action Mask.
- [ERD.md](ERD.md): Thiết kế thực thể và quan hệ dữ liệu.
- [UML.md](UML.md): Use Case, Activity, Sequence và Class Diagram.
- [DESIGN.md](DESIGN.md): Định hướng giao diện và hệ thống trình bày.
- [FRONTEND_STANDARDS.md](FRONTEND_STANDARDS.md): Quy chuẩn giao diện và thành phần frontend.
- [SHARED_COMPONENTS_REQUIREMENTS.md](SHARED_COMPONENTS_REQUIREMENTS.md): Yêu cầu đối với thành phần dùng chung.

## 3. Đặc tả kỹ thuật và lộ trình

- [API_SPEC.md](API_SPEC.md): Đặc tả REST API theo phân hệ.
- [FEATURE_ROADMAP.md](FEATURE_ROADMAP.md): Lộ trình phát triển; không dùng làm bằng chứng chức năng đã hoàn thành.
- [DEVELOPMENT_RULES.md](DEVELOPMENT_RULES.md): Quy tắc phát triển backend, frontend, cơ sở dữ liệu và tài liệu.

## 4. Báo cáo lịch sử ngày 15/07/2026

Các tài liệu trong nhóm này phản ánh trạng thái tại thời điểm lập báo cáo. Khi có mâu thuẫn, ưu tiên mã nguồn, kiểm thử hiện hành và [FEATURE_SUMMARY.md](FEATURE_SUMMARY.md).

- [ADMIN_CORE_REPORT_2026-07-15.md](ADMIN_CORE_REPORT_2026-07-15.md): Role, permission, RoomType và phòng vật lý.
- [ADMIN_ROUTE_MENU_MATRIX_2026-07-15.md](ADMIN_ROUTE_MENU_MATRIX_2026-07-15.md): Ma trận route, menu và mã chức năng quản trị.
- [HOME_SEARCH_REPORT_2026-07-15.md](HOME_SEARCH_REPORT_2026-07-15.md): Home Search và Location Autocomplete.
- [IMPLEMENTATION_REPORT_2026-07-15.md](IMPLEMENTATION_REPORT_2026-07-15.md): Unicode, tìm kiếm và tồn phòng.
- [NATIONWIDE_DEMO_REPORT_2026-07-15.md](NATIONWIDE_DEMO_REPORT_2026-07-15.md): Seeder toàn quốc và Owner Portal.
- [PUBLIC_CUSTOMER_QUALITY_REPORT_2026-07-15.md](PUBLIC_CUSTOMER_QUALITY_REPORT_2026-07-15.md): Chất lượng phân hệ Public/Customer.
- [SEARCH_RESULT_BOOKING_REPORT_2026-07-15.md](SEARCH_RESULT_BOOKING_REPORT_2026-07-15.md): Kết quả tìm kiếm và luồng đặt phòng.

## 5. Tài nguyên và giấy phép

- [ASSET_LICENSES.md](ASSET_LICENSES.md): Nguồn và giấy phép ảnh demo.
- [screenshots/](screenshots/): Ảnh minh họa dùng trong báo cáo.
- [thesis-assets/](thesis-assets/): Mẫu, front matter, rubric, diagram SVG và manifest toàn văn.
- [thesis-assets/OFFICIAL_TEMPLATE_SOURCES.md](thesis-assets/OFFICIAL_TEMPLATE_SOURCES.md): Registry, checksum và nguồn Drive D01-D08.
- [thesis-assets/RUBRIC_MATRIX.md](thesis-assets/RUBRIC_MATRIX.md): Mapping 14/14 tiêu chí D03/D04 tới câu trả lời và evidence.
- [thesis-assets/diagrams/](thesis-assets/diagrams/): 24 SVG sơ đồ đã render, mã khớp UML/ERD.
- [export/RELEASE_MANIFEST.md](export/RELEASE_MANIFEST.md): Manifest bản draft/release gate.

## 6. Thứ tự ưu tiên khi đối chiếu

1. Mã nguồn và migration hiện hành.
2. Kiểm thử tự động hiện hành.
3. [FEATURE_SUMMARY.md](FEATURE_SUMMARY.md).
4. [API_SPEC.md](API_SPEC.md), [ERD.md](ERD.md) và [UML.md](UML.md).
5. Báo cáo lịch sử.
6. [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) và [FEATURE_ROADMAP.md](FEATURE_ROADMAP.md).

Không xóa báo cáo lịch sử. Nội dung tiểu luận chỉ được khẳng định là đã hoàn thành khi có bằng chứng từ mã nguồn hoặc kiểm thử.

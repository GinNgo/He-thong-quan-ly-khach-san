# Scope gate báo cáo theo D01-D08

**Ngày rà soát:** 2026-07-29  
**Mục tiêu:** chỉ giữ nội dung cần cho mẫu chính thức hoặc cần để chứng minh chức năng thực tế; yêu cầu trang trí/ngoài phạm vi được phép bỏ có lý do.

| ID | Yêu cầu | Nguồn | Phân loại | Quyết định | Lý do |
| --- | --- | --- | --- | --- | --- |
| SCOPE-01 | Tờ bìa, thứ tự 13 slot, chương 1-5, tài liệu tham khảo, phụ lục | D01, D02, D05, D06, D07 | `REQUIRED_BY_D01_D08` | INCLUDE | Cấu trúc và thứ tự toàn văn bắt buộc. |
| SCOPE-02 | Tóm tắt, lời cảm ơn, lời cam đoan | D05, D07 | `REQUIRED_BY_D01_D08` | INCLUDE | Có trong hướng dẫn; slot “nếu có” phải ghi trạng thái. |
| SCOPE-03 | Abstract tiếng Anh | D07 hiện hành | `REQUIRED_BY_D01_D08` | SKIP/`NOT_APPLICABLE` | D07 không bắt buộc; chỉ thêm khi giảng viên yêu cầu. |
| SCOPE-04 | Phiếu/bảng điểm/nhận xét/chỉnh sửa có chữ ký | D01, D02, D08 | `REQUIRED_BY_D01_D08` | INCLUDE AS SLOT | Bản REVIEW giữ slot trạng thái; FINAL thay bằng mẫu ký thật. |
| SCOPE-05 | Use Case, Class, Sequence, Activity, ERD | D05, D06, D07 + bằng chứng thực tế | `REQUIRED_FOR_TRUTHFUL_EVIDENCE` | INCLUDE | Cần chứng minh phân tích/thiết kế chức năng đã có. |
| SCOPE-06 | PNG Word-compatible, caption, alt text, visual QA | Bằng chứng trung thực + D07 presentation | `REQUIRED_FOR_TRUTHFUL_EVIDENCE` | INCLUDE | Hình SVG-only đã gây khung rỗng trong Word. |
| SCOPE-07 | Coverage sơ đồ cho capability và lý do NOT_APPLICABLE | Bằng chứng trung thực | `REQUIRED_FOR_TRUTHFUL_EVIDENCE` | INCLUDE | Ngăn bộ sơ đồ đủ loại nhưng bỏ sót chức năng. |
| SCOPE-08 | Ma trận Admin route/permission/API/mutation/test | D03, D04 + bằng chứng cài đặt | `REQUIRED_FOR_TRUTHFUL_EVIDENCE` | INCLUDE | Không thể kết luận chức năng Admin chỉ từ route shell. |
| SCOPE-09 | Rubric D03/D04 và câu trả lời vấn đáp | D03, D04 | `REQUIRED_BY_D01_D08` | INCLUDE | Đã tải và mapping 14/14 tiêu chí. |
| SCOPE-10 | BOOK-02 multi-RoomType/cart | Source/contract hiện hành | `OPTIONAL_SKIP` | SKIP AS IMPLEMENTED | DEFERRED; chỉ ghi giới hạn/roadmap. |
| SCOPE-11 | REVIEW-01 review/favorites và báo cáo nâng cao chưa có contract | Source/contract hiện hành | `OPTIONAL_SKIP` | SKIP AS IMPLEMENTED | Không được vẽ hoặc mô tả như complete; giữ phần hướng phát triển nếu rubric cần. |
| SCOPE-12 | Nội dung trang trí, hình minh họa không gắn capability/evidence | Không có trong D01-D08 | `OPTIONAL_SKIP` | SKIP | Không phục vụ rubric hoặc tính trung thực; tránh làm báo cáo phình to. |

## Quy tắc áp dụng

- `OPTIONAL_SKIP` không có nghĩa là xóa dấu vết: nếu liên quan giới hạn, ghi ngắn trong Chương 5.
- Không bỏ artifact cần để chứng minh capability hiện hành chỉ vì D01-D08 không gọi tên kỹ thuật đó.
- Khi source hoặc rubric đổi, cập nhật bảng này trước khi sửa DOCX.

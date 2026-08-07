# Manifest toàn văn điện tử

Ngày cập nhật: 2026-07-28

| Slot | Nhóm biểu mẫu/nội dung | Bắt buộc | Nguồn hiện có | Trạng thái | Owner/Việc cần làm |
| ---: | --- | --- | --- | --- | --- |
| 1 | Tờ bìa khóa luận tốt nghiệp | Có | D07 template; chưa có bản điền thông tin | NEEDS_EVIDENCE | Điền đề tài/sinh viên/GVHD/lớp/năm và kiểm tra với khoa |
| 2 | Biên bản chấm hoặc bảng điểm | Có | Chưa phát sinh | BLOCKED | Bổ sung bản chính thức sau chấm |
| 3 | Phiếu nhận xét dành cho GV phản biện | Có | D08 template; chưa có bản có chữ ký | NEEDS_EVIDENCE | Điền thông tin và bổ sung bản có chữ ký |
| 4 | Biên bản chỉnh sửa | Nếu có | Chưa phát sinh | DEFERRED | Chỉ thêm khi có yêu cầu chỉnh sửa |
| 5 | Phiếu nhận xét dành cho GV hướng dẫn | Có | D08 template; chưa có bản có chữ ký | NEEDS_EVIDENCE | Điền thông tin và bổ sung bản có chữ ký |
| 6 | Lời cảm ơn | Nếu có | front-matter/LOI_CAM_ON.md | DRAFT | Rà giọng văn và tên đơn vị trước REVIEW |
| 7 | Lời cam đoan | Nếu có/khuyến nghị | front-matter/LOI_CAM_DOAN.md | REVIEW | Đối chiếu câu chữ với quy định D01 trước khi ký |
| 8 | Tóm tắt | Có | front-matter/TOM_TAT.md | REVIEW | Chốt lại số liệu test và giới hạn trước khi dịch Abstract |
| 9 | Abstract | Nếu có | D07 không yêu cầu Abstract tiếng Anh | NOT_APPLICABLE | Chỉ tạo nếu khoa yêu cầu ở phiên bản mẫu mới |
| 10 | Mục lục | Có | Heading trong THESIS.md; D07 yêu cầu | DRAFT | Sinh tự động sau khi chốt DOCX |
| 11 | Nội dung khóa luận | Có | docs/THESIS.md; D07 yêu cầu phần Mở đầu/Nội dung/Kết luận | REVIEW | Hoàn thiện US1-US2, hình, bảng và test evidence |
| 12 | Tài liệu tham khảo | Có | Cuối docs/THESIS.md | REVIEW | Rà citation và chuẩn hóa style |
| 13 | Phụ lục | Nếu có | Audit/test/UML/API assets; D07 cho phép | DRAFT | Chọn evidence chi tiết, rubric matrix và screenshot cần thiết |

## Release rule

- Không bỏ slot có trạng thái BLOCKED khỏi checklist.
- Slot điều kiện chỉ loại khỏi bản cuối khi quy định/mẫu cho phép.
- Bản FINAL không chứa placeholder không giải thích.
- Thứ tự slot không thay đổi khi merge DOCX/PDF.

## Precedence note

D07 is an older institutional presentation template and places supervisor/reviewer pages and the detailed outline near the front. For this electronic submission, the explicit 13-slot order supplied by the user is the controlling order. D07 remains the authority for typography, page geometry, chapter structure and content constraints; the difference is recorded rather than silently mixed.

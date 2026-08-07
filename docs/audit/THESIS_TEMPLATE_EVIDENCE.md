# Evidence đối chiếu mẫu khóa luận - 2026-07-28

## Nguồn đã nhận

D01-D08 được export từ thư mục Drive người dùng cung cấp, lưu trong `docs/thesis-assets/templates/`. D03/D04 có thêm bản copy trong `docs/thesis-assets/rubrics/` để lập ma trận rubric.

## Yêu cầu đã trích

| Nguồn | Yêu cầu dùng cho báo cáo |
| --- | --- |
| D01 | Xác nhận bộ mẫu chính thức gồm quy định, quy trình, hai rubric, hướng dẫn, đề cương, quy cách trình bày và phiếu nhận xét. |
| D02 | Quy trình gồm đăng ký/nhận đề tài, đề cương trong hai tuần, báo cáo tiến độ, nộp báo cáo, phản biện, bảo vệ, chỉnh sửa và nộp lại. |
| D03 | Rubric môn học có 7 tiêu chí, trọng số 10%, 10%, 10%, 15%, 15%, 10%, 30%; xem ma trận để giữ nguyên mô tả từng mức. |
| D04 | Rubric khóa luận có 7 tiêu chí, thang điểm 10, 40, 10, 5, 10, 5, 20; xem ma trận để giữ nguyên mô tả. |
| D05 | Báo cáo phải trả lời “Làm gì? Làm như thế nào? Kết quả ra sao?”, có đề cương được duyệt, theo dõi tiến độ, nhận xét GVHD/GVPB và chuẩn bị bảo vệ. |
| D06 | Đề cương gồm mở đầu, mục tiêu, phương pháp, đối tượng/phạm vi, công trình liên quan, kết quả dự kiến, nội dung theo chương, kết luận, tài liệu tham khảo và kế hoạch thực hiện. |
| D07 | A4; Times New Roman; line 1.5; lề trái 3 cm, phải/trên/dưới 2 cm; số trang giữa chân trang; nội dung chính 50-100 trang; yêu cầu UML/model, CD tree và checklist nộp. |
| D08 | Có phiếu nhận xét riêng cho GVHD và GVPB, gồm nội dung/khối lượng, ưu điểm, khuyết điểm, đề nghị bảo vệ, xếp loại, điểm và chữ ký. |

## Quyết định áp dụng

- Thứ tự toàn văn điện tử 13 slot do người dùng cung cấp là thứ tự merge bắt buộc.
- D07 là nguồn chính cho format, heading, caption, page geometry và nội dung chương.
- D02/D05 là nguồn chính cho quy trình thực hiện và thông điệp bảo vệ.
- D03/D04 là nguồn chính cho rubric; không tạo mã chính thức khi file không có mã.
- D08 chỉ là mẫu phiếu; chưa có bản đã ký nên slot 3/5 vẫn `NEEDS_EVIDENCE`.
- D07 không yêu cầu Abstract tiếng Anh; slot 9 được ghi `NOT_APPLICABLE` cho bản hiện tại.

## Template conflict recorded

D07 prose rule says top margin 2 cm, while the retained DOCX section audit reports a top margin of 0.39 in (approximately 1 cm). The report implementation follows the explicit D07 prose/table rule and `THESIS_FORMAT_RULES.md` (top 2 cm), because the section XML appears to be an example-document artifact rather than a reliable statement of the written rule. This deviation is named and must be checked again against the latest faculty template before FINAL.

## QA nguồn

- Structural extraction: PASS, 8 file template và 2 file rubric copy.
- SHA-256: ghi trong `docs/thesis-assets/OFFICIAL_TEMPLATE_SOURCES.md`.
- Render bằng `render_docx.py`: BLOCKED vì máy không có LibreOffice/soffice.
- Word COM conversion fallback: BLOCKED do automation timeout; không dùng kết quả này làm bằng chứng layout.

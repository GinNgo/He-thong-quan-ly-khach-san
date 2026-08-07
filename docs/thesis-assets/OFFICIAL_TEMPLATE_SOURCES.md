# Nguồn biểu mẫu chính thức

Ngày kiểm tra và export Drive: 2026-07-28

Nguồn thư mục do người dùng cung cấp: https://drive.google.com/drive/u/0/folders/1XEac4cy0L7ZdA0Hbc6frpfI4-kPPMHmI

Các file đã export bằng phiên Chrome được người dùng đăng nhập. Tên file trong repository được chuẩn hóa ASCII, còn tên nguồn được giữ nguyên để truy xuất.

| Mã | Tên nguồn | Dùng cho | Repository file | Export/nguồn | Trạng thái |
| --- | --- | --- | --- | --- | --- |
| D01 | BanHanhQuyDinh-KLTN-CNTT | Quy định khóa luận | templates/D01_BanHanhQuyDinh_KLTN_CNTT_2026-07-28.docx | Drive folder, 2026-07-28 | CURRENT |
| D02 | Mau-0-QuyTrinh-KLTN | Quy trình nộp/chấm | templates/D02_Mau_0_QuyTrinh_KLTN_2026-07-28.docx | Drive folder, 2026-07-28 | CURRENT |
| D03 | Mau-1-Rubric-Project Mon Hoc | Rubric môn học | templates/D03_Mau_1_Rubric_Project_Mon_Hoc_2026-07-28.docx; rubrics/D03_Rubric_Project_Mon_Hoc_2026-07-28.docx | Drive folder, 2026-07-28 | CURRENT |
| D04 | Mau-2-Rubric-KLTN-Edit | Rubric khóa luận | templates/D04_Mau_2_Rubric_KLTN_Edit_2026-07-28.docx; rubrics/D04_Rubric_KLTN_2026-07-28.docx | Drive folder, 2026-07-28 | CURRENT |
| D05 | Mau-3-HuongDanLam-LuanVan | Hướng dẫn viết | templates/D05_Mau_3_HuongDanLam_LuanVan_2026-07-28.docx | Drive folder, 2026-07-28 | CURRENT |
| D06 | Mau-4-DeCuong-KLTN | Đề cương | templates/D06_Mau_4_DeCuong_KLTN_2026-07-28.docx | Drive folder, 2026-07-28 | CURRENT |
| D07 | Mau-5-TrinhBay-KLTN | Quy cách trình bày | templates/D07_Mau_5_TrinhBay_KLTN_2026-07-28.docx | Drive folder, 2026-07-28 | CURRENT |
| D08 | Mau-6-Phieu_nhan_xet_HD_PB | Phiếu nhận xét | templates/D08_Mau_6_Phieu_Nhan_Xet_HD_PB_2026-07-28.docx | Drive folder, 2026-07-28 | CURRENT |

## Quy trình tiếp nhận export

1. Đặt PDF/DOCX vào docs/thesis-assets/templates/ với tên ASCII và ngày export.
2. Ghi checksum, người export và nguồn Drive vào bảng dưới đây.
3. Đọc toàn bộ file và trích các yêu cầu bắt buộc sang THESIS_FORMAT_RULES.md hoặc rubric matrix.
4. Không suy đoán mã tiêu chí/trọng số từ tên file.

## Export registry

| Mã | Repository file | SHA-256 | ExportedAt | ExportedBy | ReviewedAt |
| --- | --- | --- | --- | --- | --- |
| D01 | templates/D01_BanHanhQuyDinh_KLTN_CNTT_2026-07-28.docx | `033CBCA30FF923E03FF17FD8FBFAB85872EBE69A68DACA72DD9B90CC19C84AD8` | 2026-07-28 | Codex Chrome session | 2026-07-28 |
| D02 | templates/D02_Mau_0_QuyTrinh_KLTN_2026-07-28.docx | `3347362741009563840FE6BF73BD8550B846C950DD53EC36D0E1CA00A98AE847` | 2026-07-28 | Codex Chrome session | 2026-07-28 |
| D03 | rubrics/D03_Rubric_Project_Mon_Hoc_2026-07-28.docx | `0BDD7FE52EE3D85CEDE13F2EEC11329DB00E242979C8C8466498533802117796` | 2026-07-28 | Codex Chrome session | 2026-07-28 |
| D04 | rubrics/D04_Rubric_KLTN_2026-07-28.docx | `004BC63B310722D5898F788966A3364453BA702129749B873C74261D078A8FDD` | 2026-07-28 | Codex Chrome session | 2026-07-28 |
| D05 | templates/D05_Mau_3_HuongDanLam_LuanVan_2026-07-28.docx | `D55B2192E9851A08B2C6531E97C7E3C558481C6B317208683558E8B4E8967375` | 2026-07-28 | Codex Chrome session | 2026-07-28 |
| D06 | templates/D06_Mau_4_DeCuong_KLTN_2026-07-28.docx | `B796846189545698CA331A741A62E158325C54419CCD59D0749910168846B82A` | 2026-07-28 | Codex Chrome session | 2026-07-28 |
| D07 | templates/D07_Mau_5_TrinhBay_KLTN_2026-07-28.docx | `4F66B9D2C1F19E0975C88D7415BBC25CA38622DDD4B19A8DCF96C9C4E0E17ADD` | 2026-07-28 | Codex Chrome session | 2026-07-28 |
| D08 | templates/D08_Mau_6_Phieu_Nhan_Xet_HD_PB_2026-07-28.docx | `56944C37749A813F43CA2F6DE55B3942A3F18C91982783CE615D04CC5F468633` | 2026-07-28 | Codex Chrome session | 2026-07-28 |

## Render note

Structural extraction of all eight DOCX files completed on 2026-07-28. The packaged renderer could not run because LibreOffice/soffice is not installed. A Word COM conversion attempt also timed out, so visual render QA of the source templates remains `BLOCKED`; this does not invalidate the exported-file checksums or rubric text extraction.

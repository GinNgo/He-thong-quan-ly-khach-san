# Thesis privacy QA - 2026-07-29

## Draft artifact refresh (2026-07-29)

- `docs/export/LuxeStay_KhoaLuan_DRAFT.docx` was rebuilt with 24 appendix diagrams as PNG; `docs/export/LuxeStay_KhoaLuan_DRAFT_scrubbed.docx` was generated with the metadata scrubber for comparison.
- The thesis draft has empty `creator`/`lastModifiedBy`, no custom properties, comments or tracked changes, no local paths, and no secret/key/JWT patterns. The accessibility audit reports 0 findings, including alt text/title for all 47 inline images.
- The DOCX package contains no SVG media; source SVG remains outside the package in `docs/thesis-assets/diagrams/`.
- The thesis draft contains no email address; the rubric guide contains no email address. Screenshot alt text is descriptive and the accessibility audit reports 0 findings.
- Five new/current screenshots were reviewed. Public desktop/mobile are `CURRENT/PASS`; search, admin roles and admin rooms are `CURRENT/BLOCKED` because the browser session received `Failed to fetch` from the data service. The blocked images are retained only as honest error-state evidence.
- A scan of source text reports self-referential email/path examples only inside this QA report; those strings are documentation of the scan rules, not leaked artifact data.

The final release scan is still required after REVIEW/FINAL DOCX and PDF export; no final artifact is claimed here.

## Final DOCX scan (2026-07-29)

- `docs/export/LuxeStay_KhoaLuan_FINAL.docx` and `docs/export/LuxeStay_HuongDan_TraLoi_Rubric_FINAL.docx` were created with the metadata scrubber.
- Both packages pass ZIP integrity and can be opened by `python-docx`.
- Accessibility audit reports 0 high, medium or low findings for both FINAL DOCX files.
- Package text scan reports 0 local machine paths, private keys, JWTs, secret assignments and email addresses.
- The thesis FINAL contains 47 inline images with 47 descriptions/titles, 38 PNG media items and 0 SVG media.
- No PDF was generated because the renderer cannot start without `soffice`; PDF privacy status remains `NOT_AVAILABLE/RENDER_BLOCKED`.

## Phạm vi đã quét

- Markdown/JSON/text trong `docs/`.
- 10 ảnh trong `docs/screenshots/`.
- 24 SVG diagram trong `docs/thesis-assets/diagrams/`.
- 10 official source DOCX in `docs/thesis-assets/templates/` and `docs/thesis-assets/rubrics/`.
- Draft DOCX exists and was scanned; REVIEW/FINAL DOCX/PDF are not released and require another scan at T043/T058.

## Static scan

| Nhóm | Kết quả | Kết luận |
| --- | --- | --- |
| Đường dẫn máy cá nhân (`C:\Users`, `/home`, `file://`, `vscode://`) | Không tìm thấy trong artifact tài liệu | PASS |
| Private key, AWS/OpenAI/Google key pattern, JWT hoàn chỉnh | Không tìm thấy | PASS |
| Password/secret gán trực tiếp | Không tìm thấy giá trị; chỉ có tên biến `DEMO_ACCOUNT_PASSWORD` trong mô tả cấu hình | PASS |
| Connection string/DB credential | Không tìm thấy | PASS |
| Draft DOCX package media | No SVG-only relationship; no local path/credential pattern | PASS_STRUCTURAL |

## Official template DOCX scan

| Nhóm | Kết quả | Kết luận |
| --- | --- | --- |
| Email, private key, local path, credential | Không tìm thấy trong D01-D08 và bản rubric copy | PASS |
| Core author/lastModifiedBy metadata | Không có giá trị tác giả trong các DOCX export | PASS |
| D07 sample MSSV `1511001`, `1511004` | Có trong ví dụ bìa của template | Chỉ là dữ liệu mẫu; không được sao chép vào bản LuxeStay |
| Mẫu tên/đơn vị cũ trong D07/D08 | Có thể xuất hiện ở phần ví dụ/chữ ký | Chỉ dùng làm reference; phải thay bằng thông tin thật hoặc placeholder được duyệt |

## Screenshot review

| Nhóm ảnh | Kết quả privacy | Ghi chú |
| --- | --- | --- |
| Public home/search/room selection | PASS_CANDIDATE | Chỉ có dữ liệu demo, ngày và địa danh; không có token/path/khách thật |
| Admin roles/rooms | PASS_CANDIDATE | Hiển thị role, phòng và tài khoản hệ thống; không có email/số điện thoại/credential |
| Profile menu desktop/mobile | PASS_CANDIDATE | Có `owner@example.com`, được xem là email demo; khi chụp lại CURRENT nên dùng nhãn rõ `demo` hoặc che email |
| Loading-before | PASS_CANDIDATE | Không có dữ liệu cá nhân; chỉ dùng ở phần audit, không dùng làm bằng chứng chức năng hoàn tất |

## Rủi ro không thuộc privacy nhưng ảnh hưởng bản nộp

- Ảnh admin còn nhận diện `AURORA`, không đồng nhất LuxeStay; chỉ dùng làm evidence lịch sử hoặc chụp lại.
- Tất cả screenshot hiện là HISTORICAL, chưa phải ảnh CURRENT của bản phát hành.
- Ảnh public chứa dữ liệu địa chỉ/cơ sở demo; caption phải nói rõ dữ liệu mô phỏng.

## Release gate

T056 đã hoàn thành cho các artifact hiện có: source/template, screenshot và hai FINAL DOCX đều qua privacy scan. PDF không tồn tại do renderer bị chặn, vì vậy phải quét bổ sung nếu sau này xuất PDF. Các MSSV trong D07 được phân loại là dữ liệu ví dụ, không nằm trong nội dung LuxeStay FINAL.

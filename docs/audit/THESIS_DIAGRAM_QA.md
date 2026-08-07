# Diagram render QA - 2026-07-29

## Phạm vi

- Source: `docs/UML.md` và `docs/ERD.md`.
- Output: `docs/thesis-assets/diagrams/*.svg` (source) and `docs/thesis-assets/diagrams/png/*.png` (Word-compatible raster).
- Renderer: `@mermaid-js/mermaid-cli`, Chrome local, nền trong suốt.
- Kết quả: 24/24 Mermaid block render thành công; 0 lỗi cú pháp; 24/24 source SVG được raster thành PNG bằng Chromium.
- Revalidation sau khi bổ sung entity stub trong ERD: ERD-01 đến ERD-05 5/5 render lại thành công; không còn entity tham chiếu bị để rỗng.

## Kết quả theo nhóm

| Nhóm | Source blocks | Output | Kết quả | Ghi chú layout |
| --- | ---: | ---: | --- | --- |
| Use Case | UML-01 đến UML-03 | 3 SVG | PASS | UML-01 cao 2404 px; nên đặt trang dọc riêng hoặc tách theo actor khi xuất Word |
| Class Diagram | UML-04 đến UML-07 | 4 SVG | PASS | UML-06 rộng 1470 px; ưu tiên landscape hoặc full-width |
| Sequence Diagram | UML-08 đến UML-15 | 8 SVG | PASS | UML-09 và UML-13 rộng gần 2000 px; cần landscape để chữ không quá nhỏ |
| Activity Diagram | UML-16 đến UML-19 | 4 SVG | PASS | Các hình cao 1642-2438 px; đặt trang dọc riêng, tránh co xuống dưới 11 pt |
| ERD hiện hành | ERD-01 đến ERD-04 | 4 SVG | PASS | ERD-01/03 rộng trên 2500 px; bắt buộc landscape hoặc chia thành hai hình khi xuất DOCX |
| ERD mục tiêu | ERD-05 | 1 SVG | PASS | Phải giữ nhãn DEFERRED và không đặt trong mục chức năng đã cài đặt |

## Visual spot-check

- UML-01: actor và ba boundary hiển thị đúng; bố cục dài nhưng không cắt node/edge.
- UML-13: note `PARTIAL` cho requester/reviewer ID cố định hiển thị đúng trong sequence.
- ERD-01: quan hệ RBAC, property scope và subscription render đủ; chữ nhỏ nếu co vào trang A4 dọc.
- PNG spot-check: ERD-03, UML-01 và panel `search-result-after-a/b` hiển thị đầy đủ nhãn; không còn khung ảnh rỗng do nhúng SVG trực tiếp.
- DOCX structural spot-check: 47 inline images có `descr/title`, package không chứa SVG media; visual Word/PDF page review vẫn `BLOCKED_VISUAL` vì thiếu `soffice`.

## Quy tắc xuất bản

1. Dùng SVG làm source để diff; dùng PNG raster cho DOCX/Word để giữ `foreignObject` và nhãn Mermaid.
2. UML-09, UML-13, ERD-01 và ERD-03 dùng trang landscape hoặc panel `(a)/(b)`.
3. UML-01 và activity diagram dùng trang dọc riêng; nếu font nhỏ hơn 11 pt thì tách hình.
4. Mỗi hình phải có caption khớp mã trong `docs/UML.md` hoặc `docs/ERD.md`, alt text và được tham chiếu trong Chương 3.
5. Sau khi sửa source diagram, raster lại toàn bộ và cập nhật ngày/kết quả trong file này.

## Lệnh tái lập

```powershell
npx --yes @mermaid-js/mermaid-cli -i <diagram.mmd> -o <diagram.svg> -p <puppeteer-config.json> -b transparent
```

Các block Mermaid được trích từ `docs/UML.md` và `docs/ERD.md` trước khi chạy lệnh. Runner/cache tạm không thuộc artifact phát hành; bản SVG đã xác minh nằm trong `docs/thesis-assets/diagrams/`.

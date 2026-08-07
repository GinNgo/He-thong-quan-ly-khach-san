# Distill contract for D07 report formatting

Reference: `docs/thesis-assets/templates/D07_Mau_5_TrinhBay_KLTN_2026-07-28.docx`

Reference SHA-256: `4F66B9D2C1F19E0975C88D7415BBC25CA38622DDD4B19A8DCF96C9C4E0E17ADD`

Captured: 2026-07-28

## Page system

- A4 portrait.
- D07 written rule: left 3 cm, right/top/bottom 2 cm, footer page number centered.
- Section audit of the example DOCX reports one portrait section, page size 8.27 x 11.69 in, left/right 0.79 in and top 0.39 in. The top-margin mismatch is recorded in `THESIS_TEMPLATE_EVIDENCE.md`; the written rule controls the draft.

## Typography contract

- Times New Roman, body 13 pt, 1.5 lines, justified, first-line indent 1 cm.
- Chapter: 14 pt, uppercase, bold, centered.
- Level 1: 13 pt, uppercase, bold, left.
- Level 2: 13 pt, sentence case, bold, left.
- Level 3: 13 pt, sentence case, italic, left.
- Table body 12 pt; table title 11 pt bold above table; table note 10 pt italic below table; figure title 11 pt bold below figure; references 11 pt left.
- Arabic numbering only; no Roman chapter numbering.

## Content flow contract

- Main content: Mở đầu, Nội dung by chapter, Kết luận, tài liệu tham khảo, phụ lục.
- User electronic front-matter order: 13 slots in `WHOLE_DOCUMENT_MANIFEST.md` is authoritative for merge.
- D07 content requirements: summary, diagrams/model for software, 50-100 pages for main content, CD tree and supervisor/reviewer forms.
- Unsupported sample names/MSSV in D07 are examples only and must not be copied.

## Preservation and QA

- Reference DOCX remains unchanged; only copies or new draft files may be edited.
- Structural extraction of all body paragraphs/tables completed.
- `section_audit.py` completed.
- `style_lint.py` could not parse a legacy floating indent value (`504.00000000000006`); this is a source-template lint limitation, not a report claim.
- `render_docx.py` is blocked because LibreOffice/soffice is unavailable. Visual QA remains a release gate for the assembled report.

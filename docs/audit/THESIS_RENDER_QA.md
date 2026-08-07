# Thesis render QA - 2026-07-29

## Attempted checks

| Artifact | Method | Result |
| --- | --- | --- |
| `docs/export/LuxeStay_KhoaLuan_DRAFT_scrubbed.docx` | Packaged `render_docx.py` with bundled Python | BLOCKED: renderer could not start because `soffice` is unavailable (`WinError 2`) |
| `docs/export/LuxeStay_HuongDan_TraLoi_Rubric_DRAFT_scrubbed.docx` | Packaged `render_docx.py` with bundled Python | NOT_RUN after the thesis render blocker; same environment limitation applies |
| Thesis draft | Microsoft Word COM export | BLOCKED: export exceeded 120 seconds; orphaned Word process created by the probe was stopped |
| `docs/export/LuxeStay_KhoaLuan_FINAL.docx` | Packaged `render_docx.py --emit_pdf` with bundled Python | BLOCKED: `soffice` executable unavailable (`WinError 2`); no page PNG/PDF produced |

## What was verified without rendering

- A4 portrait geometry, margins, headings, tables, inline images, PNG-only diagram media, `TOC`/`PAGE` fields and `w:updateFields=true` passed structural audits.
- Chromium raster output for all 24 SVG sources was visually spot-checked and retained all Mermaid labels/`foreignObject` text.
- Tall diagram and screenshot assets are split into overlapping `(a)/(b)` PNG panels before embedding; image audit reports no displayed asset taller than 8.25 inches.
- Accessibility audit reported zero findings for both draft DOCX files.
- No page PNG or PDF is claimed to exist from this run.

## Release decision

The user approved a FINAL DOCX fallback on 2026-07-29. The final packages pass structural, accessibility and privacy checks, but are not visually certified. `T043`, `T044`, `T058` and `T074` remain open for page rendering, Word field refresh, visual inspection and PDF export.

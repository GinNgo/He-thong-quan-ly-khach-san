# DOCX structural QA - 2026-08-08

This audit covers the regenerated thesis draft after replacing SVG-only media patching with direct PNG embedding. It is still a structural check only; visual page render remains blocked because the available environment has no LibreOffice/soffice and Word COM export exceeded the probe timeout.

| Artifact | Paragraphs | Headings H1/H2/H3 | Tables | Inline images | Sections | Fields | Result |
| --- | ---: | ---: | ---: | ---: | ---: | --- | --- |
| `LuxeStay_KhoaLuan_DRAFT.docx` | 451 | 7 / 40 / 9 | 6 | 47 | 1 | TOC=1, PAGE field configured, updateFields=true | PASS_STRUCTURAL |
| `LuxeStay_HuongDan_TraLoi_Rubric_DRAFT.docx` | 201 | 0 / 13 / 32 | 3 | 0 | 1 | PAGE=2, updateFields=true | PASS_STRUCTURAL |
| `LuxeStay_KhoaLuan_FINAL.docx` | 459 | 7 / 41 / 9 | 6 | 49 | 1 | TOC=1, PAGE=2, updateFields=true | PASS_STRUCTURAL |
| `LuxeStay_HuongDan_TraLoi_Rubric_FINAL.docx` | 201 | 0 / 13 / 32 | 3 | 0 | 1 | PAGE=2, updateFields=true | PASS_STRUCTURAL |

## Geometry and media checks

- Both artifacts use A4 portrait, one section, left margin about 3 cm and right/top/bottom margins about 2 cm.
- Thesis final embeds 25 maintained UML/ERD/architecture PNGs in the full appendix plus chapter diagram placements and screenshot evidence; no `.svg` media or SVG relationship remains in the DOCX package.
- Diagram PNG dimensions range from 1,214 x 4,808 to 5,322 x 3,118 pixels; the 1 x 1 template media item is not used as a diagram.
- All 47 inline images have `docPr` description/title metadata, are inline rather than floating anchors, and are constrained to a maximum displayed height of 8.25 inches; tall assets are split into overlapping `(a)/(b)` panels.
- Accessibility audit: both artifacts report zero high/medium/low findings after screenshot alt text was added.
- Core `creator` and `lastModifiedBy` are empty; no custom properties, comments or tracked changes were found.

## Release limitation

This evidence does not close render-dependent tasks. The packaged renderer was attempted on 2026-07-29 and failed because the `soffice` executable is unavailable (`WinError 2`). DOCX/PDF page rendering, page-by-page visual review, field refresh in Word and final PDF export remain blocked. Word-compatible media is structurally ready, but not visually certified.

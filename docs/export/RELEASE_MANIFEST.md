# Release manifest - LuxeStay thesis report

version: `thesis-final-docx-2026-08-08`
verifiedAt: `2026-08-08` (Asia/Saigon)
status: `FINAL_DOCX/RENDER_BLOCKED`
reviewer: `User-approved finalization; Codex structural/a11y/privacy verification`

## Changed capabilities

- `AUTH-01`, `RBAC-01`: evidence and current backend security test alignment.
- `SUB-01`: source-verified read endpoints and feature-gate boundary.
- `IMPORT-01`: import staging evidence; claim identity gap recorded.
- `CHAT-01`, `NOTIF-01`: backend/current unit evidence; frontend E2E remains partial.
- `SEARCH-01`, `BOOK-01`, `PAY-01`, `OPS-01`: Chapter 3-4, UML/ERD and test freshness updates.
- Admin route inventory: 29 functional routes classified; runtime data-backed verification remains BLOCKED by environment/credential setup; `/admin/plans` purchase is PARTIAL from source.
- Diagram coverage: capability-to-Use Case/Class/Sequence/Activity/ERD matrix added; all 24 source diagrams rasterized to PNG with Chromium.
- DOCX pipeline: draft rebuilt with PNG as embedded diagram media; SVG-only patch removed.
- Final outputs: `LuxeStay_KhoaLuan_FINAL.docx` and `LuxeStay_HuongDan_TraLoi_Rubric_FINAL.docx` created from the latest generated artifacts with metadata scrubbed.

## Final artifact checksums

| Artifact | SHA-256 |
| --- | --- |
| `LuxeStay_KhoaLuan_FINAL.docx` | `2FEB2C5D8DD86612CE7E4DDBB7F8A00471AFFCDBF0B3782EFBE9C932BC3871CE` |
| `LuxeStay_HuongDan_TraLoi_Rubric_FINAL.docx` | `4ED3D1E39B9ED3FAC35B5CCF2F7A7EE1EB4F47E9ADEAE61066FE07E077741B45` |

## Verification commands and results

| Command | Result | Freshness |
| --- | --- | --- |
| `cd backend; cmd /c mvnw.cmd test` | 123/123, 0 failure/error/skipped, BUILD SUCCESS (2026-07-29) | CURRENT |
| `npm run test -- --watch=false` in `frontend/` | 73/73, 36 files passed (2026-07-29) | CURRENT |
| `npm run build` in `frontend/` | Production build passed; warnings recorded (2026-07-29) | CURRENT |
| `npx playwright test --list` | 71 tests in 12 files | CURRENT |
| `npx playwright test` | Timeout after 184 seconds; redirect/search artifacts | BLOCKED |
| Targeted Playwright smoke (`home.spec.ts` + `real-environment-smoke.spec.ts`) | 2 passed, 3 skipped, 0 failed in 21.8s; authenticated cases lacked `LUXESTAY_E2E_*` | CURRENT/PARTIAL |
| Admin shell smoke (`admin-flows.spec.ts`) | 17 passed in 1.1m; assertions are mostly `body visible` and login URL is broad | CURRENT_SMOKE_ONLY |
| Admin core data-backed smoke (`admin-core-management.spec.ts`) | 1 failed, 2 did not run; `admin/admin` remained at `/admin/login` | BLOCKED |
| Mermaid render of `docs/UML.md` + `docs/ERD.md` | 24/24 SVG pass, 0 syntax errors | CURRENT |
| Static privacy scan and screenshot spot-check | No secret/path/private key pattern; demo email noted | PRELIMINARY |
| D01-D08 Drive exports | 8 official DOCX exports, SHA-256 registry complete | CURRENT |
| D03/D04 rubric mapping | 14/14 source criteria mapped with boundary/readiness | CURRENT |
| DOCX structural QA | Thesis final: 459 paragraphs, 7/41/9 headings, 6 tables, 49 inline images, 39 package media items with no SVG, TOC/PAGE fields; architecture image and flowchart policy maps to Activity UML-16..19; rubric guide: 201 paragraphs, 3 tables, PAGE fields | CURRENT |
| DOCX accessibility audit | 0 high/medium/low findings for both draft artifacts after screenshot alt text fix | CURRENT |
| FINAL DOCX package/integrity | Both final files pass ZIP/package open; thesis has 451 paragraphs, 6 tables, 47 inline images, 38 PNG media, 0 SVG and TOC/PAGE fields | CURRENT |
| FINAL privacy scan | 0 local path, private-key, JWT, secret-assignment or email patterns in both final DOCX packages | CURRENT |
| FINAL render attempt | Packaged `render_docx.py` failed before conversion because `soffice` is unavailable (`WinError 2`); no PDF/page PNG produced | BLOCKED |
| Browser screenshot refresh | Public desktop/mobile CURRENT; search/admin/room API error states captured and labeled BLOCKED | CURRENT/BLOCKED_BY_API |
| Chromium diagram raster | 24/24 SVG sources -> PNG; labels visually retained in spot-check | CURRENT |
| Admin route matrix | 29 functional routes inventory; 10 partner-overview routes and `/admin/properties`/`profile` require guard review | CURRENT_STATIC/BLOCKED_RUNTIME |

## Historical results

Old totals 49/49, 60/60, 86/86, 20/20 and 32/32 remain in the audit as `HISTORICAL`; they are not used as current conclusions.

## Blocked conditions

- Official template/rubric exports are now available and checksum-verified; signed institutional forms are still missing.
- Playwright authenticated/full suite is not stable.
- Claim controller still uses fixed requester/reviewer IDs.
- Admin E2E lacks `LUXESTAY_E2E_*` credentials and LuxeStay backend is not isolated from `videoai-api-1` on port 8080.
- FINAL DOCX and rubric guide are assembled and structurally audited. PNG media is ready, but the packaged renderer cannot start because `soffice` is unavailable, so PDF export and page-by-page visual QA remain blocked.

## Privacy and release gate

The user approved finalizing the DOCX deliverables with the documented renderer limitation. `THESIS_PRIVACY_QA.md` passes for both FINAL DOCX files, but no PDF exists to scan. Status can become `FINAL_VERIFIED_VISUAL/PDF` only after Word/LibreOffice page review and PDF export.

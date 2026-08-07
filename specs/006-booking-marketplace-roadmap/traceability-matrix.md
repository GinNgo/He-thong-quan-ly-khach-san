# Traceability Matrix

| Requirement | Business rules | Acceptance | Planned tasks | Status (2026-07-29) |
|---|---|---|---|---|
| FR-001 | BR-004 | AC-101 | T007-T008 | COMPLETE - header/browser regression evidence recorded |
| FR-002, FR-004-FR-006 | BR-002-BR-004 | AC-102, AC-104 | T009-T015 | COMPLETE - 4 viewport matrix, 16 focused tests and full frontend regression pass |
| FR-003 | BR-001-BR-003 | AC-103 | T009-T012, T015 | PARTIAL/BLOCKED - overnight/local-date/invalid range covered; day-use UI is intentionally disabled because the backend contract still requires checkout |
| FR-007-FR-009 | BR-005-BR-008 | AC-201-AC-204 | T016-T024 | COMPLETE - persisted E2E API/browser verification passes for labelled suggestions, URL/radius reload, distance context and recovery coverage |
| FR-010-FR-014 | BR-009-BR-015 | AC-301-AC-305 | T025-T035, T110-T117 | COMPLETE - OQ-003/OQ-004/OQ-005 are approved; campaign/placement lifecycle tests, canonical price consistency, idempotent E2E fixtures and authenticated 375/768/1024/1440 Home/Search/Detail/Checkout evidence prove GOLD eligibility, transparent original/final totals and VI/EN sponsored disclosure with no overflow or missing P1 keys. OQ-002 remains separate unpaid-hold scope |
| FR-015-FR-018 | BR-029-BR-033 | AC-401-AC-404 | T036-T044 | COMPLETE - runtime asset delivery, 573-key VI/EN parity, translated P1 surfaces, pre-render locale initialization, accessible slideshow controls and browser VI/EN evidence pass. Playwright reduced-motion verification plus five cold desktop/mobile runs report p75 interaction delay below 7 ms and max CLS below 0.002 |
| FR-019-FR-024 | BR-016-BR-028 | AC-501-AC-505 | T045-T054, T097-T101 | PARTIAL - T045-T054 and T097-T100 complete. T054 passes 2/2 authenticated customer/admin Playwright journeys, including cancellation creating `REQUESTED`, all payment/refund states across admin pagination, no page overflow and customer/admin screenshots. T100 passes official MoMo/ZaloPay query/refund/refund-query contracts, scheduled missed-callback recovery, server-owned amount/reference checks and timeout-safe refund retries. Full regression is 46 files/113 tests and 54 suites/216 tests; only T101 live sandbox proof remains open |
| FR-025-FR-027 | BR-034-BR-039 | AC-601-AC-604 | T055-T061 | PARTIAL - T055-T058 and AC-601-AC-602 complete: authenticated browser chat, tenant/property/reservation conversation context, private user queues, assignment/escalation, hidden cross-tenant denial and audit evidence pass. T059-T061 remain blocked for approved secret-safe Facebook/Zalo channel configuration and official sandbox adapters |
| FR-028-FR-030 | BR-040-BR-045 | AC-701-AC-704 | T062-T068 | PARTIAL - T062/T063/T065-T068 complete with canonical plan/feature/usage API, truthful read-only UI, server-side quota enforcement, principal-derived property-claim requester/reviewer identity, active/expired/lifetime/unlimited/multi-plan integration plus 5/5 real browser journeys, expired mutation denial and a verified support-contact upgrade path. Only T064 admin plan/feature lifecycle remains open behind OQ-010/configuration approval; online purchase is intentionally not claimed |
| FR-031-FR-032 | All stop/evidence rules | AC-801-AC-804 | T001-T006, T069-T075 | COMPLETE for the integrated evidence/decision-gate contract - T069/T070 regression, T071/T072 4-viewport x 5-surface browser/performance audit, T073 reconciliation, T074 matrix and T075 release report are complete. Unapproved product/provider work remains explicitly blocked rather than hidden by mock UI |
| FR-033-FR-038 | BR-046-BR-053 | AC-205-AC-210 | T076-T088 | COMPLETE - deterministic 122-row catalog, strict 34/34 coverage, idempotent import, audit schema/entities, measured API behavior and four-region browser evidence pass |
| FR-039-FR-043 | BR-046-BR-053 | AC-205-AC-210 | T089-T096 | COMPLETE for backend/data scope - 34 public `VN34-*` rows, 63-to-34 collision-safe aliases, merged hotel search, remapped landmarks, strict generator/import tests and live E2E API evidence pass |

## Evidence Rules

- Update the status only when the linked acceptance criteria have automated and browser/provider evidence.
- Payment/social external rows may remain `BLOCKED` when policy/credentials are absent; do not use mocks to change them to complete.
- A requirement marked complete must link to test names, browser viewport/actor and any external provider sandbox result.

Phase 1 evidence is detailed in `quickstart.md`, including the viewport metrics, test commands, screenshots, overlay ordering and the explicit day-use backend blocker.

Phase 2 evidence is detailed in `quickstart.md`, including fixture/import validation, duplicate-name province scoping, backend-resolved coordinates, bounded radius search, URL reload restoration, distance display and empty-state recovery. T024 is complete against the isolated E2E backend.

Phase 2B evidence is recorded separately because nationwide source coverage, licensing, deterministic generation and safe refresh are data-governance requirements beyond the original seven-row Phase 2 fixture.

Phase 4 evidence is detailed in `quickstart.md`: translation assets are included in production output and served at runtime, the current full 46-file/113-test regression passes, and the slideshow contract is implemented. Home VI/EN rendering, reduced-motion behavior and five cold desktop/mobile interaction/CLS runs are recorded through the focused Playwright release spec.

Phase 5 evidence is detailed in `quickstart.md`: backend transition, concurrency and callback/refund idempotency suites pass; T054 adds dedicated E2E fixtures and 2/2 authenticated customer/admin Playwright journeys with `docs/screenshots/payment-refund-customer.png` and `docs/screenshots/payment-refund-admin.png`. T100 closes local provider query/refund adapters and scheduled recovery; live sandbox callbacks remain a separate T101 evidence gate.

Phase 6 evidence is detailed in `quickstart.md`: T055 retains the authenticated browser lifecycle, while T056-T058 add the tenant-scoped conversation schema, principal/reservation context resolution, private support delivery, assignment/escalation controls and `2/2` H2 isolation tests. External provider configuration and transmission remain blocked under T059-T061.

Phase 8 evidence is detailed in `quickstart.md`: `integrated-release-matrix.spec.ts` passes four serial viewport journeys covering 20 public/customer/admin/management surfaces. The historical no-sponsored result recorded the then-open policy gate. Phase 3 now supersedes that state with approved, fixture-backed and visibly disclosed sponsored placement evidence at all four required widths.

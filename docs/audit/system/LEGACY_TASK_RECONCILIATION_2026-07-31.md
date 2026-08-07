# Legacy Task Reconciliation - 2026-07-31

## Purpose

This document reconciles the older SpecKit/task backlogs with Feature 007. It does not mark a task complete based on source appearance alone and does not delete or rewrite the original task files.

## Snapshot

| Task source | Completed markers | Open markers | Disposition |
|---|---:|---:|---|
| Feature 01 - Core Auth/Tenant | 29 | 2 | Merge security/RBAC evidence into Feature 007 foundation; keep any missing migration evidence open |
| Feature 02 parent | 2 | 14 | Tracking-only backlog; converge with child features and Feature 007 full-system verification |
| Feature 02A - Design System | 15 | 0 | Complete markers retained; still needs final regression evidence if reused by changed screens |
| Feature 02B - App Shell/Role Navigation | 4 | 14 | Still open; UI shell and final regression track |
| Feature 03 - UI Functional Audit | 44 | 32 | Merge payment/security/audit portions; retain unresolved UI evidence tasks |
| Feature 04 - Thesis Report | 69 | 6 | Separate documentation/render track; selected Admin E2E items merge into full-system verification |
| Feature 05 - Home/Footer | 16 | 0 | Complete markers retained |
| Feature 06 - Booking Marketplace | 111 | 6 | Split: payment/subscription/sandbox into Feature 007; promotions/VIP/ads/social remain separate |
| Feature 07 - Payment/Billing | 193 | 152 | Current Feature 007 markers include bounded evidence tasks, but final-worktree gates and unresolved remediation remain open |

The current legacy source files contain 74 open markers (Feature 01-06, including Feature 02A/02B). The Feature 02 parent contributes 14 tracking markers that overlap its Feature 02B child backlog, so the unique legacy implementation backlog is 60 markers. These counts were read directly from each `tasks.md` on 2026-08-03 and do not include Feature 007's 152 open markers.

## Reconciliation Run - 2026-08-03

This run read the current legacy task files, Feature 007 `tasks.md`, the inventory/traceability/audit artifacts and available evidence under `docs/testing/evidence/007/`. It did not mutate source code, run production migrations, call external providers or change any task checkbox. The working tree is dirty, so the repository `HEAD` (`b2196f503b3bb306fb8446a9154a03b8be51d70c`) is only a base reference, not a final-worktree fingerprint. At the time of the read, Feature 007 contained 193 checked markers and 152 open markers (345 total).

### Current disposition of every `MERGED_PENDING` mapping

`PARTIAL` below means that the destination has bounded implementation or focused-test evidence, but the legacy scope cannot be closed because a required final-worktree, browser, database, policy or external-provider boundary is still absent. A checked Feature 007 marker is not treated as final proof when its evidence explicitly records a skip, blocker or non-final environment.

| Legacy item | Current status | Current evidence | Remaining blocker / closure condition |
|---|---|---|---|
| Feature 01 `TASK-C1` valid role/permission acceptance | `PARTIAL / MERGED_PENDING` | Feature 007 T027-T029 are checked; requirement traceability still classifies FR-026/FR-039/FR-040/SC-011 as `PARTIAL`. | T159 final backend evidence and T174 release-gate/status reconciliation are absent; no final all-role/property HTTP run is recorded. |
| Feature 01 `TASK-C1` `PROPERTY_OWNER` permission migration | `PARTIAL / MERGED_PENDING` | `V28__financial_permissions.sql` exists and the foundation SQL Server run proves additive V21-V29 execution and 11 financial functions. The legacy task still has an unchecked follow-up migration specifically for `PROPERTY_OWNER`. | A dedicated `PROPERTY_OWNER` mapping migration/rollback and final SQL Server evidence are missing; T174 remains open and T158 clean V1-to-latest migration is a gap. |
| Feature 02 parent `F02-003`, `F02-A`-`F02-H` convergence markers | `PARTIAL / MERGED_PENDING` | T141-T150, T164 and T166 are checked; inventory/traceability artifacts exist. | T151 remediation execution, T155 manual journey evidence, T159-T163 final suites/reconciliation and T165 responsive coverage remain open or partial. |
| Feature 02 parent `F02-004`-`F02-007` test/build/route/convergence markers | `PARTIAL / MERGED_PENDING` | Focused artifacts and route/audit inventories exist; T173 maps 61/61 FR/SC rows. | T159 backend, T160 Angular build/unit, T161 all-role Playwright, T162 financial reconciliation and T163 final report artifacts are absent. |
| Feature 02B `T02B-011`-`T02B-016` tests/build/browser/security/convergence | `PARTIAL / MERGED_PENDING` | T143, T146, T164 and T166 are checked; `financial-accessibility.spec.ts` is discoverable and `responsive.md` records eight five-width passes. | The responsive artifact records blocked checkout/invoice/platform-admin surfaces; T161 final traces and T160 final build are absent, so no shell-wide regression closure is justified. |
| Feature 03 `T021`-`T025` public/customer/admin/property UI fixes and evidence | `PARTIAL / MERGED_PENDING` | T141-T146 inventory rows and bounded frontend/payment evidence exist; `FULL_SYSTEM_AUDIT_REPORT.md` remains `PARTIAL / NOT READY`. | Final API-backed browser journeys, screenshots and role coverage (T155/T161) are missing; Angular/runtime fixture blockers remain in the final evidence set. |
| Feature 03 `T026`, `T028`, `T030`-`T036` audit taxonomy, tokens, shells, controls and responsive review | `PARTIAL / MERGED_PENDING` | T147-T150, T164 and T166 are checked; `responsive.md` is explicitly `PARTIAL`. | T151/T155 and T165 remain open; several required widths/routes are blocked or only source/component-covered. |
| Feature 03 `T040`, `T049`, `T052`, `T053`, `T063`-`T069` regression/traceability/payment evidence | `PARTIAL / MERGED_PENDING` | Property callback contract (22/22), callback replay/concurrency (7/7), manual confirmation (14/14) and payment-panel evidence report passing focused runs; T173 is checked. | No final backend/frontend/Playwright/reconciliation artifacts (T159-T163); T169 abuse-control rerun failed (3/5 failures, no `429`, oversized callback `500`). |
| Feature 03 `T058` non-production payment callback approval/contract | `PARTIAL / MERGED_PENDING` | T061 provider contracts report 22/22 pass; T062 reports 7/7 pass; T156 documents simulator/sandbox-only boundary. | T169 rate-limit/polling/oversize abuse controls failed, live provider delivery is `BLOCKED_EXTERNAL`, and final T159/T161 evidence is absent. |
| Feature 04 `T066`, `T067` Admin data-backed and mutation E2E | `PARTIAL / MERGED_PENDING` | Admin inventory and focused lifecycle artifacts exist; platform billing unit/controller suites pass in bounded evidence. | T161 all-role Playwright traces are absent; `responsive.md` records checkout/invoice fixtures blocked and platform purchase skipped without sandbox variables. |
| Feature 06 `T064` admin plan/feature lifecycle | `PARTIAL / MERGED_PENDING` | T092-T111 are checked and platform billing evidence records focused backend/UI suites passing; provider contracts are simulator-only. | Playwright purchase/negative journeys are explicitly skipped without simulator variables; downgrade/proration and production merchant remain `BLOCKED_EXTERNAL`, so final lifecycle closure is not proven. |

### Release blockers observed during this run

- Final artifacts `docs/testing/evidence/007/final/clean-migration.md`, `backend.md`, `frontend.md`, `reconciliation.md` and `docs/testing/FINAL_WORKTREE_TEST_REPORT.md` are absent.
- The final audit reports `PARTIAL / NOT READY`; T151 and 129 of the 130 generated remediation tasks (T217-T345; T216 is checked) remain open. The audit report's embedded "130 open" wording is stale against the current task markers. T174/T175 are still release gates.
- T168 privacy review is a `GAP`: fixture logs, CSV exports and screenshots contain unmasked account/PII identifiers, and generic exception-message paths are not fail-closed.
- T169 callback-abuse verification is failed: 3 failures in 5 tests, polling emitted only `200` (no `429`), callback burst was not rate-limited by source IP, and an oversized callback returned `500` instead of `413`.
- T176 performance budgets are blocked because the measured report p95 is 3698.4 ms against the 3000 ms budget.
- Production provider credentials/merchant registration/public callbacks and Facebook/Zalo decisions/credentials remain intentionally `BLOCKED_EXTERNAL`; production stays disabled.

## Status Definitions

- `COMPLETE_VERIFIED`: Existing task has a completed marker and current evidence remains valid for the final worktree; otherwise it is reopened by the relevant regression task.
- `MERGED_PENDING`: Older scope is represented by one or more Feature 007 tasks, but implementation/evidence is not complete yet.
- `STILL_OPEN`: Scope remains an independent deliverable and must be executed in its original feature or the assigned legacy convergence task.
- `BLOCKED_EXTERNAL`: Requires an explicit policy, legal decision, credential or provider sandbox; simulator/adapter/contract work remains required.
- `SUPERSEDED`: A newer mandatory requirement replaces the old scope. The original file remains historical and is not deleted.

## Mapping by Feature

### Feature 01 - Core Auth/Tenant

| Legacy item | Classification | Destination |
|---|---|---|
| `TASK-C1` acceptance test for valid role/permission | `MERGED_PENDING` | Feature 007 T027-T029, T159 and T174 |
| `TASK-C1` `PROPERTY_OWNER` permission migration | `MERGED_PENDING` | Feature 007 T028-T029 and T174; migration remains required |

The old Feature 01 notes that tenant isolation was previously fixed with service guards. Feature 007 additionally requires active Hibernate filtering and architecture coverage, so old `PASSED` status does not close the new requirement.

### Feature 02 - Frontend UX

| Legacy item | Classification | Destination |
|---|---|---|
| Parent `F02-003`, `F02-A` to `F02-H` convergence markers | `MERGED_PENDING` | Feature 007 T141-T166 and T173-T175 |
| Parent `F02-004` to `F02-007` test/build/route/convergence markers | `MERGED_PENDING` | Feature 007 T159-T163 and T174 |
| Parent `F02-008` push marker | `STILL_OPEN` | Execute only after the owner requests commit/push for the final green worktree |
| Feature 02B `T02B-005` to `T02B-010` shell/navigation work | `STILL_OPEN` | Feature 007 T143, T146, T164-T166 and legacy task file |
| Feature 02B `T02B-011` to `T02B-016` tests/build/browser/security/convergence | `MERGED_PENDING` | Feature 007 T159-T166 |
| Feature 02B `T02B-017` to `T02B-018` commit/push | `STILL_OPEN` | Final handoff only; no automatic push in this reconciliation |

Feature 02A has no open checkbox, but changed financial screens still require the responsive/accessibility regression in Feature 007.

### Feature 03 - UI Functional Audit and Polish

| Legacy items | Classification | Destination |
|---|---|---|
| `T021` to `T025` public/customer/admin/property UI fixes and evidence | `MERGED_PENDING` | Feature 007 T141-T146, T164-T166 and story-specific frontend tasks |
| `T026`, `T028`, `T030` to `T036` audit taxonomy, tokens, shells, controls and responsive review | `MERGED_PENDING` | Feature 007 T147-T155, T164-T166 |
| `T040`, `T049`, `T052`, `T053`, `T063` to `T069` regression/traceability/payment evidence | `MERGED_PENDING` | Feature 007 T056-T069, T147-T163, T173-T175 |
| `T058` non-production payment callback approval/contract | `MERGED_PENDING` | Supplied payment requirements approve simulator/sandbox boundary; implementation remains Feature 007 T055-T063 |

The old task's “partial” suffix is preserved as evidence that code alone did not close the flow.

### Feature 04 - Thesis Report Maintenance

| Legacy items | Classification | Destination |
|---|---|---|
| `T043`, `T044`, `T058`, `T074` DOCX/PDF render and review | `STILL_OPEN` | Feature 007 T180; remain documentation-specific |
| `T066`, `T067` Admin data-backed and mutation E2E | `MERGED_PENDING` | Feature 007 T141-T146 and T161 |

### Feature 05 - Home Landing/Footer

No open checkbox remains. Any regression caused by later payment/search UI changes is covered by Feature 007 T160-T166 and the existing Feature 05 quickstart.

### Feature 06 - Booking Marketplace

| Legacy items | Classification | Destination |
|---|---|---|
| `T004` OQ-002, OQ-003, OQ-004, OQ-005, OQ-010 approval gate | `SPLIT` | OQ-002 remains a product decision; subscription OQ-010 is superseded by mandatory Feature 007 scope; OQ-003 to OQ-005 remain Feature 007 T181 |
| `T025` to `T035` promotions, membership tier, sponsored placement and quote consistency | `STILL_OPEN` | Feature 007 T181 and original Feature 06 tasks |
| `T101` live VNPay/MoMo/ZaloPay sandbox journey | `BLOCKED_EXTERNAL` | Feature 007 T061-T063 and T156 provide simulator/contract/sandbox coverage; real credentials/public callbacks require separate provisioning |
| `T059` to `T061` Facebook/Zalo tenant channels | `BLOCKED_EXTERNAL` | Feature 007 T182; do not store credentials or transmit messages before OQ-006 to OQ-009 are approved |
| `T064` admin plan/feature lifecycle | `MERGED_PENDING` | Feature 007 T092-T111 and T181 if catalog policy gaps remain |

## New Legacy Convergence Tasks

The following tasks are appended to Feature 007 so the old backlog has an executable owner and cannot disappear between feature switches:

- `T177` Re-run the legacy reconciliation against the final worktree and update this document with evidence for every `MERGED_PENDING` item.
- `T178` Complete Feature 01 RBAC acceptance and `PROPERTY_OWNER` migration evidence if T027-T029/T174 do not cover it.
- `T179` Complete Feature 02B shell/navigation implementation and route/responsive/security regression not covered by T141-T166.
- `T180` Complete Feature 04 thesis DOCX/PDF render/review deliverables and attach final artifact evidence.
- `T181` Complete Feature 06 promotion stacking, membership tier, sponsored placement, quote consistency and admin lifecycle work after OQ-002 to OQ-005 policy approval.
- `T182` Complete the approved Facebook/Zalo tenant support adapter and management UI only after OQ-006 to OQ-009 decisions and sandbox credentials; otherwise keep the capability explicitly `BLOCKED_EXTERNAL`.
- `T183` Run final legacy task/status convergence, update every original task file with evidence-backed status, and prepare a separate commit/push recommendation; do not push automatically.

## Non-Destructive Rules

1. Original task files remain intact and are not reset or broadly rewritten.
2. A completed source change without fresh test/evidence remains `MERGED_PENDING`.
3. Real provider credentials, production database migrations, real-money callbacks and social-message transmission remain stop gates.
4. Commit/push tasks are handoff actions, not automatic consequences of this reconciliation.

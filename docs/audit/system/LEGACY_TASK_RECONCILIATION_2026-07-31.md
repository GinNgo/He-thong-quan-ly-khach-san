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
| Feature 03 - UI Functional Audit | 43 | 26 | Merge payment/security/audit portions; retain unresolved UI evidence tasks |
| Feature 04 - Thesis Report | 69 | 6 | Separate documentation/render track; selected Admin E2E items merge into full-system verification |
| Feature 05 - Home/Footer | 16 | 0 | Complete markers retained |
| Feature 06 - Booking Marketplace | 84 | 17 | Split: payment/subscription/sandbox into Feature 007; promotions/VIP/ads/social remain separate |
| Feature 07 - Payment/Billing | 0 | 176 | New execution backlog; no implementation task has been falsely marked complete |

The raw legacy total is 79 open markers. The Feature 02 parent contributes 14 tracking markers that overlap its child backlog, so the unique implementation backlog is lower and must be verified against source/evidence before closure.

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

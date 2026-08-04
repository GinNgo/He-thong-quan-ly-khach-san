# Cross-Cutting Branch Handoff

Branch: `codex/cross-cutting`

This file records branch-local completion evidence. Shared aggregate files are intentionally left for the coordinator.

| Task | Commit | Validation | Domain evidence |
|---|---|---|---|
| T319 | `85fa7693c6f48ffbeef3707790a32ca01f29401b` | Backend 6/6; Angular 7/7; Playwright 1/1; production build PASS | `docs/testing/evidence/007/remediation/T319-customer-notification-inbox.md`; CROSS-003 in `docs/audit/system/inventory/cross-cutting.md` |
| T320 | `fff38aee85fac41837f1ca4954c5749904db5d5f` | Backend reliability/concurrency 8/8; Angular 8/8; Playwright 1/1; production build PASS | `docs/testing/evidence/007/remediation/T320-notification-delivery-reliability.md`; CROSS-004 in `docs/audit/system/inventory/cross-cutting.md` |
| T321 | `cf5082e896a0f58de7b988c0aec64a5fd629d343` | Backend 22/22; Angular 11/11; Playwright 1/1; production build PASS | `docs/testing/evidence/007/remediation/T321-notification-preferences-lifecycle.md`; CROSS-005 in `docs/audit/system/inventory/cross-cutting.md` |
| T325 | `1c4851aca9dd8f90118b61eb19ceadff5436e03c` | Backend 10/10; Angular 13/13; Playwright two-context 1/1; production build PASS; SQL Server V59 rerun PASS | `docs/testing/evidence/007/remediation/T325-support-chat-conversation-history.md`; CROSS-011 in `docs/audit/system/inventory/cross-cutting.md` |
| T326 | `dfde4a7ecce6ba8e379ae6cd1c6a3aa728fde9aa` | Backend 30/30; Angular 14/14; Playwright HTTP/STOMP two-context 1/1; production build PASS; SQL Server V59+V60 rerun PASS | `docs/testing/evidence/007/remediation/T326-support-queue-lifecycle.md`; CROSS-012 in `docs/audit/system/inventory/cross-cutting.md` |
| T327 | `726178bef6a163708f6aae703d8fbb4a94a4e00f` | Backend 24/24; Angular 20/20; Playwright two-context 1/1; SQL Server V61 first run and rerun PASS; production build PASS | `docs/testing/evidence/007/remediation/T327-chat-idempotency-read-state.md`; CROSS-015 in `docs/audit/system/inventory/cross-cutting.md` |
| T328 | `43700ad1ef723b25d764c2387a0bde75e86c7f48` | Backend 28/28; Angular 23/23; Playwright 1/1; SQL Server V62 first run and rerun PASS; production build PASS | `docs/testing/evidence/007/remediation/T328-support-close-attachments-search-sla.md`; CROSS-016 in `docs/audit/system/inventory/cross-cutting.md` |
| T329 | `090178f66074ca4d1961a77a4113b6a957c6cd3c` | Backend focused/context/reconciliation 16/16; Angular 3/3; Playwright 2/2; production build PASS | `docs/testing/evidence/007/remediation/T329-admin-authoritative-kpi-dashboard.md`; CROSS-018 in `docs/audit/system/inventory/cross-cutting.md` |

## Coordinator updates required

- Mark T319 complete in `specs/007-payment-billing-completion/tasks.md` after merging the commit and evidence.
- Promote CROSS-003 in `docs/audit/system/MASTER_FUNCTION_INVENTORY.md`.
- Add T319 executable coverage to `docs/audit/system/FULL_SYSTEM_TRACEABILITY_MATRIX.md` for FR-044 / SC-013.
- Mark T320 complete in `specs/007-payment-billing-completion/tasks.md` after merging the commit and evidence.
- Promote CROSS-004 in `docs/audit/system/MASTER_FUNCTION_INVENTORY.md` and add T320 executable coverage to `docs/audit/system/FULL_SYSTEM_TRACEABILITY_MATRIX.md` for FR-044 / SC-013.
- Mark T321 complete in `specs/007-payment-billing-completion/tasks.md` after merging the commit and evidence.
- Promote CROSS-005 in `docs/audit/system/MASTER_FUNCTION_INVENTORY.md` and add T321 executable coverage to `docs/audit/system/FULL_SYSTEM_TRACEABILITY_MATRIX.md` for FR-044 / SC-013.
- Mark T325 complete in `specs/007-payment-billing-completion/tasks.md` after merging the commit and evidence.
- Promote CROSS-011 in `docs/audit/system/MASTER_FUNCTION_INVENTORY.md` and add T325 executable coverage to `docs/audit/system/FULL_SYSTEM_TRACEABILITY_MATRIX.md` for FR-044 / SC-013.
- Mark T326 complete in `specs/007-payment-billing-completion/tasks.md` after merging the commit and evidence.
- Promote CROSS-012 in `docs/audit/system/MASTER_FUNCTION_INVENTORY.md` and add T326 executable coverage to `docs/audit/system/FULL_SYSTEM_TRACEABILITY_MATRIX.md` for FR-044 / SC-013.
- Mark T327 complete in `specs/007-payment-billing-completion/tasks.md` after merging the commit and evidence.
- Promote CROSS-015 in `docs/audit/system/MASTER_FUNCTION_INVENTORY.md` and add T327 executable coverage to `docs/audit/system/FULL_SYSTEM_TRACEABILITY_MATRIX.md` for FR-044 / SC-013.
- Mark T328 complete in `specs/007-payment-billing-completion/tasks.md` after merging the commit and evidence.
- Promote CROSS-016 in `docs/audit/system/MASTER_FUNCTION_INVENTORY.md` and add T328 executable coverage to `docs/audit/system/FULL_SYSTEM_TRACEABILITY_MATRIX.md` for FR-044 / SC-013.
- Mark T329 complete in `specs/007-payment-billing-completion/tasks.md` after merging the commit and evidence.
- Promote CROSS-018 in `docs/audit/system/MASTER_FUNCTION_INVENTORY.md` and add T329 executable coverage to `docs/audit/system/FULL_SYSTEM_TRACEABILITY_MATRIX.md` for FR-044 / SC-013.

## Deferred claim conflicts

- T323 was not modified because its invoice-email/PDF scope overlaps the active T309 claim owned by `/root/t309_itemized_invoice`. The coordinator should requeue T323 only after that claim is released or explicitly partitioned.

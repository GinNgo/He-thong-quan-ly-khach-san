# Cross-Cutting Branch Handoff

Branch: `codex/cross-cutting`

This file records branch-local completion evidence. Shared aggregate files are intentionally left for the coordinator.

| Task | Commit | Validation | Domain evidence |
|---|---|---|---|
| T319 | `85fa7693c6f48ffbeef3707790a32ca01f29401b` | Backend 6/6; Angular 7/7; Playwright 1/1; production build PASS | `docs/testing/evidence/007/remediation/T319-customer-notification-inbox.md`; CROSS-003 in `docs/audit/system/inventory/cross-cutting.md` |
| T320 | `fff38aee85fac41837f1ca4954c5749904db5d5f` | Backend reliability/concurrency 8/8; Angular 8/8; Playwright 1/1; production build PASS | `docs/testing/evidence/007/remediation/T320-notification-delivery-reliability.md`; CROSS-004 in `docs/audit/system/inventory/cross-cutting.md` |
| T321 | `cf5082e896a0f58de7b988c0aec64a5fd629d343` | Backend 22/22; Angular 11/11; Playwright 1/1; production build PASS | `docs/testing/evidence/007/remediation/T321-notification-preferences-lifecycle.md`; CROSS-005 in `docs/audit/system/inventory/cross-cutting.md` |
| T325 | `1c4851aca9dd8f90118b61eb19ceadff5436e03c` | Backend 10/10; Angular 13/13; Playwright two-context 1/1; production build PASS; SQL Server V59 rerun PASS | `docs/testing/evidence/007/remediation/T325-support-chat-conversation-history.md`; CROSS-011 in `docs/audit/system/inventory/cross-cutting.md` |

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

## Deferred claim conflicts

- T323 was not modified because its invoice-email/PDF scope overlaps the active T309 claim owned by `/root/t309_itemized_invoice`. The coordinator should requeue T323 only after that claim is released or explicitly partitioned.

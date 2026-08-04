# Stay Lifecycle Branch Handoff

Branch: `codex/stay-lifecycle`

This branch does not edit the parallel aggregate task list or system-wide
inventory/traceability files. The coordinator should apply the updates listed
below after merging or cherry-picking the task commits.

## Completed Tasks

| Task | Commit | Focused validation | Domain evidence |
|---|---|---|---|
| T295 | `6788dbe` | Backend 11/11; frontend 13/13 | `docs/testing/evidence/007/remediation/T295-reservation-read-journeys.md` |
| T296 | `58d75a4` | Isolated backend compile; backend 46/46; frontend 15/15 | `docs/testing/evidence/007/remediation/T296-reservation-change-policy-gate.md` |
| T300 | `29338ee` | Isolated backend compile; backend 20/20; frontend 12/12 | `docs/testing/evidence/007/remediation/T300-available-physical-room-picker.md` |
| T301 | `bcc85e0`, `ada8586`, `3bed3ba` | Isolated backend compile; backend 38/38; frontend 24/24; final picker 10/10 | `docs/testing/evidence/007/remediation/T301-room-assignment-lifecycle.md` |
| T306 | `7d4999b` | Database-backed reconciliation and tenant isolation 2/2 | `docs/testing/evidence/007/remediation/T306-booking-financial-reconciliation.md` |

## Partial Tasks

| Task | Commit | Focused validation | Remaining gate |
|---|---|---|---|
| T302 | `8834080` | Backend policy/locking/IDOR/HTTP PASS; frontend 16/16 | Authenticated real API/browser journey is blocked because no usable local staff demo credential is configured. Evidence: `docs/testing/evidence/007/remediation/T302-check-in-readiness.md`. |
| T303 | `c6a8340` | Isolated backend compile; backend 6/6; frontend 10/10; Angular development build PASS | Authenticated real quote/create/assignment journey is blocked by the same missing local staff demo credential. Evidence: `docs/testing/evidence/007/remediation/T303-staff-booking.md`. |

## Coordinator Updates

- Mark T295 complete in `specs/007-payment-billing-completion/tasks.md`.
- Promote STAY-001 to `COMPLETE_VERIFIED` in
  `docs/audit/system/MASTER_FUNCTION_INVENTORY.md`.
- Add T295/STAY-001 executable evidence to
  `docs/audit/system/FULL_SYSTEM_TRACEABILITY_MATRIX.md`.
- Mark T296 complete in `specs/007-payment-billing-completion/tasks.md`.
- Promote STAY-002 to `COMPLETE_VERIFIED` in
  `docs/audit/system/MASTER_FUNCTION_INVENTORY.md` and add the T296 evidence/commit to
  `docs/audit/system/FULL_SYSTEM_TRACEABILITY_MATRIX.md`.
- After the parallel locale work is merged, move the amendment workspace's local Vietnamese copy
  into the shared EN/VI catalog without changing the server-owned pricing and policy behavior.
- Mark T300 complete in `specs/007-payment-billing-completion/tasks.md`.
- Promote STAY-006 to `COMPLETE_VERIFIED` in
  `docs/audit/system/MASTER_FUNCTION_INVENTORY.md` and add the T300 evidence/commit to
  `docs/audit/system/FULL_SYSTEM_TRACEABILITY_MATRIX.md`.
- Mark T301 complete in `specs/007-payment-billing-completion/tasks.md`.
- Promote STAY-008 to `COMPLETE_VERIFIED` in
  `docs/audit/system/MASTER_FUNCTION_INVENTORY.md` and add the T301 evidence/commit to
  `docs/audit/system/FULL_SYSTEM_TRACEABILITY_MATRIX.md`.
- Mark T306 complete in `specs/007-payment-billing-completion/tasks.md`.
- Promote STAY-016 to `COMPLETE_VERIFIED` in
  `docs/audit/system/MASTER_FUNCTION_INVENTORY.md` and add the exact-VND T306 evidence/commit to
  `docs/audit/system/FULL_SYSTEM_TRACEABILITY_MATRIX.md`.

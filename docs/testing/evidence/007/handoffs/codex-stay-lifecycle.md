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
| T307 | `a5baf5c` | Database preview/recheck 3/3; HTTP permission/tenant 3/3 | `docs/testing/evidence/007/remediation/T307-authoritative-checkout-preview.md` |
| T313 | `c09aedd` | Backend compile and 5/5; frontend 7/7; Angular development build | `docs/testing/evidence/007/remediation/T313-verified-stay-reviews.md` |

## Partial Tasks

| Task | Commit | Focused validation | Remaining gate |
|---|---|---|---|
| T302 | `8834080` | Backend policy/locking/IDOR/HTTP PASS; frontend 16/16 | Authenticated real API/browser journey is blocked because no usable local staff demo credential is configured. Evidence: `docs/testing/evidence/007/remediation/T302-check-in-readiness.md`. |
| T303 | `c6a8340` | Isolated backend compile; backend 6/6; frontend 10/10; Angular development build PASS | Authenticated real quote/create/assignment journey is blocked by the same missing local staff demo credential. Evidence: `docs/testing/evidence/007/remediation/T303-staff-booking.md`. |

## Blocked Tasks

| Task | Evidence | Stop gate |
|---|---|---|
| T308 | `docs/testing/evidence/007/remediation/T308-debt-overpayment-policy-gate.md` | Missing owner-approved debt limits/approval rules and overpayment refund-versus-credit/accounting policy; current behavior remains fail-closed. |

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
- Mark T307 complete in `specs/007-payment-billing-completion/tasks.md`.
- Promote STAY-017 to `COMPLETE_VERIFIED` in
  `docs/audit/system/MASTER_FUNCTION_INVENTORY.md` and add T307 HTTP/database evidence to
  `docs/audit/system/FULL_SYSTEM_TRACEABILITY_MATRIX.md`.
- Keep T308 open and classify STAY-018 as `BLOCKED_EXTERNAL` in the aggregate
  inventories until the versioned debt/overpayment policy described in the handoff is approved.
- T310 was not edited because its mandatory invoice backend/customer/admin files overlap
  the active exclusive T309 claim. Re-run it after that claim is released.
- Mark T313 complete in `specs/007-payment-billing-completion/tasks.md` and promote
  STAY-025 in both aggregate inventory files using the T313 evidence and commit.

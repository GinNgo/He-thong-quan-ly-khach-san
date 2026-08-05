# T239 Claim Rejection And Cancellation Evidence

## Result

- Status: `COMPLETE_VERIFIED`
- Code commit: `4dc51df`
- Scope: PROP-SUB-012 / FR-044 / SC-013

## Implemented Contract

- Admin rejection accepts a typed, trimmed reason of 10-500 characters and only a `PENDING` claim.
- Reject and cancel pessimistically lock the exact claim and require the matching pending OWNER mapping to be deactivated before mutating claim state.
- Requester cancellation uses a locked `(claimId, requesterUserId)` lookup, so foreign and missing claim identifiers have the same safe boundary.
- Rejection and cancellation retain claim/mapping history, remove stale owner authority, and leave the imported property status, approval status, and operation status unchanged.
- Partner registration status exposes the exact nullable `claimId` and `claimStatus`, distinguishes `REJECTED` from `CANCELLED`, and preserves the rejection reason after reload.
- Admin UI uses an inline rejection editor with validation, busy protection, canonical response checks, and safe 400/403/404/409 messages.
- Requester UI offers cancellation only for the authenticated requester's exact pending claim, prevents double submit, verifies `CANCELLED`, then reloads durable status.

## Verification

- Backend production compile: PASS through the target-only compatibility harness necessitated by unrelated missing subscription catalog classes in the base branch.
- Backend focused tests: 74/74 PASS, 0 failures, 0 errors, 0 skipped.
  - Property registration controller: 15.
  - Property claim controller: 17.
  - Claim persistence/rollback: 7.
  - Property claim service: 12.
  - Ownership lifecycle: 5.
  - Registration status: 18.
- Frontend focused tests: 24/24 PASS across property-claim service/admin queue and registration-status service/component suites.
- Angular development build: PASS with compile-only local i18n compatibility stubs; stubs were removed before commit. Without them, the base branch still fails only on its pre-existing missing `core/i18n/locale.service` and `core/i18n/public-i18n.service` imports.
- Independent final diff review: no blocking finding.
- `git diff --check`: PASS.
- `backend/target`: cleaned after verification.

## Coordinator Handoff

- Mark T239 complete in `specs/007-payment-billing-completion/tasks.md`.
- Merge PROP-SUB-012 completion/evidence into `docs/audit/system/MASTER_FUNCTION_INVENTORY.md`.
- Merge PROP-SUB-012 traceability into `docs/audit/system/FULL_SYSTEM_TRACEABILITY_MATRIX.md`.
- Do not infer uniqueness or cross-transition concurrency completion from this task; those invariants remain T240/PROP-SUB-014.

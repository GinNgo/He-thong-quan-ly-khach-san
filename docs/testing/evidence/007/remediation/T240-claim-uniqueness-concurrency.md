# T240 Claim Uniqueness And Concurrency Evidence

## Result

- Status: `COMPLETE_VERIFIED`
- Code commit: `4b4f034`
- Scope: PROP-SUB-014 / FR-044 / SC-013

## Implemented Invariants

- Request, approve, reject and cancel acquire locks in the same deterministic order: property, claim, then OWNER mapping.
- Request locks the property before checking pending claims or creating the pending OWNER mapping, closing the request-versus-review phantom window.
- Domain and database races flush inside the transaction and return stable, safe 409 contracts without exposing index names or persistence details.
- V74 is additive and non-destructive. It checks required schema, rejects existing duplicate actionable rows with `THROW`, creates indexes idempotently, and verifies their exact metadata.
- V74 enforces one pending claim per `(property_id, requester_user_id)`, one OWNER mapping per `(user_id, hotel_id)` across lifecycle states, and one primary active OWNER per hotel.
- The migration does not impose one pending claimant per property and does not prevent future non-primary co-owners; those policies remain outside T240.

## Verification

- Backend production compile: PASS through target-only compatibility stubs required by unrelated missing subscription catalog types in the base branch.
- Backend focused tests: 41/41 PASS.
  - Property claim controller/conflict contract: 20.
  - Migration static contract: 1.
  - Spring/JPA concurrency integration: 3.
  - Property claim service: 12.
  - Ownership lifecycle: 5.
- Spring/JPA race tests use independent transactions, executor barriers and bounded timeouts. They verify same-requester duplicate submission, different-requester coexistence, approve-versus-cancel one-winner behavior, no deadlock, and final claim/property/OWNER invariants.
- Disposable SQL Server 2022 verifier passed clean/repeat/duplicate-fail-closed/checksum checks before the final metadata-only included-column rejection was added. The final hardening is covered by the static migration contract test; its executable rerun was blocked when the local Docker daemon became unresponsive, so that last rerun is not claimed.
- Frontend focused tests: 21/21 PASS for stable conflicts, stale cross-tab review, safe error details and double-submit guards.
- Independent final review: no blocking finding.
- `git diff --check`: PASS.
- Temporary compile stubs, SQL containers and `backend/target` were removed after verification.

## Rollout And Recovery

- If V74 duplicate preflight throws, stop rollout and investigate the duplicate rows. Do not update, delete, merge or auto-deduplicate them in this migration.
- A failed preflight leaves row count and checksum unchanged. After an approved data-reconciliation process, rerun the idempotent migration.
- Coordinator must preserve the V74 namespace during branch integration.

## Coordinator Handoff

- Mark T240 complete in `specs/007-payment-billing-completion/tasks.md`.
- Merge PROP-SUB-014 completion/evidence into `docs/audit/system/MASTER_FUNCTION_INVENTORY.md`.
- Merge PROP-SUB-014 traceability into `docs/audit/system/FULL_SYSTEM_TRACEABILITY_MATRIX.md`.
- Keep T241 ownership/co-owner/transfer/subscription responsibility blocked until business policy is approved.

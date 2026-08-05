# T244 Plan Version Governance Evidence

## Result

- Status: `COMPLETE_VERIFIED`
- Code commit: `4dd6f8b`
- Scope: PROP-SUB-024 / FR-044 / SC-013

## Implemented Contract

- System admins with `PLATFORM_BILLING:UPDATE` create new INACTIVE plan versions; published or retired versions are never edited in place or reactivated.
- Creation validates unique codes, family/version identity, whole positive VND price, supported billing/duration combinations, known feature keys and limits of `-1` or non-negative values.
- Activation locks the whole plan family in deterministic order, deactivates and flushes the prior ACTIVE version first, then activates the target, satisfying the filtered unique index without a transient duplicate.
- Activation/deactivation idempotency is persisted and bound to operation key, target, action, request payload and reason. Same-state operations bind a terminal replay rather than leaving keys reusable.
- Explicit deactivation requires a 10-1000 character reason. Prior-version auto-deactivation and explicit transitions both emit auditable reasons.
- V89 enforces one ACTIVE version per family, unique feature code per plan and governed operation uniqueness with schema/duplicate preflight.
- Existing subscription orders and software contracts retain their immutable price/term/feature/version snapshots across catalog version transitions.
- Initializers only create missing seed versions and never overwrite published catalog rows. Legacy FREE is inactive/non-purchasable and public catalog excludes non-positive prices.

## Verification

- Backend clean production compile: PASS.
- Backend focused tests: 10/10 PASS.
  - Plan administration/idempotency/locking/snapshots: 7.
  - V89 migration contract: 1.
  - Controller contracts and permission/body/header binding: 2.
- Frontend focused tests: 22/22 PASS.
- Migration collision scan: V89 is unique across registered worktrees at implementation time; V88 was rejected because another branch already owned it.
- Independent final review: no blocking finding.
- `git diff --check`: PASS.
- Temporary focused POM, i18n stubs and `backend/target` removed.

## Coordinator Handoff

- Mark T244 complete in `specs/007-payment-billing-completion/tasks.md`.
- Merge PROP-SUB-024 evidence into both shared aggregate inventories.
- Preserve V89 and the rule that catalog changes never rewrite existing order/contract snapshots.

# T241 Ownership Lifecycle Evidence

## Result

- Status: `COMPLETE_VERIFIED`
- Code commit: `92ef7f2`
- Scope: PROP-SUB-015 / FR-044 / SC-013
- The earlier policy stop gate is resolved by the approved ownership/subscription policy encoded in `spec.md`.

## Necessary Implementation

- Exactly one active primary owner is retained; co-owner memberships are hotel-scoped, capped by configuration and cannot duplicate an active membership.
- Primary-only invitations use a one-time random token with only its hash persisted, expire after seven days, grant no pending authority, require exact verified email and owner-terms acceptance, and apply a seven-day cooling period.
- Delivery occurs after commit. Successful delivery and automatic failure cancellation are audited with actor, reason, IP, user-agent and correlation context; failed delivery leaves a recoverable cancelled invitation so the primary can invite again.
- Primary transfer requires server-side password re-authentication, an eligible mature co-owner, two-party acceptance and a typed financial-readiness result. READY performs an atomic primary/co-owner swap; BLOCKED and UNAVAILABLE fail closed.
- Co-owner leave and primary removal are ACTIVE-only, require a 10-500 character reason, retain terminal history and preserve/remove the global owner role according to other active memberships.
- Session revocation, notifications and tenant audit accompany authority changes. Ownership transitions do not create, refund, prorate, renew or otherwise mutate the hotel's subscription or issued financial snapshots.

## Verification

- Backend production compile: PASS using target-only compatibility stubs for unrelated missing catalog classes in the base branch.
- Backend focused tests: 16/16 PASS.
  - Governance lifecycle: 13.
  - Invitation delivery transitions: 2.
  - V87 migration contract: 1.
- Frontend focused tests: 13/13 PASS across ownership service, management UI and invitation acceptance UI.
- Independent final review: no blocking finding in the necessary-only scope.
- `git diff --check`: PASS.
- `backend/target` and temporary frontend i18n stubs removed.

## Truthful Deferrals

- Invitation decline/resend UI/API and per-property `BILLING_ADMIN` administration.
- Hotel close/delete/CLOSING workflow.
- Exceptional Super Admin debt-assumption override.
- Default production transfer remains `OWNERSHIP_FINANCIAL_READINESS_UNAVAILABLE` until authoritative overdue subscription invoice and dispute/chargeback sources are wired. The typed gateway and READY atomic path are implemented and tested; no unsupported source is treated as zero.

## Coordinator Handoff

- Mark T241 complete in `specs/007-payment-billing-completion/tasks.md`.
- Merge PROP-SUB-015 evidence into both shared aggregate inventories.
- Preserve V87 and the approved policy section in `spec.md`.
- Use T242/T243 to wire authoritative platform subscription/invoice/dispute readiness without changing ownership-transfer financial policy.

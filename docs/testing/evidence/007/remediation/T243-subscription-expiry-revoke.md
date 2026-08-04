# T243 Subscription Expiry And Revoke Evidence

## Result

- Status: `COMPLETE_VERIFIED`
- Code commits: `e505a7f`, `00c7409`
- Scope: PROP-SUB-022 / FR-044 / SC-013

## Implemented Contract

- Authoritative lifecycle is the hotel-scoped software contract, entitlement and append-only subscription history; legacy account scheduler rows do not drive platform truth.
- Non-lifetime ACTIVE subscriptions expire when `effectiveUntil <= Clock.now()` in UTC. One instant before remains ACTIVE and lifetime subscriptions never auto-expire.
- Expiry takes precedence over explicit revoke at or after the boundary, preventing scheduler timing from changing the legal terminal state.
- Expiry/revoke lock hotel, entitlement and contract deterministically and atomically preserve status parity plus one history transition. Replay is idempotent and concurrent terminal transitions have one winner.
- Explicit revoke requires `PLATFORM_BILLING:UPDATE`, selected assigned property and a bounded reason. Audit includes actor, reason, IP, user-agent and correlation; property sessions are invalidated.
- Lifecycle transitions never refund, prorate, mutate orders/payments, or create replacement entitlements.
- Assigned users retain terminal history/export even for suspended properties; foreign properties use a hidden boundary. CSV fields are minimized and formula-injection neutralized.

## Verification

- Backend production compile: PASS.
- Backend focused tests: 16/16 PASS.
  - Lifecycle boundary/lifetime/revoke race: 6.
  - Entitlement terminal reads: 5.
  - Scheduler isolation: 1.
  - History privacy/formula export: 2.
  - Controller contract/headers: 2.
- Frontend combined T242/T243 tests: 24/24 PASS; final T243 focused suite: 16/16 PASS.
- Independent final review: no blocking finding.
- `git diff --check`: PASS.
- Temporary focused POM, i18n stubs and `backend/target` removed.

## Coordinator Handoff

- Mark T243 complete in `specs/007-payment-billing-completion/tasks.md`.
- Merge PROP-SUB-022 evidence into both shared aggregate inventories.
- Keep catalog administration/versioning isolated to T244.

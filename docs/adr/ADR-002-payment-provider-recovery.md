# ADR-002: In-Process Payment Provider Recovery

- **Status**: Accepted
- **Date**: 2026-07-30
- **Decision owners**: Product and engineering
- **Scope**: T100 MoMo/ZaloPay query and refund recovery

## Context

The application already persists payment sessions, refund requests, provider attempts, transition rules, and pessimistic locks. MoMo and ZaloPay can miss callbacks or return asynchronous refund results, so the system needs server-side query recovery without trusting browser return parameters.

## Decision

Use a bounded Spring scheduled service inside the existing backend.

- Query pending MoMo/ZaloPay payment sessions and feed terminal results into the existing verified callback path.
- Submit refund requests with deterministic provider references and persisted server-owned amount/original transaction ID.
- Query pending refund references until a terminal provider result is available.
- Keep network calls outside lifecycle database transactions; perform each state change through existing locked services.
- Leave live credentials and public callback proof to T101.

## Alternatives Considered

### Message broker and dedicated payment worker

Rejected for the current scale. It adds deployment, delivery, outbox, monitoring, and operational complexity without a measured throughput requirement that exceeds a bounded scheduler.

### Query from the browser return page

Rejected. The browser is not an authority for amount, ownership, provider reference, or terminal financial state and may be closed or tampered with.

### Mark refund success immediately after local cancellation

Rejected. A local negative ledger is not proof that an external provider refund succeeded.

## Consequences

The design is deployable with the current monolith and schema. Deterministic provider IDs and existing database locks provide replay safety, but a multi-instance deployment may still send duplicate network requests; provider idempotency makes those safe. If provider volume, scan latency, or operational isolation becomes measurable pain, move the same command/query contracts behind an outbox-backed worker in a later ADR.

## Rollout and rollback

Roll out with recovery disabled in test/E2E and enabled only where provider credentials are configured. Rollback disables `PAYMENT_PROVIDER_RECOVERY_ENABLED`; persisted sessions and refund attempts remain available for manual reconciliation or a later deployment.

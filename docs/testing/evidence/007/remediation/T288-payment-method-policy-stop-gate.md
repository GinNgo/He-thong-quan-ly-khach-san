# T288 Payment Method Policy Stop Gate

Task: `T288`
Capability: `PUB-021`
Status: `BLOCKED_PAYMENT_POLICY_AND_CREDENTIALS`

## Decision

Implementation is stopped because `PAY_AT_HOTEL` has no approved mapping, deposit interaction or reservation/hold lifecycle. Choosing one changes inventory expiry and financial recognition. Online provider readiness is also externally blocked outside the existing simulator because property-scoped credentials are unavailable.

## Conflicting Current Behavior

- Checkout hard-codes methods including unsupported `STRIPE`; backend configuration uses a different method taxonomy.
- No sanitized public readiness endpoint exists; the management response must not be exposed.
- Reservation creation checks only globally enabled configuration, not whether the submitted method is configured, enabled and ready, then stores caller text.
- `PAY_AT_HOTEL` skips payment-attempt creation in the UI but still creates `PENDING_PAYMENT` inventory with an expiring hold.
- Search always reports pay-at-property false.

## Required Decisions

1. Map `PAY_AT_HOTEL` to CASH, CARD_TERMINAL, both, or a separate capability.
2. Define deposit handling for pay-at-property.
3. Define reservation status, hold expiry/release and cancellation/no-show guarantee.
4. Define when local money is recorded as collected.
5. Define per-method readiness independence from unrelated online-provider configuration.

## Credential Boundary

The simulator can be tested and must be labeled simulator. Sandbox/production VNPay, MoMo and ZaloPay readiness lacks a property-scoped secret resolver; no production credential, callback or real transaction is authorized.

## Resume Condition

After policy approval, expose a sanitized per-method public contract, validate the selected method atomically under the booking lock, keep local readiness independent where approved, hide disabled/unready methods and test zero-mutation rejection plus concurrent configuration changes.

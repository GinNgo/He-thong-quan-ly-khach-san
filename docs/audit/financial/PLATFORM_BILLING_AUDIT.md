# Platform Billing Audit

Feature `007-payment-billing-completion` keeps owner-to-platform subscription money in the Platform Billing bounded context. Property Commerce reservations never activate a platform contract, and platform callbacks never settle a reservation.

## Lifecycle Status

| Capability | Status | Evidence |
|---|---|---|
| Backend catalog snapshot and versioned price | COMPLETE_VERIFIED | T093, `subscription-order-creation.md`, `subscription-order-tests.md` |
| Expiring purchase/renewal/upgrade order | COMPLETE_VERIFIED | T093, T097, T098 |
| System-merchant-only attempt creation | COMPLETE_VERIFIED | T094, `platform-merchant-attempt-readiness.md` |
| Signature, merchant, amount, currency, reference and expiry verification | COMPLETE_VERIFIED | T095, T102, `platform-payment-callback.md`, T110 negative journey |
| Exactly-once ledger transaction | COMPLETE_VERIFIED | T095, T103, `platform-callback-concurrency.md` |
| Contract, entitlement and history application | COMPLETE_VERIFIED | T096, `subscription-application.md`, T103 |
| Renewal and approved upgrade policy boundaries | COMPLETE_VERIFIED | T097, T098, `subscription-renewal.md`, `subscription-upgrade.md` |
| Downgrade/proration behavior | BLOCKED_EXTERNAL | T099 and T110; no approved policy is configured |
| Customer-facing purchase/attempt status UI | COMPLETE_VERIFIED | T106, T107, T108, `platform-payment-panel.md` |
| Owner registration to activation browser journey | COMPLETE_VERIFIED | T109, `platform-subscription-purchase.md` |
| Tamper/replay/cancel/unsupported-policy browser coverage | COMPLETE_VERIFIED | T110, `platform-subscription-negative.md` |
| Production merchant enablement | BLOCKED_EXTERNAL | Production approval and credentials are intentionally absent |

## Provider Integration Variables

The browser journeys and local callback boundary use synthetic simulator values only:

- `LUXESTAY_E2E_ADMIN_USERNAME` and `LUXESTAY_E2E_ADMIN_PASSWORD`: an admin account allowed to approve the temporary property fixture.
- `LUXESTAY_E2E_PLATFORM_MERCHANT_ID`: the configured simulator merchant identity.
- `LUXESTAY_E2E_PLATFORM_SIGNING_SECRET`: the simulator HMAC secret; never commit its value.
- `LUXESTAY_E2E_API_URL`: optional API prefix when the backend is not at `http://localhost:8080/api`.
- `LUXESTAY_E2E_PLATFORM_ORDER_EXPIRY_MINUTES=1`: optional short-expiry profile used only for T110 expiry evidence.

The production configuration remains disabled unless a separate readiness approval supplies complete secrets, merchant identity, callback registration, monitoring and rollback evidence.

## Verified Invariants

- The server snapshots plan price, duration, billing period and feature limits into each order; the client cannot submit a price or entitlement effect.
- Every attempt binds to the configured system merchant, exact VND amount, provider reference, environment and expiry.
- Repeated or concurrent equivalent callbacks produce one platform ledger transaction and one contract/entitlement transition.
- Failed, expired, tampered, cancelled or policy-blocked flows create no successful platform revenue and activate no entitlement.
- Callback errors return stable financial codes and do not disclose secrets or full merchant identifiers.

## Open Decisions

- Approve downgrade and proration rules before implementing or enabling automatic downgrade/refund credits (OQ-002/OQ-003).
- Approve production merchant credentials and operational readiness before any production adapter is enabled (OQ-004/OQ-005).
- Keep T112-T124 refund work separate from Platform Billing activation; a platform refund must not mutate Property Commerce records.

# T293 Booking And Payment Recovery Evidence

Task: `T293`
Capability: `PUB-028`
Status: `BLOCKED_PROVIDER_CONTRACT_AND_RUNTIME`

## Implemented Safe Slice

- Minimal 24-hour recovery state is bound to the current user and room type and stores only reservation ID, opaque attempt ID, method and phase.
- Reload verifies the owner-authorized reservation first, then the payment attempt; mismatched reservation/attempt IDs are rejected.
- Explicit phases distinguish reservation created, payment pending, payment success, payment failed and payment expired.
- Pending recovery resumes the existing payment panel/polling path.
- Terminal retry reuses the existing reservation and creates only a new payment attempt; it never submits another booking.
- QR payload, receiver data, provider token and other financial data are not persisted in browser storage.

## Focused Verification

| Target | Result |
|---|---|
| Booking checkout + recovery service specs | 2 files, 10/10 PASS after final template change |
| Angular development build | Final bundle generated successfully after the template change (`dist/frontend/browser/index.html` timestamp 19:55:58) |
| `git diff --check` | PASS |

Tests cover user/room/expiry rejection, owner-authorized reload recovery, reservation/attempt matching, phase restoration and terminal retry without rebooking.

## Remaining Boundaries

- T288 has not approved which pay-at-property methods create attempts or how their terminal lifecycle behaves.
- No provider-specific return/callback route contract is approved, so a live redirect return cannot be invented.
- Pending/success/failure/expiry browser journeys remain blocked by the shared Playwright backend startup timeout.

## Security And Safety

Recovery treats URL/storage IDs only as hints and re-authorizes every resource through server APIs. Cross-user/expired/invalid state is cleared. No booking/payment mutation, production credential or provider network call was used in validation.

## Promotion Condition

Land T288/provider return contracts, repair the E2E runtime and pass reload, redirect return, terminal retry, expiry, cross-user/tampered ID and double-tab browser journeys before promoting PUB-028.

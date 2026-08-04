# T290 Same Room-Type Quantity Evidence

Task: `T290`
Capability: `PUB-025`
Status: `BLOCKED_RUNTIME`

## Verified Contract

- Quantity N creates exactly one reservation detail and one active hold with matching quantity.
- Server-owned subtotal/total scale with quantity and remaining authoritative availability decrements exactly N.
- Sold-out and concurrent last-N failures create no partial reservation/detail/hold state.
- Assignment requires exactly N unique rooms; N-1 and duplicate sets fail before mutation, exact replay is idempotent.
- A final invalid room rolls back earlier room state and creates zero assignments; cross-property access fails before mutation.

## Focused Verification

`SameRoomTypeQuantityIntegrationTest`: 4/4 PASS, 0 failures/errors/skips; Surefire 57.601s, command wall 107.3s.

The persisted success fixture proves quantity 2 over 3 rooms, one detail quantity 2, one hold quantity 2, subtotal 2,800,000, total 3,220,000 and remaining availability 1. Concurrent quantity-2 requests against the last two rooms produce exactly one success and one sold-out failure.

## Policy Boundaries

Fixtures use explicit capacities well within bounds, the existing synthetic enabled payment configuration and the already-authoritative server total. No T281 capacity fallback, T282 pricing formula, T288 pay-at-property or T291 mixed-cart rule is introduced.

## Browser Runtime Boundary

The quantity >1 detail-to-checkout/browser journey remains blocked by the shared Playwright backend startup timeout already evidenced for T279/T280. No redundant long runtime attempt was made.

## Safety

Test-only change; no schema or production behavior change. Temporary Maven overlay was removed and `git diff --check` passed.

## Promotion Condition

Repair the shared E2E runtime and pass desktop/mobile quantity preservation, single-submit success and stale sold-out recovery before promoting PUB-025 to `COMPLETE_VERIFIED`.

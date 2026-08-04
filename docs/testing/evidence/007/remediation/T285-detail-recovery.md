# T285 Detail Recovery Evidence

Task: `T285`
Capability: `PUB-018`
Status: `BLOCKED_RUNTIME`

## Implemented Contract

- Route and query replacements cancel active property/room requests; late stale responses cannot overwrite the latest state.
- A stay-query refresh immediately clears prior rooms and selection and exposes a non-actionable refresh state.
- Transient room failures provide a room-only retry using the latest route/query snapshot.
- Room-catalog 404 keeps the generic page-level not-found boundary and hides stale property content.
- Component destruction cancels active requests and the pending scroll timer.
- Capacity and pricing formulas are unchanged, preserving the T281/T282 policy stop-gates.

## Focused Verification

| Command / suite | Result |
|---|---|
| Hotel-detail Angular/Vitest target | 1 file, 9/9 PASS; Vitest 27.48s, wall 91.6s |
| Angular development build | PASS; bundle 58.223s, wall 75.2s |
| `git diff --check` | PASS |

The matrix includes invalid route, property not-found, stale room-catalog 404, date forwarding, latest-route wins, latest-query stale clearing, room retry and destroy cancellation.

## Browser Runtime Boundary

The required seeded desktop/mobile journey is registered as remaining evidence rather than claimed complete. This branch already reproduced the shared Playwright backend `webServer` timeout for T279 and T280, including a bounded 180-second attempt with zero product assertions. No further duplicate runtime attempt was made for T285.

## Safety

- Public read state only; no tenant mutation, financial calculation or migration changed.
- Temporary i18n test overlays were removed and no task-owned process remains.

## Promotion Condition

Repair the shared E2E backend runtime, then pass seeded desktop/mobile property detail, stay-query refresh, transient room retry and not-found recovery cases before promoting PUB-018 from `PARTIAL` to `COMPLETE_VERIFIED`.

# T286 Authentication Return Recovery Evidence

Task: `T286`
Capability: `PUB-019`
Status: `BLOCKED_RUNTIME`

## Implemented Contract

- A reusable sanitizer accepts only local absolute router URLs and rejects schemes, scheme-relative URLs, backslashes, control characters and malformed values.
- The booking guard preserves the full route/query/fragment for unauthenticated users and requires the `CUSTOMER` role for booking routes.
- Login preserves and sanitizes the return URL, honors already-authenticated customer recovery and gives non-customer booking access a truthful forbidden result.
- Login/register links and registration-success navigation retain the sanitized checkout return URL.
- Failed refresh from a booking route logs out and preserves the complete return state for one recovery navigation.
- No caller-controlled price/capacity value is promoted to authority; T287 remains responsible for canonical checkout context.

## Focused Verification

| Command / suite | Result |
|---|---|
| Focused Angular/Vitest target | 8 files, 25/25 PASS |
| Angular development build | PASS; application bundle 67.086s, wall 86.3s |
| `git diff --check` | PASS |

Coverage includes safe/unsafe return URLs, guard role behavior, exact booking query preservation, expired-session interceptor recovery, login/register round trip and existing account/registration contracts.

## Browser Runtime Boundary

The complete anonymous detail-to-login/register-to-checkout and expired-session browser journeys remain unexecuted because T279/T280 already established the shared Playwright backend `webServer` timeout. No redundant long runtime attempt was made.

## Security And Safety

- External/open redirects and path-confusion inputs fail closed to a local fallback.
- Backend CUSTOMER authorization is not weakened.
- No booking, payment, tenant mutation, migration or production credential is involved.
- Temporary i18n overlays were removed.

## Promotion Condition

Repair the shared E2E runtime and pass customer login, register round-trip, expired-session recovery and malicious-return rejection against the complete preserved checkout state before promoting PUB-019.

# T283 Public Property Detail Eligibility Evidence

Task: `T283`
Capability: `PUB-015`
Status: `COMPLETE_VERIFIED`

## Implemented Contract

- `GET /api/v1/hotels/public/{id}` resolves the property through `PublicInventoryEligibilityPolicy` instead of the generic management lookup.
- Only approved, operationally active properties are returned.
- Missing, draft, pending, rejected, suspended, closed, maintenance, out-of-service and otherwise ineligible properties share the same 404 boundary.
- Production demo inventory is hidden unless the existing explicit demo-public flag allows it.
- Successful public detail responses use `Cache-Control: no-store` so stale eligibility is not cached.

## Focused Verification

| Command / suite | Result |
|---|---|
| `HotelControllerIntegrationTest` | 8/8 PASS in 22.276s |
| `PublicInventoryEligibilityPolicyTest` | 6/6 PASS in 0.818s |
| Focused backend aggregate | 14/14 PASS; 0 failures/errors/skips |
| `git diff --check` | PASS |

The controller test proves canonical-policy delegation, `no-store`, DTO response, generic 404 and that the management lookup is never used. The policy matrix covers all known approval/operation states plus missing and production-demo allow/deny behavior.

## Frontend Build Boundary

The unchanged hotel-detail unit target and development build were attempted once. Both stopped before test execution at the known missing parallel i18n sources (`locale.service.ts` and `public-i18n.service.ts`): focused tests 0 executed after 50.6s; build stopped after 74.8s. No T283-specific frontend diagnostic appeared, no overlay was created and no frontend file changed.

## Security And Data Safety

- Public callers receive no hidden approval, operation or demo-rejection reason.
- This is a public read boundary; tenant mutation and financial behavior are unchanged.
- No migration, production credential or destructive operation is involved.

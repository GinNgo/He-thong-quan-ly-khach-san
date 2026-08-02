# Full-System Error Expectation Catalog

Audit date: 2026-08-02

## Stable Envelope

Every JSON API error produced by controller advice, validation, permission interception or
Spring Security uses this additive, backward-compatible shape:

```json
{
  "status": 409,
  "code": "CONCURRENT_MODIFICATION",
  "message": "The resource changed concurrently; reload current state before retrying.",
  "correlationId": "client-or-server-generated-id",
  "fieldErrors": {},
  "retryable": true,
  "currentState": null,
  "path": "/api/example"
}
```

- `code` is the machine contract; UI text may be localized without branching on `message`.
- `correlationId` is echoed in the `X-Correlation-ID` response header and is sanitized to
  `A-Z`, `a-z`, digits, `.`, `_`, `:`, `-`, with a maximum length of 100 characters.
- `retryable=true` is emitted only for a classified safe retry. A mutation still requires its
  idempotency contract; generic HTTP 500 errors are never automatically retryable.
- `currentState` is populated when the domain can safely disclose the latest state. Clients must
  refresh when it is absent instead of inventing a transition.
- Provider callbacks that require a provider-specific acknowledgement body keep that external
  protocol contract; internal operator/customer APIs use the shared envelope.

## Common HTTP And Security Errors

| Error code | Safe UI meaning | HTTP | Field/current-state semantics | Mutation | Retry guidance | Audit/log expectation |
|---|---|---:|---|---|---|---|
| `UNAUTHORIZED` | Sign in again or correct credentials | 401 | Empty fields; no state | None | No automatic retry; refresh credentials | Security log/correlation only |
| `INVALID_AUTH_TOKEN` | Social identity token is invalid | 401 | Empty fields | None | Obtain a new provider token | Correlation only; token is never logged |
| `ACCESS_DENIED` | Current account cannot perform this action | 403 | Empty fields | None | No | Security denial evidence where supported |
| `FORBIDDEN_PERMISSION` | Required action permission is missing | 403 | Empty fields | None | No | Permission denial with correlation |
| `FORBIDDEN_FEATURE` | Current subscription does not enable the feature | 403 | Empty fields | None | Retry only after entitlement refresh/change | Feature-gate denial with correlation |
| `NOT_FOUND` | Resource is missing or intentionally hidden | 404 | Empty fields | None | Refresh list/context | Do not reveal cross-tenant existence |
| `INVALID_REQUEST` | Request values or business input are invalid | 400 | Empty fields unless a typed validator applies | None | Correct input | Correlation; no raw payload logging |
| `VALIDATION_FAILED` | One or more fields are invalid | 400 | `fieldErrors` contains safe field messages | None | Correct fields | Correlation; no sensitive values |
| `MALFORMED_REQUEST` | Request body cannot be parsed | 400 | Empty fields | None | Correct JSON/body | Correlation; parser detail is not exposed |
| `MISSING_PARAMETER` | A required query/form parameter is absent | 400 | Missing parameter in `fieldErrors` | None | Add parameter | Correlation |
| `INVALID_PARAMETER` | Query/path parameter has the wrong type/value | 400 | Invalid parameter in `fieldErrors` | None | Correct parameter | Correlation |
| `METHOD_NOT_ALLOWED` | Endpoint does not support this HTTP method | 405 | Empty fields | None | Use documented method | Correlation |
| `UNSUPPORTED_MEDIA_TYPE` | Request content type is unsupported | 415 | Empty fields | None | Use documented content type | Correlation |
| `CONFLICT` | Request conflicts with current state | 409 | State is absent unless authoritative | Transaction rolls back | Refresh, then decide explicitly | Domain audit where mutation was attempted |
| `DATA_CONFLICT` | Request conflicts with persisted uniqueness/integrity | 409 | Empty fields | Transaction rolls back | Correct request or refresh | Correlation; database detail is hidden |
| `SERVICE_UNAVAILABLE` | Required non-production integration/configuration is unavailable | 503 | Empty fields | None | After configuration/service recovery | Secret-safe operational log |
| `INTERNAL_ERROR` | Unexpected server failure | 500 | Empty fields | Transaction rolls back where transactional | No automatic retry | Error type/path/correlation only; message/cause hidden |

## Financial And Billing Errors

| Error code | Safe UI meaning | HTTP | Current-state semantics | Mutation | Retry guidance | Audit expectation |
|---|---|---:|---|---|---|---|
| `TENANT_ACCESS_DENIED` | Financial resource is unavailable to this property/account | 403 | Hidden | None | No | Yes |
| `RESOURCE_NOT_FOUND` | Financial resource was not found | 404 | Absent | None | Refresh context | Yes |
| `INVALID_AMOUNT` | Amount is invalid | 400 | Absent | None | Correct amount | Yes |
| `INVALID_CURRENCY` | Only VND is supported | 400 | Absent | None | Correct currency | Yes |
| `INVALID_STATE_TRANSITION` | Action is not allowed in the current state | 409 | Authoritative state when available | None | Refresh state; do not blind retry | Yes |
| `OUTSTANDING_BALANCE` | Balance must be resolved before checkout | 409 | Invoice/payment state when available | None | Add payment or approved override | Yes |
| `OVERPAYMENT_REQUIRES_RESOLUTION` | Overpayment requires an explicit resolution | 409 | Invoice/payment state when available | None | Choose approved resolution | Yes |
| `IDEMPOTENCY_KEY_REUSED` | Request key was reused with different data | 409 | Existing result state when available | None | Use a new key only for a new intent | Yes |
| `CALLBACK_SIGNATURE_INVALID` | Provider confirmation cannot be verified | 401 | Attempt remains unchanged | None | Provider-controlled retry only | Yes |
| `CALLBACK_MERCHANT_MISMATCH` | Callback merchant is not the configured merchant | 400 | Attempt remains unchanged | None | Fix provider/configuration | Yes |
| `CALLBACK_AMOUNT_MISMATCH` | Callback amount differs from server-owned amount | 400 | Attempt remains unchanged | None | Investigate; no automatic retry | Yes |
| `CALLBACK_REFERENCE_MISMATCH` | Callback reference differs from the expected attempt | 400 | Attempt remains unchanged | None | Investigate; no automatic retry | Yes |
| `ATTEMPT_EXPIRED` | Payment attempt expired | 409 | `EXPIRED` when available | No new financial effect | Create a new attempt | Yes |
| `REFUND_EXCEEDS_BALANCE` | Refund exceeds refundable balance | 409 | Refund/payment state when available | None | Correct amount | Yes |
| `POLICY_NOT_CONFIGURED` | Required billing policy is not configured | 409 | Configuration state when safe | None | Configuration change only | Yes |
| `PAYMENT_ENVIRONMENT_DISABLED` | Payment environment is disabled | 503 | Environment when safe | None | Yes, after readiness recovery | Yes |
| `PRODUCTION_NOT_APPROVED` | Production payment has not been approved | 503 | Environment when safe | None | No automatic retry | Yes |
| `PROVIDER_UNAVAILABLE` | Payment provider is temporarily unavailable | 503 | Attempt state when safe | No unverified success | Safe idempotent retry | Yes |
| `CONCURRENT_MODIFICATION` | Financial resource changed concurrently | 409 | Latest state when safe | Losing transaction rolls back | Safe idempotent retry after refresh | Yes |
| `EXPORT_RECONCILIATION_MISMATCH` | Export failed reconciliation | 422 | Report reconciliation state | No file is released as valid | Rebuild after data/reconciliation review | Yes |

## Client Rules

1. Branch on `code`, never translated `message` text.
2. Display `fieldErrors` beside fields and preserve form input on 400 responses.
3. Offer retry only when `retryable=true`; for mutations reuse the same idempotency key and
   normalized payload unless the user starts a new intent.
4. On 409, use `currentState` when present; otherwise refresh the resource before enabling actions.
5. Show or copy `correlationId` in support details without exposing credentials, tokens or raw
   provider payloads.

# Full-System Error Expectation Catalog

| Error code | UI message | HTTP | Database mutation | Retry | Audit |
|---|---|---:|---|---|---|
| `TENANT_ACCESS_DENIED` | Access denied | 403/404 | None | No | Yes |
| `INVALID_AMOUNT` | Amount is invalid | 400 | None | Correct input | Yes |
| `INVALID_STATE_TRANSITION` | Action is not allowed in the current state | 409 | None | After state change | Yes |
| `IDEMPOTENCY_KEY_REUSED` | Request key was already used with different data | 409 | None | New key only | Yes |
| `CALLBACK_SIGNATURE_INVALID` | Payment confirmation could not be verified | 400/401 | None | Provider retry only | Yes |
| `ATTEMPT_EXPIRED` | Payment attempt has expired | 409 | No financial effect | Create new attempt | Yes |
| `OUTSTANDING_BALANCE` | Outstanding balance must be resolved | 409 | None | Add payment/authorized override | Yes |
| `REFUND_EXCEEDS_BALANCE` | Refund exceeds refundable balance | 409 | None | Correct amount | Yes |
| `POLICY_NOT_CONFIGURED` | This billing policy is not enabled | 409 | None | No | Yes |
| `PAYMENT_ENVIRONMENT_DISABLED` | Payment environment is disabled | 503 | None | Configuration only | Yes |

The catalog is expanded per inventory module during T154. Each final row must include UI text, HTTP status, error code, mutation/no-mutation assertion, retry safety and audit behavior.

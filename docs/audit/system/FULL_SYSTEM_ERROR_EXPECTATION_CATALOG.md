# Full-System Error Expectation Catalog

Audit date: 2026-08-03

This catalog is the executable expectation for the stable HTTP error contract. It is based on
`GlobalExceptionHandler`, authentication/security writers, `FinancialErrorCode`, the financial API
contract and the remediation evidence listed at the end of this file. The catalog describes what a
manual tester, UI client, operator and database reviewer should observe; it does not claim that an
audit row exists where the current implementation only writes an application log.

The two financial bounded contexts are evaluated separately: `PROPERTY_COMMERCE` owns tenant-scoped
reservations, folios, invoices, property payment attempts and property refunds; `PLATFORM_BILLING`
owns subscription orders, platform payment attempts, entitlements and platform refunds. Shared
provider adapters, idempotency and the error envelope are listed once, then mapped to both contexts.
`Verified` means current code plus a focused test/evidence path supports the expectation; `Partial`
means an observable path diverges; `Contract-only` means the enum/spec defines the behavior but no
executable throw/response assertion was found. A safe UI message below is expected fallback wording,
not a claim that every code already has a Vietnamese/English translation key.

## Stable Envelope

Controller advice, validation, permission interception and Spring Security return this additive
JSON shape for internal/operator/customer APIs:

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

- `code` is the stable machine contract. UI branches on `code`, never on translated `message`.
- `status` duplicates the HTTP status for clients that process a body outside the transport layer.
- `correlationId` is echoed in `X-Correlation-ID`, normalized to `A-Z`, `a-z`, digits, `.`, `_`,
  `:`, `-`, and limited to 100 characters. It is safe to show in support details.
- `fieldErrors` is an empty object unless validation or a typed parameter/header error supplies
  safe field-level messages.
- `currentState` is populated only when the domain can safely disclose authoritative state. A
  missing value means the client must refresh rather than infer a transition.
- `retryable=true` is emitted only for a classified safe retry. A mutation still needs its
  idempotency contract and the same normalized payload/key. Generic HTTP 500 is never retryable.
- Provider callback services normally return their provider acknowledgement DTO instead of this
  envelope. Exceptions raised before a callback result reaches the controller still use this shared
  envelope; that boundary difference is recorded below rather than treated as verified parity.

## Common HTTP And Security Errors

| Code | Safe UI message | HTTP | Fields/current state | Database mutation expectation | Retry | Audit/observability expectation |
|---|---|---:|---|---|---|---|
| `UNAUTHORIZED` | Sign in again or correct credentials. | 401 | Empty; no state | None | No automatic retry; refresh credentials | Security response contains correlation; handler/filter log only where configured; never log credentials/tokens |
| `ACCOUNT_DISABLED` | This account is inactive; contact an administrator. | 401 | Empty; no state | None | No; administrator must reactivate account | Security correlation and account-status evidence; do not expose account details |
| `INVALID_AUTH_TOKEN` | The social sign-in token is invalid; obtain a new provider token. | 401 | Empty; no state | None | Obtain a new provider token | Correlation only; token and provider payload are never logged |
| `ACCESS_DENIED` | Your account cannot perform this action. | 403 | Empty; no state | None | No | Security denial/correlation where supported; no sensitive authorization details |
| `FORBIDDEN_PERMISSION` | You do not have the required permission. | 403 | Empty; no state | None | No; permission change is required | Permission denial with correlation; UI may route to forbidden page using `code` |
| `FORBIDDEN_FEATURE` | Your subscription does not include this feature. | 403 | Empty; no state | None | Only after entitlement refresh/change | Feature-gate denial with correlation; do not trust a client-supplied feature limit |
| `NOT_FOUND` | The requested resource is unavailable. | 404 | Empty; cross-tenant resources stay hidden | None | Refresh list/context | Correlation; do not reveal whether another tenant owns the identifier |
| `INVALID_REQUEST` | Correct the request values and try again. | 400 | Empty unless a safe typed validator applies | None; runtime transaction rolls back if already open | No; correct input | Handler warning with code/path/correlation; raw payload is not logged |
| `VALIDATION_FAILED` | One or more fields need correction. | 400 | `fieldErrors` contains safe validator messages | None | No; preserve input and correct fields | Correlation; no sensitive field values |
| `MALFORMED_REQUEST` | The request body is not valid JSON for this endpoint. | 400 | Empty; parser detail hidden | None | No; correct body/content | Correlation; parser exception detail is not exposed |
| `MISSING_PARAMETER` | Add the required request parameter. | 400 | Missing parameter in `fieldErrors` | None | No; add parameter | Correlation; parameter name is safe to expose |
| `MISSING_HEADER` | Add the required request header. | 400 | Missing header in `fieldErrors` | None | No; add header (for example `Idempotency-Key`) | Correlation; header values are not logged |
| `INVALID_PARAMETER` | Correct the parameter value or type. | 400 | Invalid path/query parameter in `fieldErrors` | None | No; correct parameter | Correlation; no raw request dump |
| `METHOD_NOT_ALLOWED` | Use the documented HTTP method for this endpoint. | 405 | Empty; no state | None | No; use the documented method | Correlation |
| `UNSUPPORTED_MEDIA_TYPE` | Use the documented request content type. | 415 | Empty; no state | None | No; send supported media type | Correlation |
| `CONFLICT` | The request conflicts with the current state; reload and decide explicitly. | 409 | `currentState` absent unless authoritative | Transaction rolls back for a runtime failure; no committed business mutation | No blind retry; refresh first | Handler warning with code/path/correlation; domain audit only if the service explicitly appends one |
| `PROPERTY_NOT_OPERATIONAL` | Property operations require approval and active operation status. | 409 | `approval=<status>;operation=<status>` | None | No until property state changes | Correlation and operational denial evidence; no financial effect |
| `DATA_CONFLICT` | The request conflicts with existing data. | 409 | Empty; database details hidden | Integrity transaction rolls back; no partial business mutation | No; correct data or refresh | Handler warning with correlation; never expose constraint/SQL detail |
| `USERNAME_ALREADY_EXISTS` | This username is already registered. | 409 | `fieldErrors.username` identifies the conflict | Registration is not committed; uniqueness-race transaction rolls back | No; choose another username | Correlation in the stable response; no persistent rejection audit is guaranteed |
| `EMAIL_ALREADY_EXISTS` | This email is already registered. | 409 | `fieldErrors.email` identifies the conflict | Registration is not committed; uniqueness-race transaction rolls back | No; use another email or sign in | Correlation in the stable response; no persistent rejection audit is guaranteed |
| `SERVICE_UNAVAILABLE` | The required sign-in or service configuration is temporarily unavailable. | 503 | Empty; no state | No business mutation | Retry after configuration/service recovery only | Secret-safe operational log with correlation; exception text must not contain credentials |
| `INTERNAL_ERROR` | An unexpected error occurred. Contact support with the correlation ID. | 500 | Empty; no state | Open business transaction rolls back; idempotency failure bookkeeping may be independent | No automatic retry | Error type/path/correlation only; cause and message are redacted |

## Financial And Billing Errors

The financial enum is the source of HTTP status and retry classification. Internal financial APIs
use the shared envelope; callback-service results normally return
`{ accepted, replayed, errorCode, ... }` with the same status/code and no customer JWT requirement.

| Code | Safe UI message | HTTP | Fields/current state | Database mutation expectation | Retry | Audit expectation |
|---|---|---:|---|---|---|---|
| `TENANT_ACCESS_DENIED` | This property's financial resource is unavailable to your account. | 403 | Hidden; do not disclose tenant state | None | No | Financial handler warning with correlation; persistent denial audit is not guaranteed |
| `RESOURCE_NOT_FOUND` | The financial resource was not found. | 404 | Absent; cross-tenant access remains indistinguishable from missing | None | Refresh context | Handler warning only for ordinary API reads. Platform unknown callbacks append an audit event; the property endpoint can reject in its resolver before the callback audit (`Partial`). |
| `INVALID_AMOUNT` | Enter a valid positive VND amount. | 400 | Absent or safe amount field error | None | No; correct amount | Handler warning; no financial audit row unless the calling workflow explicitly records a rejected action |
| `INVALID_CURRENCY` | Only VND payments are supported. | 400 | Absent or safe currency field error | None | No; correct currency | Handler warning; callback verification rejection is auditable when processed by callback service |
| `INVALID_STATE_TRANSITION` | This action is not allowed in the current financial state. | 409 | Authoritative state when supplied | None; transaction rolls back | No blind retry; refresh state | Callback reject paths append a financial audit event; ordinary service failures are handler-log only |
| `OUTSTANDING_BALANCE` | Settle the outstanding balance before checkout. | 409 | Invoice/folio state when supplied | Checkout transaction rolls back; no invoice finalization, room or housekeeping mutation | No automatic retry; add payment or approved override | Handler warning; checkout audit is written only for a committed override/operation |
| `OVERPAYMENT_REQUIRES_RESOLUTION` | Choose an approved overpayment resolution before checkout. | 409 | Invoice/payment state when supplied | Checkout transaction rolls back; no final invoice or operational state mutation | No blind retry; choose resolution explicitly | Handler warning; resolution/override audit is expected only after a committed choice |
| `IDEMPOTENCY_KEY_REUSED` | This request key was already used for different data. | 409 | Existing idempotency identity/result may be disclosed safely | Existing idempotency row/result remains unchanged; no business mutation | No; use a new key only for a new intent | Handler warning with correlation; idempotency ledger is the authoritative evidence |
| `CALLBACK_SIGNATURE_INVALID` | Provider confirmation could not be verified. | 401 | Attempt remains unchanged; a callback-service rejection DTO has `accepted=false`; a pre-result failure uses the shared envelope | No attempt/transaction/entitlement mutation | Provider-controlled retry only after a corrected signature | Callback-service rejection appends redacted financial audit; resolver/adapter-only failures are correlation-log/test evidence only |
| `CALLBACK_MERCHANT_MISMATCH` | Provider merchant does not match the configured merchant. | 400 | Attempt remains unchanged | No financial mutation | No automatic retry; fix provider/configuration | Callback reject audit with code, provider and correlation; secrets/signatures redacted |
| `CALLBACK_AMOUNT_MISMATCH` | Provider amount does not match the server-owned amount. | 400 | Attempt remains unchanged | No financial mutation | No automatic retry; investigate | Callback reject audit with expected/result metadata (no secrets) |
| `CALLBACK_REFERENCE_MISMATCH` | Provider reference does not match this payment attempt. | 400 | Attempt remains unchanged | No financial mutation | No automatic retry; investigate | Callback-service rejects append an audit. A property resolver rejection can occur before unknown-callback audit persistence (`Partial`). |
| `ATTEMPT_EXPIRED` | This payment attempt has expired; create a new attempt. | 409 | `EXPIRED` when safely known | Property attempt/manual confirmation does not save a new effect. Platform attempt creation marks the order `EXPIRED` before throwing, but the enclosing runtime transaction rolls that change back; no committed order/attempt mutation is expected. | No; create a new attempt/key | Callback reject audit when callback reaches an attempt; ordinary expiry validation is handler-log only |
| `REFUND_EXCEEDS_BALANCE` | The requested refund exceeds the refundable balance. | 409 | Refund/payment balance when supplied | No refund request or ledger transaction is committed | No; correct amount | Handler warning; refund audit exists only for a committed request/approval |
| `POLICY_NOT_CONFIGURED` | The required billing policy is not configured. | 409 | Safe configuration blocker may be in `fieldErrors`/state | No order, entitlement, configuration or downgrade mutation | No; configuration change is required | Handler warning; configuration mutation audit is separate and only on commit |
| `PAYMENT_ENVIRONMENT_DISABLED` | Payments are disabled for this property or platform environment. | 503 | Safe environment/readiness state when supplied | No payment attempt or transaction; configuration validation is read-only | Yes after readiness/configuration recovery; reuse key for the same intent | Handler warning; readiness/configuration audit is separate |
| `PRODUCTION_NOT_APPROVED` | Production payments are not approved yet. | 503 | Safe environment/approval state | No production configuration or financial mutation | No automatic retry; obtain explicit approval | Handler warning and readiness evidence; never attempt a real provider call |
| `PROVIDER_UNAVAILABLE` | The payment provider is temporarily unavailable. | 503 | Attempt state when safe; no unverified success | No unverified transaction/entitlement effect | Yes when provider readiness returns; reuse the same idempotency key | Handler warning; persistent callback audit exists only when verification rejects after callback-service attempt resolution |
| `CONCURRENT_MODIFICATION` | The resource changed concurrently; reload current state and retry safely. | 409 | Latest state when safe; otherwise refresh | Losing business transaction rolls back. A persisted idempotency claim/replay/fail record may be managed in an independent transaction | Yes only with same key and normalized payload after refresh; never create a second intent | Handler warning; callback exceptions may roll back their joined audit transaction, while idempotency ledger/correlation remains evidence |
| `EXPORT_RECONCILIATION_MISMATCH` | Export failed reconciliation checks; review the report before retrying. | 422 | Report/reconciliation state | Contract expectation: no valid file is released and source financial evidence is unchanged. Current `FinancialReconciliationService` is read-only and returns rendered artifacts plus a mismatch queue; no production throw site or 422 assertion was found (`Contract-only`). | No automatic retry; rebuild after reconciliation review | Contract expects reconciliation/export evidence, but no persistent rejection-audit write was found (`Contract-only`) |

### Context Applicability And Evidence

The financial rows above are shared expectations. This matrix prevents evidence from one bounded
context from being silently promoted to the other:

| Stable code | `PROPERTY_COMMERCE` | `PLATFORM_BILLING` | Evidence anchor / boundary |
|---|---|---|---|
| `TENANT_ACCESS_DENIED` | Verified for tenant-scoped idempotency and property services | Not the normal platform boundary; platform uses explicit system scope and hides inaccessible resources as `RESOURCE_NOT_FOUND` | `FinancialIdempotencyService`; property tenant/security tests |
| `RESOURCE_NOT_FOUND`, `INVALID_AMOUNT`, `INVALID_CURRENCY`, `INVALID_STATE_TRANSITION` | Verified | Verified | Property/platform services plus provider contract and negative tests |
| `OUTSTANDING_BALANCE`, `OVERPAYMENT_REQUIRES_RESOLUTION` | Verified at checkout; no invoice/room/housekeeping commit on rejection | Not applicable | `CheckoutPreviewService`, `InvoiceFinalizationService`, checkout balance/transaction tests |
| `IDEMPOTENCY_KEY_REUSED`, `CONCURRENT_MODIFICATION` | Verified; same key/payload may replay, and an in-progress duplicate is retryable | Verified; same key/payload may replay, and an in-progress duplicate is retryable | `FinancialIdempotencyService`, `MutationIdempotencyService`, context concurrency tests |
| `CALLBACK_SIGNATURE_INVALID`, `CALLBACK_REFERENCE_MISMATCH` | Partial: service-returned rejects are auditable, but resolver failures can return the shared envelope before the callback service | Partial: service-returned acknowledgement and `auditUnknown` are verified, but pre-result exceptions still use the shared envelope | Property/platform callback services/controllers and provider contract tests |
| `CALLBACK_MERCHANT_MISMATCH`, `CALLBACK_AMOUNT_MISMATCH` | Verified after the callback service resolves the attempt | Verified after the callback service resolves the attempt | Property/platform callback and adapter contract tests |
| `ATTEMPT_EXPIRED` | Verified; no committed attempt/transaction effect | Verified; order expiry is rolled back when the service throws in its transaction | Property/platform payment-attempt tests |
| `REFUND_EXCEEDS_BALANCE`, `POLICY_NOT_CONFIGURED` | Verified; the rejected refund/configuration mutation does not commit | Verified; the rejected policy/refund mutation does not commit | Property/platform refund and subscription-policy tests |
| `PAYMENT_ENVIRONMENT_DISABLED`, `PRODUCTION_NOT_APPROVED`, `PROVIDER_UNAVAILABLE` | Partial for callback readiness failures because resolver/guard exceptions use the shared envelope and may have no financial audit row | Partial for callback readiness failures because `requireReady` exceptions occur before acknowledgement audit | Environment/configuration tests; callback readiness code paths |
| `EXPORT_RECONCILIATION_MISMATCH` | Contract-only; reconciliation returns mismatch data but no enum throw/422 response assertion | Contract-only; reconciliation returns mismatch data but no enum throw/422 response assertion | `FinancialReconciliationService`, reporting/reconciliation evidence |

## Provider Callback Acknowledgement

`POST /api/payment-providers/property/{provider}/callback` and
`POST /api/payment-providers/platform/{provider}/callback` are intended to preserve the external
acknowledgement contract. When the callback service returns a `CallbackResult`, a rejection uses
`FinancialErrorCode.status()` and the provider-specific body contains `accepted=false` and the enum
name in `errorCode`; an equivalent callback returns `accepted=true, replayed=true` without a second
ledger effect. Invalid signature, merchant, amount, reference, expiry, state and provider readiness
must leave unverified money, entitlement and invoice effects absent.

This is only partial at the HTTP boundary. Property credential/reference/readiness failures in
`PropertyPaymentCallbackCredentialsResolver`, and platform adapter/configuration failures before a
`CallbackResult`, throw into `GlobalExceptionHandler` and therefore return the shared
`ApiErrorResponse` instead of the provider acknowledgement DTO. The property resolver also rejects
an unknown attempt before `PropertyPaymentCallbackService.auditUnknown` can run. These are catalogued
gaps, not verified completion claims.

## Cross-Cutting Database And Retry Invariants

1. A 400/401/403/404/405/415 response must not create or update a business aggregate.
2. Runtime financial conflicts roll back the enclosing business transaction. The exception response
   itself is not proof that a database row was written.
3. `MutationIdempotencyService` claims, completes and fails its ledger in independent
   `REQUIRES_NEW` transactions. An in-progress duplicate can therefore return retryable
   `CONCURRENT_MODIFICATION` while the original business mutation is still running.
4. `IDEMPOTENCY_KEY_REUSED` means the stored identity remains bound to its original normalized
   request; do not overwrite it and do not retry with the same key and different data.
5. A retryable payment/provider failure never means success. The client may retry only after the
   provider/readiness condition is safe and must retain the same idempotency key for the same intent.
6. Audit metadata is redacted for passwords, secrets, tokens, signatures, authorization,
   credentials and key-like fields. Correlation IDs remain bounded and searchable.

## Evidence And Known Gaps

| Contract area | Current executable evidence |
|---|---|
| Shared envelope, HTTP mapping, registration conflicts, correlation sanitization and secret-safe 500 | `backend/src/main/java/com/hotel/controllers/GlobalExceptionHandler.java`, `backend/src/main/java/com/hotel/exceptions/RegistrationConflictException.java`, `AuthController.java`, `PermissionInterceptor.java`, `JwtAuthenticationEntryPoint.java`, `JwtAccessDeniedHandler.java`; `docs/testing/evidence/007/remediation/T189-stable-error-envelope.md` |
| Stable financial codes/status/retry flags | `backend/src/main/java/com/hotel/paymentprovider/error/FinancialErrorCode.java`; `specs/007-payment-billing-completion/contracts/financial-api-contract.md` (`Required Error Codes`) |
| UI parsing, correlation/idempotency headers and retry gating | `frontend/src/app/shared/financial/financial.models.ts`, `frontend/src/app/core/interceptors/financial-request.interceptor.ts`, `frontend/src/app/core/interceptors/error-interceptor.ts`; `docs/testing/evidence/007/remediation/T191-async-mutation-retry.md` |
| Property Commerce error paths | `PropertyPaymentAttemptService`, `PropertyPaymentCallbackCredentialsResolver`, `PropertyPaymentCallbackService`, `PropertyRefundService`, `CheckoutPreviewService`, `InvoiceFinalizationService`; property payment/callback/refund/checkout tests and evidence |
| Platform Billing error paths | `SubscriptionOrderService`, `PlatformPaymentAttemptService`, `PlatformPaymentCallbackService`, `PlatformRefundService`, subscription lifecycle services; platform order/payment/callback/refund tests and evidence |
| Provider rejection, replay and financial audit persistence | `PropertyPaymentCallbackService`, `PlatformPaymentCallbackService`, `FinancialAuditService`; `docs/testing/evidence/007/property-commerce/property-callback-concurrency.md`, `docs/testing/evidence/007/platform-billing/platform-callback-concurrency.md`, provider contract evidence |
| Operational audit boundary | `docs/testing/evidence/007/remediation/T213-operational-audit-log.md`; this is separate from financial callback audit |
| Remaining gaps | `EXPORT_RECONCILIATION_MISMATCH` is present in the enum/contract but no current production throw site or end-to-end 422 response assertion was found; current reconciliation returns artifacts and mismatch data. Callback failures raised before a callback-service result can use the shared envelope and lack the expected callback audit row. Ordinary validation, authorization, checkout preconditions and configuration/readiness failures generally have correlation logs rather than guaranteed persistent rejection-audit rows. |
| UI localization gap | The Angular client preserves stable `code`, `message`, `retryable` and `currentState` fields and adds correlation/idempotency headers. Only `PAYMENT_ENVIRONMENT_DISABLED` and `PRODUCTION_NOT_APPROVED` currently have matching i18n error keys; a complete code-to-message translation map remains open under T164. |

## Client Rules

1. Branch on `code`, never translated `message` text.
2. Display `fieldErrors` beside fields and preserve form input for 400 responses.
3. Offer retry only when `retryable=true`; for mutations reuse the same idempotency key and
   normalized payload unless the user starts a new intent.
4. On 409, use `currentState` when present; otherwise refresh before enabling actions.
5. For a provider acknowledgement body, inspect `accepted`, `replayed` and `errorCode`. Also handle
   the current partial path where a pre-result callback exception returns the shared error envelope.
6. Show or copy `correlationId` in support details without exposing credentials, tokens, signatures,
   payment secrets or raw provider payloads.

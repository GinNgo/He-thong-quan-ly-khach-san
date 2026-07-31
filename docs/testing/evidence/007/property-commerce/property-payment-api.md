# T058 Property Payment API

## Scope

- Added authenticated financial-summary, payment-attempt creation/status and cancellation endpoints.
- Added the management-only manual-transfer confirmation endpoint backed by the existing permission and tenant checks.
- Added the public provider callback endpoint backed by server-resolved environment, merchant identity and credentials.
- Added safe API response models that omit database identifiers, raw receiver JSON and all provider secrets.
- Added idempotent cancellation and automatic expiry when an active attempt is polled after its deadline.

## HTTP Contracts

- `GET /api/reservations/{reservationId}/financial-summary`
- `POST /api/reservations/{reservationId}/payment-attempts`
- `GET /api/payment-attempts/{attemptId}`
- `POST /api/payment-attempts/{attemptId}/cancel`
- `POST /api/management/payment-attempts/{attemptId}/confirm-manual`
- `POST /api/payment-providers/property/{provider}/callback`

Mutation endpoints require `Idempotency-Key` where the caller controls retries. The callback endpoint does not require a customer JWT, but it accepts only a provider payload/signature and resolves the expected attempt environment, merchant and secret from server configuration.

## Authorization and Safety

- Financial summary, attempt status and cancellation use the authenticated reservation-owner/property-access check.
- Cross-property and cross-account lookups retain resource-not-found semantics.
- Manual confirmation still requires `PROPERTY_PAYMENT_CONFIRM_MANUAL/APPROVE` and cannot be performed by the reservation owner.
- Request bodies cannot supply an authoritative amount, property ID, payment environment, expected merchant or verification credentials.
- Missing server-side credentials and disabled environments fail closed before callback orchestration.
- Invalid callbacks return stable financial error codes and create no financial mutation.
- Production enablement, production credentials and real-money calls remain disabled stop gates.

## Automated Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=PropertyPaymentControllerTest,PropertyPaymentCallbackCredentialsResolverTest,PropertyPaymentAttemptServiceTest,PropertyPaymentCallbackServiceTest,ManualTransferConfirmationServiceTest' -DforkCount=0 test
```

Final result:

- Tests run: 29
- Failures: 0
- Errors: 0
- Skipped: 0
- Build: SUCCESS

Coverage includes all six routes, safe response mapping, ignored caller-owned amount/property fields, server-only callback credentials, disabled/missing credential failures, reservation-owner reads, tenant hiding, expiry and idempotent cancellation.

Additional security regression command:

```powershell
.\mvnw.cmd '-Dtest=EndpointSecurityArchitectureTest,PropertyPaymentControllerTest' -DforkCount=0 test
```

Result: 5 tests passed with no failures, errors or skips.

## T064 Angular API Client

Added a typed `PropertyPaymentService` for:

- booking financial summary reads;
- server-priced payment-attempt creation;
- safe attempt status polling;
- idempotent customer/staff cancellation;
- permissioned management manual confirmation.

The create-attempt type exposes only `purpose` and `method`; it cannot carry an amount, property ID, receiver account, environment or merchant identity. Mutation methods accept optional idempotency/correlation headers while remaining compatible with the shared financial interceptor that supplies missing identities.

Focused command from `frontend/`:

```powershell
.\node_modules\.bin\ng.cmd test --watch=false --include "src/app/core/services/property-payment.service.spec.ts"
```

Result:

- Test files: 1 passed
- Tests: 5 passed
- Failures: 0
- Angular/Vitest build: SUCCESS
- Existing unrelated `NG8107` optional-chain warning remains in `client-layout.html`.

## T065 Checkout Attempt Integration

The public booking checkout now creates a Property Commerce payment attempt only after the reservation succeeds. The client sends the persisted reservation ID, `DEPOSIT` purpose and selected configured method; it does not send amount, property scope, receiver details, environment or merchant data.

Retry behavior preserves one payment idempotency key for the same reservation/purpose/method and reuses the existing reservation instead of booking a second room after a transient attempt failure. Pay-at-property bookings still complete without creating an online attempt.

Focused command from `frontend/`:

```powershell
.\node_modules\.bin\ng.cmd test --watch=false --include "src/app/features/client/booking-checkout/booking-checkout.component.spec.ts" --include "src/app/core/services/property-payment.service.spec.ts"
```

Result:

- Test files: 2 passed
- Tests: 10 passed
- Failures: 0
- Angular/Vitest build: SUCCESS
- Existing unrelated `NG8107` optional-chain warning remains in `client-layout.html`.

## T066 Accessible Payment Panel

The booking-success view now renders a responsive payment panel from the server-owned attempt response. It displays the exact VND amount, environment, status, expiry, masked receiver details, bilingual instructions, required transfer content and QR/provider state without accepting client-authored financial values.

Active attempts poll the read-only status endpoint. Poll failures expose an explicit status retry, while terminal `FAILED`, `EXPIRED` and `CANCELLED` attempts can request a new attempt with a fresh idempotency key against the existing reservation. This retry path does not call booking creation again and cannot duplicate inventory mutation.

Focused command from `frontend/`:

```powershell
.\node_modules\.bin\ng.cmd test --watch=false --include "src/app/features/client/booking-checkout/booking-checkout.component.spec.ts" --include "src/app/core/services/property-payment.service.spec.ts"
```

Result:

- Test files: 2 passed
- Tests: 11 passed
- Failures: 0
- Angular/Vitest build: SUCCESS
- Existing unrelated `NG8107` optional-chain warning remains in `client-layout.html`.

## Remaining Work

- T059-T064 are complete for legacy compatibility, provider contracts, callback concurrency, manual confirmation integration and the Angular payment client.
- T065 is complete for server-owned checkout attempt creation and safe retry identity.
- T066 is complete for accessible instructions, expiry, environment labeling, polling and safe terminal retry.
- T067-T069 remain open for dedicated panel unit coverage and browser journeys.

## Recovery

- This task adds no migration and does not rewrite immutable ledger evidence.
- The callback route can be disabled independently while preserving attempts, transactions and audit events.
- Safe recovery for transient cancellation/callback failures is replay with the same idempotency/provider identity.

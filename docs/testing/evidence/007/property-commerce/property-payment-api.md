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

## Remaining Work

- T059 remains open for read-only legacy payment compatibility during migration.
- T061 remains open for the complete provider signature/merchant/amount/currency/reference/expiry contract matrix.
- T062 remains open for database-backed replay and concurrent callback integration tests.
- T063 remains open for database-backed manual confirmation permission/audit/isolation tests.
- T064-T068 remain open for the Angular payment client, accessible payment panel and browser journeys.

## Recovery

- This task adds no migration and does not rewrite immutable ledger evidence.
- The callback route can be disabled independently while preserving attempts, transactions and audit events.
- Safe recovery for transient cancellation/callback failures is replay with the same idempotency/provider identity.

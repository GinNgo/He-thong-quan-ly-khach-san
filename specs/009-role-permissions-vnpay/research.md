# Research: Role Permissions and VNPay

## Decision 1: Extend the current bitmask rather than replace authorization

**Decision**: Add `TASK_EXECUTE=64` while retaining the existing six action bits and the `@Permission`/`PermissionInterceptor` path.

**Rationale**: `ActionCode`, `RolePermission.actionMask`, Angular `PermissionService` and existing migrations/tests already use compatible integer masks. A new non-overlapping bit is backward-compatible and keeps authorization centralized.

**Alternatives considered**:

- Replace bitmasks with one row per action: clearer relationally, but creates a broad migration with little value for this scope.
- Reuse `UPDATE`: rejected because editing fields and executing check-in/refund/payment transitions are materially different powers.
- Reuse `APPROVE`: rejected because approval/dual control must remain distinct from performing assigned work.

## Decision 2: Function catalog declares supported actions

**Decision**: Add supported-action metadata to each function and validate requested masks against it. Require `VIEW` whenever another action is present.

**Rationale**: The current permission editor displays stored masks but the function entity does not declare which actions are meaningful. Catalog metadata prevents nonsensical grants such as deleting reports or executing a profile page.

**Alternatives considered**:

- Hard-code supported actions only in Angular: rejected because direct requests could store invalid masks.
- Allow all bits for all functions: rejected because it creates misleading and untestable grants.

## Decision 3: Permission revocation is request-fresh

**Decision**: Treat JWT/browser masks as display hints only. Preserve the current behavior where the authentication filter reloads user details and permissions from the database for each HTTP request. Refresh frontend context after administrative changes/403 responses, and revalidate or close long-lived socket sessions when authorization changes.

**Rationale**: Current `PermissionInterceptor` reads request-loaded `CustomUserDetails.permissionMasks`, while Angular reads local storage. The backend path can already satisfy next-request revocation; the remaining gap is stale UI and long-lived connections.

**Alternatives considered**:

- Force logout after every permission change: safe but disruptive and hard to guarantee across devices.
- Very short JWT lifetime: reduces but does not eliminate the stale-rights window.

## Decision 4: Authorization is permission plus resource scope plus entitlement

**Decision**: Evaluate function/action permission, authenticated property access or customer ownership, and subscription feature gate independently; all required checks must pass.

**Rationale**: The constitution requires `hotel_id` isolation and forbids trusting client-provided tenant identity. A global function bit alone cannot prevent IDOR.

**Alternatives considered**:

- Encode each hotel into role names: rejected because users can manage multiple properties and assignments change independently.
- Rely only on repository query naming: rejected because the constitution mandates centralized property resolution and Hibernate filters.

## Decision 5: Keep generic task queues as projections over domain transitions

**Decision**: Use a generic `OperationalTask` only for assignment, visibility, reassignment and audit; domain services remain authoritative for check-in, checkout, refunds, payment confirmation and housekeeping completion.

**Rationale**: Existing aggregates already contain state rules and concurrency controls. A task record must not become a second source of truth.

**Alternatives considered**:

- One universal workflow engine: excessive for current scope and risky to existing flows.
- No task abstraction: insufficient for cross-role queues and revoked-permission reassignment requirements.

## Decision 6: Reuse the shared VNPay adapter with context-owned orchestration

**Decision**: Reuse `VnpayPaymentProviderAdapter` for signature and normalized callback evidence, while Property Commerce and Platform Billing independently resolve merchant configuration, expected order/amount and business effect.

**Rationale**: Existing provider primitives validate HMAC-SHA512, merchant, amount, currency and reference. Existing payment packages already separate property and platform attempts.

**Alternatives considered**:

- Duplicate VNPay code in both contexts: rejected because cryptographic/provider behavior would drift.
- One shared payment aggregate: rejected because it could mix property money with platform money.

## Decision 7: Browser return is display-only; callbacks are authoritative

**Decision**: Return pages poll/read stored status. Only verified callback/IPN processing may mark success and trigger booking/subscription effects.

**Rationale**: Query parameters visible to the browser are replayable and user-controlled. Existing adapter/callback tests already support server-authoritative verification.

**Alternatives considered**:

- Mark success from return URL: rejected for integrity and replay risk.
- Require the customer JWT on provider callback: rejected because providers do not operate in a customer session.

## Decision 8: Exactly-once effects use multiple safeguards

**Decision**: Combine unique idempotency keys, unique provider event/reference constraints, row version/locking, immutable evidence and a unique business-effect identity.

**Rationale**: VNPay and browser retries can deliver duplicate or concurrent events. A single application-level check is race-prone.

**Alternatives considered**:

- Check status before update without locking: rejected because concurrent callbacks can both observe pending.
- Ignore all duplicate callbacks: rejected because identical replay should return the stored result while conflicting evidence must be audited.

## Decision 9: Additive migrations and compatibility

**Decision**: Add supported-action/version/task/reconciliation fields or tables through forward-only Flyway migrations. Preserve existing action values and backfill defaults deterministically.

**Rationale**: Existing masks, financial attempts and audit data must remain valid. The worktree already has implemented Feature 007 financial contexts that should be extended, not replaced.

**Alternatives considered**:

- Rewrite existing permission/payment tables: rejected due regression and data-loss risk.

## Decision 10: Converge legacy and canonical VNPay entry points

**Decision**: Move booking VNPay checkout to `PropertyPaymentAttempt`, add provider-specific VNPay GET IPN/return ingress that adapts query parameters into the shared verification pipeline, and keep legacy payment-session endpoints behind a temporary compatibility adapter. Add equivalent signed checkout URL/return/IPN support to Platform Billing.

**Rationale**: Booking currently branches VNPay through legacy payment sessions, while canonical context callbacks accept a generic POST contract. Platform Billing has attempts/callbacks but no redirect URL and the UI disables VNPay. VNPay requires a provider-facing query ingress without making that ingress the domain source of truth.

**Alternatives considered**:

- Keep two active booking ledgers: rejected because state and reconciliation can diverge.
- Put provider-specific query parsing directly into domain services: rejected because it couples business orchestration to transport details.

## Decision 11: Add stable retry and provider recovery behavior

**Decision**: Persist/reuse an idempotency key per order/provider/method across page retry, bind the VNPay transaction reference before redirect, and add a read-only VNPay transaction-query recovery path for lost or late IPN.

**Rationale**: Platform UI currently creates a new key per click and VNPay recovery is not covered by the existing provider-recovery scope. Recovery must reconcile existing attempts, never create a new debit.

**Alternatives considered**:

- Create a new attempt on every retry: rejected because users can produce duplicate pending payments.
- Auto-apply any late success: rejected when order/booking state, merchant, amount or reference no longer matches.

## Evidence Reviewed

- `backend/src/main/java/com/hotel/security/ActionCode.java`
- `backend/src/main/java/com/hotel/security/PermissionInterceptor.java`
- `backend/src/main/java/com/hotel/services/RolePermissionService.java`
- `backend/src/main/java/com/hotel/services/PropertyAccessService.java`
- `backend/src/main/java/com/hotel/entities/AppFunction.java`
- `backend/src/main/java/com/hotel/entities/RolePermission.java`
- `backend/src/main/java/com/hotel/entities/RolePermissionAudit.java`
- `backend/src/main/java/com/hotel/entities/HousekeepingTask.java`
- `backend/src/main/java/com/hotel/propertycommerce/payment/PropertyPaymentAttempt.java`
- `backend/src/main/java/com/hotel/platformbilling/order/SubscriptionOrder.java`
- `backend/src/main/java/com/hotel/platformbilling/payment/PlatformPaymentAttempt.java`
- `backend/src/main/java/com/hotel/paymentprovider/adapters/VnpayPaymentProviderAdapter.java`
- `backend/src/main/java/com/hotel/controllers/PaymentController.java`
- `backend/src/main/java/com/hotel/services/payment/VnpayPaymentGateway.java`
- `backend/src/main/java/com/hotel/platformbilling/payment/PlatformPaymentAttemptService.java`
- `frontend/src/app/features/client/booking-checkout/booking-checkout.component.ts`
- `frontend/src/app/features/management/subscription-billing/platform-payment-panel.component.ts`
- `frontend/src/app/core/services/permission.service.ts`
- `frontend/src/app/core/guards/permission.guard.ts`
- `frontend/src/app/features/system/role-permission/role-permission.ts`

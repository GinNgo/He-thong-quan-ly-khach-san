# Data Model: Role Permissions and VNPay

## Action Bits

| Action | Bit | Rule |
|---|---:|---|
| `VIEW` | 1 | Required for every other granted action |
| `CREATE` | 2 | Creates a new resource; does not execute workflow transitions |
| `UPDATE` | 4 | Edits mutable resource data |
| `DELETE` | 8 | Soft-delete/deactivate when history or references exist |
| `EXPORT` | 16 | Exports authorized rows using the same filters/scope as the view |
| `APPROVE` | 32 | Approves exceptional or dual-control decisions |
| `TASK_EXECUTE` | 64 | Executes operational state transitions or completes assigned work |

Valid masks are non-negative, contain only catalog-supported bits, and contain `VIEW` whenever any other bit is set.

## Authorization Entities

### FunctionCatalogItem (`app_function` extension)

| Field | Rule |
|---|---|
| `id`, `module_id`, `code`, `name` | Existing stable identity |
| `url`, `icon`, `sort_order` | Existing navigation metadata |
| `supported_action_mask` | Required; only declared actions can be granted |
| `scope_type` | `PUBLIC`, `SELF`, `PROPERTY`, `PLATFORM` |
| `active` | Inactive functions cannot be newly granted or shown in menus |
| `version` | Optimistic concurrency for catalog changes |

### Role

Existing role identity is retained. Relevant rules:

- `code` is unique and stable.
- `scope_type` distinguishes platform roles from property roles.
- Core-role mutability is controlled by policy; custom roles can copy defaults.
- Deactivation does not delete historical assignments or audit evidence.

### RolePermission (`app_role_permission`)

| Field | Rule |
|---|---|
| `role_id`, `function_id` | Unique pair |
| `action_mask` | Must satisfy supported actions and `VIEW` dependency |
| `version` | Rejects concurrent lost updates |

### EffectivePermissionContext (derived, not persisted)

| Field | Rule |
|---|---|
| `user_id` | Authenticated user identity |
| `permission_masks` | Current union loaded from active roles for the request |
| `property_assignments` | Current active property scope |
| `feature_limits` | Current subscription entitlements |
| `loaded_at` | Diagnostic timestamp only; not an authorization cache contract |

The effective mask for a function is the bitwise union of active assigned roles, after scope eligibility. Resource scope is still checked separately.

### PropertyAssignment

Existing user-property association remains authoritative.

| Field | Rule |
|---|---|
| `user_id`, `hotel_id`, `role_id` | Unique active assignment |
| `active_from`, `active_until`, `status` | Controls current access |
| `version` | Concurrent assignment protection |

Client-supplied `hotel_id` may select among already-authorized properties but cannot create authorization.

### PermissionAuditEvent

Use append-only `RolePermissionAudit` plus operational audit events.

| Field | Rule |
|---|---|
| `actor_user_id`, `role_id` | Required mutation evidence |
| `expected_version`, `resulting_version` | Concurrency evidence |
| `previous_state_json`, `new_state_json` | Complete matrix snapshots |
| `reason`, `correlation_id`, `occurred_at` | Traceability; reason required for sensitive changes |

## Operational Task Entities

### OperationalTask

| Field | Rule |
|---|---|
| `id`, `public_id` | Stable identity |
| `hotel_id` | Required for property work and tenant filter |
| `task_type` | `CHECKIN`, `CHECKOUT`, `PAYMENT_CONFIRMATION`, `RECONCILIATION`, `REFUND`, `HOUSEKEEPING`, `APPROVAL`, `OTHER` |
| `function_code`, `required_action` | Usually domain function plus `TASK_EXECUTE`; approval tasks may also require `APPROVE` |
| `aggregate_type`, `aggregate_id` | Domain source of truth |
| `status` | `OPEN`, `ASSIGNED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `BLOCKED` |
| `assigned_to_user_id`, `assigned_by`, timestamps | Assignment history |
| `effect_key` | Unique per domain transition to prevent duplicate completion |
| `version` | Optimistic completion/reassignment protection |

### OperationalTaskHistory

Append-only transition record containing task, previous/new status, actor, reason, permission version, domain result reference, correlation ID and timestamp.

### Task State Transitions

```text
OPEN -> ASSIGNED -> IN_PROGRESS -> COMPLETED
OPEN | ASSIGNED | IN_PROGRESS -> CANCELLED | BLOCKED
ASSIGNED | IN_PROGRESS -> ASSIGNED          (authorized reassignment)
BLOCKED -> ASSIGNED                         (after cause is resolved)
```

Permission revocation does not change task state automatically. The next execute request fails; an authorized actor may reassign or block it. `COMPLETED` and `CANCELLED` are terminal.

## Booking Payment Entities

### BookingPaymentOrder

Represented by the reservation financial aggregate plus `PropertyPaymentAttempt`.

| Field | Rule |
|---|---|
| `hotel_id`, `reservation_id`, `owner_user_id` | Required ownership |
| `purpose` | `DEPOSIT`, `BALANCE`, `SERVICE`, `SURCHARGE`, `OTHER` |
| `expected_amount`, `currency` | Server-owned, positive scale-zero VND |
| `provider`, `environment`, `configuration_id` | Property-owned payment identity |
| `idempotency_key`, `request_hash` | Same key with different payload is rejected |
| `provider_order_ref`, `provider_transaction_ref`, `provider_event_id` | Bind-once evidence |
| `status`, `expires_at`, `version` | Controlled lifecycle and concurrency |

Property attempts and all downstream financial effects carry `hotel_id` and tenant filtering.

## Subscription Payment Entities

### SubscriptionOrder

| Field | Rule |
|---|---|
| `owner_user_id`, `target_hotel_id` | Authorized purchaser and entitlement target |
| `operation` | `PURCHASE`, `RENEW`, `UPGRADE`; unsupported transitions fail before payment |
| `plan_id`, `plan_version`, `plan_code`, `plan_name` | Immutable catalog snapshot identity |
| `price`, `currency`, `duration`, `feature_snapshot` | Server-owned immutable terms |
| `status` | `CREATED`, `PENDING_PAYMENT`, `PAID`, `APPLIED`, `FAILED`, `CANCELLED`, `EXPIRED`, `REFUNDED` |
| `idempotency_key`, `request_hash`, `expires_at`, `version` | Retry/concurrency controls |

### PlatformPaymentAttempt

Owns provider processing for a subscription order and references only platform configuration. Unique constraints cover public ID, order/idempotency key and provider event/effect identities.

## Shared VNPay and Reconciliation Entities

### VNPayTransaction Evidence

No context-neutral mutable payment row is introduced. Each context attempt stores normalized evidence:

- terminal code/merchant identity (masked outside secure processing);
- transaction reference and provider transaction number;
- provider event identity;
- expected and received VND amount;
- response and transaction status;
- payment time and verification time;
- environment and signature verification result.

### ReconciliationCase

| Field | Rule |
|---|---|
| `id`, `context` | `PROPERTY_COMMERCE` or `PLATFORM_BILLING` |
| `hotel_id` | Required only for property context |
| `attempt_public_id`, `provider_event_id` | Evidence linkage |
| `reason_code` | Signature, merchant, amount, reference, state, duplicate-conflict, expired aggregate or unknown event |
| `expected_snapshot_json`, `received_snapshot_json` | Redacted comparison evidence |
| `status` | `OPEN`, `INVESTIGATING`, `RESOLVED`, `DISMISSED` |
| `assigned_to`, `resolution`, timestamps, `version` | Accounting workflow |

## Payment State Transitions

### Property Payment Attempt

```text
CREATED -> PENDING | PROCESSING | CANCELLED | EXPIRED
PENDING -> PROCESSING | SUCCESS | FAILED | CANCELLED | EXPIRED
PROCESSING -> SUCCESS | FAILED | CANCELLED | EXPIRED
SUCCESS -> PARTIALLY_REFUNDED -> REFUNDED
```

### Platform Payment Attempt

```text
CREATED -> PENDING -> PROCESSING -> SUCCESS
CREATED | PENDING | PROCESSING -> FAILED
CREATED | PENDING -> CANCELLED | EXPIRED
SUCCESS -> PARTIALLY_REFUNDED -> REFUNDED
```

Identical terminal replay returns stored outcome. Conflicting terminal evidence is rejected and creates a reconciliation case.

## Required Constraints and Indexes

- Unique role/function permission pair.
- Check constraint: action mask contains only supported global bits; service validation enforces function-specific supported bits and `VIEW` dependency.
- Unique active user/property/role assignment.
- Unique task effect key and tenant-prefixed indexes for task queues.
- Existing property/platform idempotency and public-ID constraints retained.
- Unique provider event/reference scoped by provider, environment and merchant/configuration.
- Unique subscription application effect per successful order/payment.
- Composite property indexes begin with `hotel_id` for property task/payment/reconciliation queries.

## Migration Rules

1. Add the `TASK_EXECUTE` bit without changing existing bit values.
2. Add function supported-action metadata and backfill from a reviewed mapping; stop if an active permission contains an unsupported legacy bit.
3. Preserve request-time permission reload and add audit/invalidation handling for UI/socket contexts without making browser state authoritative.
4. Add task/reconciliation tables only when an existing domain entity cannot satisfy the queue requirement.
5. Add provider uniqueness constraints after duplicate preflight queries.
6. Use additive forward-only Flyway migrations; do not delete legacy audit or financial evidence.

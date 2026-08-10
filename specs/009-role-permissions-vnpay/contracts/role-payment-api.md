# API Contract: Role Permissions, Tasks and VNPay

All protected responses use the existing correlation-aware error envelope. Authorization failures return `403` with stable codes and create no mutation. Tenant-scoped IDs are resolved against authenticated property access.

## Function Catalog and Permissions

### `GET /api/functions`

Returns active function catalog items including `code`, module/navigation metadata, `supportedActionMask`, `scopeType` and version. Requires platform function-catalog view permission.

### `GET /api/role-permissions/tree/{roleId}`

Returns modules/functions with the role's `actionMask`, function `supportedActionMask` and role `expectedVersion`.

### `PUT /api/role-permissions/{roleId}`

Request:

```json
{
  "expectedVersion": 12,
  "reason": "Điều chỉnh nhiệm vụ lễ tân ca đêm",
  "permissions": [
    { "functionId": 101, "actionMask": 71 }
  ]
}
```

`71` represents `VIEW | CREATE | UPDATE | TASK_EXECUTE`. The service rejects duplicate function IDs, unsupported bits, bits outside function support, missing `VIEW` dependencies and stale versions.

Response: updated role version and normalized permission matrix.

Errors:

- `PERMISSION_MASK_INVALID`
- `PERMISSION_VIEW_REQUIRED`
- `FUNCTION_ACTION_UNSUPPORTED`
- `ROLE_VERSION_CONFLICT`
- `CORE_ROLE_IMMUTABLE`
- `FORBIDDEN_PERMISSION`

### `GET /api/auth/effective-permissions`

Returns current permission version, active property assignments and normalized function masks for UI presentation. This endpoint does not replace backend enforcement.

## Operational Tasks

### `GET /api/management/tasks`

Filters: `hotelId` selection within authorized assignments, status, type, assignee and date. Results include required function/action, aggregate reference and version.

### `POST /api/management/tasks/{taskId}/claim`

Requires task function `VIEW | TASK_EXECUTE`, property access and matching expected version.

### `POST /api/management/tasks/{taskId}/execute`

Headers: `Idempotency-Key` required.

Request:

```json
{
  "expectedVersion": 4,
  "command": "COMPLETE",
  "reason": "Đã hoàn tất nghiệp vụ",
  "payload": {}
}
```

The task service rechecks current permission/version, delegates to the domain service, then records the domain result and history exactly once.

### `POST /api/management/tasks/{taskId}/reassign`

Requires the function's `APPROVE` or an explicit task-administration permission. Revoked execute permission never automatically reassigns a task.

Errors:

- `TASK_PERMISSION_REVOKED`
- `TASK_VERSION_CONFLICT`
- `TASK_ALREADY_COMPLETED`
- `TASK_EFFECT_ALREADY_APPLIED`
- `PROPERTY_ACCESS_DENIED`
- domain-specific transition errors

## Booking VNPay

### `POST /api/reservations/{reservationId}/payment-attempts`

Headers: `Idempotency-Key` required.

Request identifies `method: "VNPAY"` and purpose only. Amount, currency, property configuration and payable balance are server-derived.

Response includes attempt public ID, pending status, expiry, environment label and redirect URL. Sensitive credentials are never returned.

### `POST /api/payment-providers/property/VNPAY/callback`

Public provider endpoint. It does not require customer JWT. The handler validates signed VNPay parameters, property merchant/configuration, reference, VND amount, current state and replay identity before applying a booking financial effect.

The provider-facing VNPay ingress may be exposed as a GET IPN endpoint accepting official `vnp_*` query parameters; it must adapt to this same callback service and return the provider acknowledgement contract. The browser return endpoint remains display-only.

### `GET /api/payment-attempts/{attemptId}`

Returns owner/authorized-staff-visible status. Browser return pages poll this endpoint and never submit a success mutation.

## Subscription VNPay

### `POST /api/platform/subscription-orders`

Requires package function `VIEW | TASK_EXECUTE` and property access. Request selects plan and operation; the response contains the immutable server-owned snapshot and order expiry.

### `POST /api/platform/subscription-orders/{orderId}/payment-attempts`

Headers: `Idempotency-Key` required. Request selects `VNPAY`; platform merchant/configuration and order amount are server-owned.

### `POST /api/payment-providers/platform/VNPAY/callback`

Validates the platform merchant and subscription order. Success applies exactly one payment effect and exactly one entitlement/contract history transition.

As with property payments, an official VNPay GET IPN ingress delegates to this service. Payment-attempt creation returns a signed redirect URL and reuses a stable order/provider/method idempotency identity across equivalent retries.

### `GET /api/platform/subscription-orders/{orderId}`

Returns order, payment and application status for the authorized owner/property or platform finance role.

## Reconciliation

### `GET /api/management/reconciliation/property-payments`

Requires property finance `VIEW`; export requires `EXPORT`; resolving a case requires `TASK_EXECUTE` or `APPROVE` according to resolution type.

### `GET /api/admin/reconciliation/platform-payments`

Platform-only reconciliation. Property staff cannot access platform merchant or subscription-revenue records without explicit platform scope.

### `POST /api/reconciliation/{caseId}/resolve`

Requires expected version, resolution code, explanation and appropriate permission. It may record a decision but cannot fabricate provider success or rewrite immutable financial evidence.

## Idempotency and Concurrency Contract

- Reusing an idempotency key with the same payload returns the stored result.
- Reusing it with a different payload returns `IDEMPOTENCY_CONFLICT`.
- Identical callback replay acknowledges the stored terminal result.
- Conflicting callback evidence returns the provider-safe rejection acknowledgement and opens a reconciliation case.
- Concurrent role updates, task completion and payment callbacks permit one winning transition; losing requests return a stable conflict or stored idempotent result.

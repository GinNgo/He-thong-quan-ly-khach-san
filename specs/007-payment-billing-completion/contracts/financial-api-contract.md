# Financial API Contract

## Contract Rules

- Base media type is JSON; generated/export responses use explicit file media types.
- Money is an integer-valued JSON number in VND; the backend rejects fractional values.
- Property ownership is resolved from authentication and authorized property access. A route may include a property identifier for navigation, but it is never trusted without access validation.
- Mutating requests accept `Idempotency-Key`; replay with the same normalized payload returns the original result, while key reuse with different content returns `409 IDEMPOTENCY_KEY_REUSED`.
- Provider callbacks do not require customer JWT. They require provider verification and are rate/replay controlled.
- Error bodies use `{ code, message, correlationId, fieldErrors?, retryable, currentState? }` and never contain secrets or stack traces.
- Production-disabled or incomplete configuration returns a truthful availability error; it never falls back to a simulator.

## Property Commerce API

### Payment configuration

| Method | Path | Permission | Contract |
|---|---|---|---|
| GET | `/api/management/properties/{propertyId}/payment-configuration` | `PROPERTY_PAYMENT_CONFIG_VIEW` | Return enabled methods, environment, deposit policy, expiry, bilingual instructions and masked readiness data |
| PUT | `/api/management/properties/{propertyId}/payment-configuration` | `PROPERTY_PAYMENT_CONFIG_MANAGE` | Validate ownership, provider readiness, bank fields and production gate; never echo secrets |
| POST | `/api/management/properties/{propertyId}/payment-configuration/validate` | `PROPERTY_PAYMENT_CONFIG_MANAGE` | Validate configuration without sending money; return per-method readiness |

### Booking financial summary and attempts

| Method | Path | Permission | Contract |
|---|---|---|---|
| GET | `/api/reservations/{reservationId}/financial-summary` | Reservation owner or authorized property role | Return server-derived charges, deposit, successful payments/refunds, remaining amount and financial state |
| POST | `/api/reservations/{reservationId}/payment-attempts` | Reservation owner or authorized property role | Create/reuse an attempt from server-owned payable amount/purpose; request selects method but cannot set settled amount |
| GET | `/api/payment-attempts/{attemptId}` | Authorized resource owner | Return safe status, expiry, instructions/redirect and references |
| POST | `/api/payment-attempts/{attemptId}/cancel` | Authorized resource owner | Cancel only an allowed non-terminal attempt |
| POST | `/api/management/payment-attempts/{attemptId}/confirm-manual` | `PROPERTY_PAYMENT_CONFIRM_MANUAL` | Confirm authentic transfer with reason/evidence; idempotent; never accepts property scope from body |

Payment-attempt response includes `attemptId`, `status`, `environment`, `expectedAmount`, `currency`, `expiresAt`, `method`, masked receiver data, unique transfer content, QR/redirect data if applicable and bilingual instructions.

### Provider callback

| Method | Path | Authentication | Contract |
|---|---|---|---|
| POST | `/api/payment-providers/property/{provider}/callback` | Provider signature/merchant verification | Verify provider identity, expected amount/currency/reference and replay identity; apply exactly one property-payment effect |

Callback responses acknowledge equivalent replays without repeating domain changes. Invalid signature/merchant/amount/reference produces a stable denial and audit event.

### Charges and checkout

| Method | Path | Permission | Contract |
|---|---|---|---|
| POST | `/api/management/reservations/{reservationId}/charges/services` | `RESERVATION_SERVICE_ADD` | Add a server-priced service snapshot with positive quantity and usage time |
| POST | `/api/management/reservations/{reservationId}/charges/surcharges` | `RESERVATION_SURCHARGE_ADD` | Add a typed, reasoned surcharge; negative adjustment requires separate permission |
| POST | `/api/management/reservations/{reservationId}/checkout-preview` | `RESERVATION_CHECKOUT` | Recompute full authoritative folio without mutation |
| POST | `/api/management/reservations/{reservationId}/checkout` | `RESERVATION_CHECKOUT`; debt override additionally requires `RESERVATION_DEBT_OVERRIDE` | Lock and atomically settle/finalize/update room and housekeeping state; request may reference payment methods/attempts but not authoritative totals |

Checkout returns the finalized invoice ID/number, financial summary and resulting operational states. Underpayment returns `409 OUTSTANDING_BALANCE`; overpayment returns `409 OVERPAYMENT_REQUIRES_RESOLUTION` unless an approved rule applies.

### Invoices and property refunds

| Method | Path | Permission | Contract |
|---|---|---|---|
| GET | `/api/invoices/{invoiceId}` | Customer owner or authorized property role | Return immutable invoice snapshot and payment allocations |
| GET | `/api/invoices/{invoiceId}/pdf` | Same as invoice view | Render finalized snapshot only |
| POST | `/api/invoices/{invoiceId}/email` | Same as invoice view plus verified recipient policy | Queue/send the finalized invoice and record notification evidence |
| POST | `/api/management/invoices/{invoiceId}/credit-notes` | `INVOICE_ADJUST` | Append an authorized post-finalization correction |
| POST | `/api/property-payments/{transactionId}/refunds` | `PROPERTY_REFUND_REQUEST` | Request full/partial refund against remaining refundable balance |
| POST | `/api/property-refunds/{refundId}/approve` | `PROPERTY_REFUND_APPROVE` | Separate approval where policy requires |
| GET | `/api/property-refunds/{refundId}` | Authorized resource owner | Return refund/request attempt status and remaining refundable amount |

## Platform Billing API

### Catalog and orders

| Method | Path | Permission | Contract |
|---|---|---|---|
| GET | `/api/platform/subscription-plans` | Authenticated owner/authorized representative | Return active public plan catalog without merchant configuration |
| POST | `/api/platform/subscription-orders` | `SUBSCRIPTION_PURCHASE` | Create/reuse an order using backend plan snapshot; request identifies plan and target property, not price/duration/features |
| GET | `/api/platform/subscription-orders/{orderId}` | Order owner or system billing role | Return safe order, payment and application status |
| POST | `/api/platform/subscription-orders/{orderId}/payment-attempts` | Order owner | Create/reuse a platform payment attempt using system merchant configuration |
| POST | `/api/platform/subscription-orders/{orderId}/cancel` | Order owner or system billing role | Cancel only allowed unpaid orders |

### Platform callbacks and lifecycle

| Method | Path | Authentication/permission | Contract |
|---|---|---|---|
| POST | `/api/payment-providers/platform/{provider}/callback` | Provider signature/system merchant verification | Apply one platform financial transaction and one eligible subscription effect |
| POST | `/api/platform/subscriptions/{subscriptionId}/renewal-orders` | `SUBSCRIPTION_RENEW` | Create a backend-snapshotted renewal order |
| POST | `/api/platform/subscriptions/{subscriptionId}/upgrade-orders` | `SUBSCRIPTION_UPGRADE` | Validate target plan and approved upgrade policy before order creation |
| POST | `/api/platform/subscriptions/{subscriptionId}/downgrade-orders` | `SUBSCRIPTION_DOWNGRADE` | Apply approved policy or return `409 POLICY_NOT_CONFIGURED` without mutation |
| POST | `/api/platform-payments/{transactionId}/refunds` | `PLATFORM_REFUND_REQUEST` | Request platform refund; entitlement effects require approved policy |
| GET | `/api/platform/subscriptions/{subscriptionId}/history` | Subscription owner or system billing role | Return contract and entitlement transition evidence |

## Reporting and Exports

| Method | Path | Scope | Contract |
|---|---|---|---|
| GET | `/api/management/reports/property-revenue` | Authorized current property | Filters by date/basis/method/room type; returns gross, refunds, net, cash, invoiced, unpaid and detail/reconciliation queues |
| GET | `/api/management/reports/property-revenue/export` | Same property/report permission | Excel/PDF/CSV output generated from the same report result model |
| GET | `/api/admin/reports/platform-revenue` | `PLATFORM_REVENUE_VIEW` | Returns purchase/renewal/upgrade/refund/credit/net, plan mix, status and provider metrics |
| GET | `/api/admin/reports/platform-revenue/export` | `PLATFORM_REVENUE_EXPORT` | File output with identical normalized filters, rows and totals |
| POST | `/api/admin/reconciliation/property` | Audit/admin permission | Run authoritative property reconciliation and return mismatches without mutating source financial evidence |
| POST | `/api/admin/reconciliation/platform` | Audit/admin permission | Run platform reconciliation and return mismatches |

All collected totals exclude `CREATED`, `PENDING`, `PENDING_VERIFICATION`, `PROCESSING`, `FAILED`, `CANCELLED` and `EXPIRED` attempts. Invoice allocation prevents a deposit from being counted again at checkout.

## Configuration Safety API

| Method | Path | Permission | Contract |
|---|---|---|---|
| GET | `/api/admin/payment-readiness` | `PAYMENT_READINESS_VIEW` | Return context/provider environment, masked configuration completeness and blockers |
| POST | `/api/admin/payment-readiness/validate` | `PAYMENT_READINESS_VALIDATE` | Execute no-money health/contract checks |
| POST | `/api/admin/payment-readiness/production-approval` | Separate out-of-band approval only | Not implemented/enabled until the project owner explicitly approves production readiness work |

## Required Error Codes

`TENANT_ACCESS_DENIED`, `RESOURCE_NOT_FOUND`, `INVALID_AMOUNT`, `INVALID_CURRENCY`, `INVALID_STATE_TRANSITION`, `OUTSTANDING_BALANCE`, `OVERPAYMENT_REQUIRES_RESOLUTION`, `IDEMPOTENCY_KEY_REUSED`, `CALLBACK_SIGNATURE_INVALID`, `CALLBACK_MERCHANT_MISMATCH`, `CALLBACK_AMOUNT_MISMATCH`, `CALLBACK_REFERENCE_MISMATCH`, `ATTEMPT_EXPIRED`, `REFUND_EXCEEDS_BALANCE`, `POLICY_NOT_CONFIGURED`, `PAYMENT_ENVIRONMENT_DISABLED`, `PRODUCTION_NOT_APPROVED`, `PROVIDER_UNAVAILABLE`, `CONCURRENT_MODIFICATION`, `EXPORT_RECONCILIATION_MISMATCH`.

Every error contract specifies whether retry is safe and guarantees no unauthorized/invalid financial mutation.

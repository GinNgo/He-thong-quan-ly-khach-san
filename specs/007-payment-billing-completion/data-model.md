# Data Model: Payment, Billing, and Full-System Completion

## Modeling Conventions

- Monetary columns use `decimal(19,0)` and currency is constrained to `VND`.
- Property Commerce tables include non-null `hotel_id`, an index beginning with `hotel_id`, and tenant filtering/ownership enforcement.
- Platform Billing tables use explicit system scope and do not contain property merchant/account references.
- Ledger, invoice, credit-note, history and audit rows are append-only after finalization.
- Mutable aggregate rows use optimistic version columns; callback/refund/checkout paths also acquire row locks where concurrency matters.
- Every external or user retry boundary has a persisted idempotency identity and payload hash.
- Timestamps are stored consistently in UTC; UI formats them in the selected locale/time zone.

## Shared Value Types

### Money

| Field | Type | Rule |
|---|---|---|
| amount | decimal(19,0) | Non-negative unless the containing type explicitly represents credit/adjustment direction |
| currency | varchar(3) | Must be `VND` |

### PaymentEnvironment

`SIMULATOR`, `SANDBOX`, `PRODUCTION`

### ProviderMethod

`MANUAL_TRANSFER`, `QR_TRANSFER`, `VNPAY`, `MOMO`, `ZALOPAY`, `CASH`, `CARD_TERMINAL`, `OTHER`

## Property Commerce

### PropertyPaymentConfiguration

Tenant-owned configuration selected by `hotel_id`.

| Field | Rule |
|---|---|
| id | Primary key |
| hotel_id | Required, unique active configuration per property |
| enabled | Overall property-payment enablement |
| environment | Explicit simulator/sandbox/production mode |
| allowed_methods | Normalized child rows or validated JSON; cannot enable an unconfigured provider |
| bank_name, bank_code | Public receiving-bank identity |
| account_name | Public receiving-account name |
| account_number_encrypted | Encrypted at rest; only masked form returned except in authorized payment instructions |
| deposit_policy_type | `NONE`, `FIXED`, `PERCENTAGE` |
| deposit_value | Scale-zero fixed VND or bounded percentage |
| payment_expiry_minutes | Positive bounded value |
| transfer_template | Contains a unique booking/payment placeholder |
| qr_provider | Optional provider identity |
| instructions_vi, instructions_en | Bilingual manual guidance |
| production_approved_at/by | Null until separate readiness approval |
| version, created_at, updated_at | Concurrency/audit metadata |

### PropertyPaymentAttempt

Mutable provider/manual processing record.

| Field | Rule |
|---|---|
| id | Primary key |
| hotel_id | Required tenant owner |
| reservation_id | Required payable aggregate |
| configuration_id | Snapshot source reference |
| purpose | `DEPOSIT`, `BALANCE`, `SERVICE`, `SURCHARGE`, `OTHER` |
| method, provider, environment | Required execution identity |
| expected_amount, currency | Server-owned and immutable after creation |
| unique_transfer_content | Unique while active for manual/QR transfer |
| status | Controlled payment lifecycle |
| idempotency_key, request_hash | Unique per actor/context; mismatch is rejected |
| provider_order_ref, provider_transaction_ref | Uniqueness scoped to provider/environment |
| expires_at | Required for remote/manual attempts |
| verified_at, verified_by | Populated only after authoritative confirmation |
| version, created_at, updated_at | Concurrency/audit metadata |

### PropertyFinancialTransaction

Immutable ledger event.

| Field | Rule |
|---|---|
| id | Primary key |
| hotel_id | Required tenant owner |
| reservation_id, invoice_id | Owning business references |
| attempt_id | Source attempt where applicable |
| original_transaction_id | Required for refunds/credits |
| type | `BOOKING_DEPOSIT`, `ROOM_PAYMENT`, `SERVICE_PAYMENT`, `SURCHARGE`, `MANUAL_ADJUSTMENT`, `REFUND` |
| direction | `DEBIT` or `CREDIT` from property ledger perspective |
| amount, currency | Exact immutable value |
| method, provider, environment | Settlement evidence |
| provider_transaction_ref | Immutable external reference |
| occurred_at, recorded_at | Economic and system timestamps |
| actor_type, actor_id, reason | Source evidence |
| idempotency_identity | Unique economic effect identity |

### ReservationChargeLine

Immutable or append-only folio line before invoice finalization; finalized lines are copied to `InvoiceLine`.

| Field | Rule |
|---|---|
| id, hotel_id, reservation_id | Ownership |
| type | `ROOM`, `SERVICE`, `MINIBAR`, `SURCHARGE`, `TAX`, `FEE`, `DISCOUNT`, `ADJUSTMENT` |
| source_id, source_version | Optional catalog origin |
| code, name, description | Snapshotted identity |
| unit_price, quantity, tax_amount, discount_amount, total_amount | Server-calculated exact values |
| service_used_at | Required for service/minibar usage |
| actor_id, created_at | Evidence |
| reverses_line_id | Correction link; prior row is not edited |

### BookingFinancialSummary

Server-derived projection, not a client-editable ledger.

| Field | Rule |
|---|---|
| reservation_id, hotel_id | Unique aggregate identity |
| gross_charges, deposit_required | Derived from policy/snapshots |
| successful_payments, successful_refunds | Derived from ledger |
| remaining_balance | Charges minus net payments |
| financial_state | `UNPAID`, `PARTIALLY_PAID`, `DEPOSIT_PAID`, `PAID`, `OVERPAID`, `PARTIALLY_REFUNDED`, `REFUNDED` |
| calculated_at, source_version | Projection consistency metadata |

### Invoice and InvoiceLine

Finalized immutable billing snapshot.

| Entity | Important fields and rules |
|---|---|
| Invoice | `id`, `hotel_id`, `reservation_id`, unique invoice number, customer/property identity snapshots, subtotal/tax/fee/discount/total/paid/refunded/balance, status `DRAFT` or `FINALIZED`, finalized timestamps |
| InvoiceLine | Invoice owner, line type, code/name/description, quantity, unit price, tax/discount/total, service/room usage dates, source references for evidence only |
| InvoicePaymentAllocation | Invoice-to-ledger allocation amount; prevents deposit/payment double counting |

Finalized invoice/header/lines/allocations cannot be updated or deleted by application services.

### CreditNote and CreditNoteLine

Post-finalization correction linked to an invoice. Contains reason, approver, immutable line snapshots and amount. It does not rewrite the original invoice.

### PropertyRefundRequest and PropertyRefundAttempt

| Entity | Important fields and rules |
|---|---|
| PropertyRefundRequest | `hotel_id`, original successful transaction, requested/approved/succeeded amount, reason, actor, approver, status, idempotency key, remaining refundable snapshot |
| PropertyRefundAttempt | Request, provider/environment, provider reference, status, normalized error, retry count, timestamps |

The sum of successful refund ledger events for an original transaction cannot exceed its amount. Concurrency is protected by locking and constraints.

### CheckoutOverride

Records an authorized debt checkout or other exceptional financial decision. Requires `hotel_id`, reservation, permission code, reason, actor, approver if policy requires, amount outstanding and timestamp.

## Platform Billing

### PlatformPaymentConfiguration

System-owned merchant/provider configuration, isolated from properties. Sensitive values are encrypted or environment references; responses expose only readiness/masked metadata. Production activation requires an approval record.

### SubscriptionOrder

| Field | Rule |
|---|---|
| id | Primary key |
| owner_user_id, target_hotel_id | Authorized purchaser and entitlement target |
| operation | `PURCHASE`, `RENEW`, `UPGRADE`, `DOWNGRADE`, `REFUND` |
| plan_id, plan_version, plan_code, plan_name | Backend snapshot identity |
| price, currency, billing_period, duration_value/unit | Backend-owned immutable terms |
| feature_snapshot | Immutable normalized child rows or JSON snapshot |
| status | `CREATED`, `PENDING_PAYMENT`, `PAID`, `APPLIED`, `FAILED`, `CANCELLED`, `EXPIRED`, `REFUNDED` |
| idempotency_key, request_hash | Unique retry protection |
| expires_at, created_at, updated_at, version | Lifecycle metadata |

### PlatformPaymentAttempt and PlatformFinancialTransaction

Mirror the processing/evidence separation used by Property Commerce, but reference `SubscriptionOrder` and use platform merchant configuration. Transaction types are `SUBSCRIPTION_PURCHASE`, `SUBSCRIPTION_RENEWAL`, `SUBSCRIPTION_UPGRADE`, `DOWNGRADE_CREDIT`, `SUBSCRIPTION_REFUND`.

### SoftwareContract

Immutable contract version for an applied subscription order: target property, plan/feature snapshot, effective dates, price terms, originating order/payment and superseded contract where applicable.

### SubscriptionEntitlement and SubscriptionHistory

`SubscriptionEntitlement` is the current enforceable projection. `SubscriptionHistory` is append-only and records previous/new plan, dates, feature limits, source order/payment, actor/source and reason. Replayed callbacks cannot create a second history effect.

### PlatformRefundRequest and PlatformRefundAttempt

Original-platform-transaction-bound refund lifecycle. Entitlement effects require an approved versioned policy; otherwise the request is blocked before provider mutation.

## Shared Evidence and Reporting

### FinancialAuditEvent

Append-only event containing context, aggregate type/id, tenant/system scope, actor/source, previous/new state, reason, idempotency/provider identity, correlation ID, timestamp and redacted metadata.

### RevenueReportSnapshot

Optional persisted evidence for exported/reconciled reports. Contains context, recognition basis, normalized filters, generated totals, row count, source watermark, checksum, requester and generated time.

### FunctionInventoryItem

Documentation-backed inventory row with feature ID, role, menu/route, UI component/service, API/controller/business service/repository/database object, permission, automated/manual tests, status, evidence and linked task.

## State Transitions

### Payment attempt

`CREATED -> PENDING | PENDING_VERIFICATION | PROCESSING | CANCELLED | EXPIRED`  
`PENDING -> PROCESSING | SUCCESS | FAILED | CANCELLED | EXPIRED`  
`PENDING_VERIFICATION -> SUCCESS | FAILED | CANCELLED | EXPIRED`  
`PROCESSING -> SUCCESS | FAILED | CANCELLED | EXPIRED`  
`SUCCESS -> PARTIALLY_REFUNDED -> REFUNDED` through refund ledger effects only.

Terminal states cannot transition back to processing. Repeated equivalent callbacks return the stored result without a second effect; conflicting callbacks are audited and rejected.

### Booking financial state

Derived only: `UNPAID`, `PARTIALLY_PAID`, `DEPOSIT_PAID`, `PAID`, `OVERPAID`, `PARTIALLY_REFUNDED`, `REFUNDED`. Reservation operational status cannot directly set this value.

### Subscription order

`CREATED -> PENDING_PAYMENT -> PAID -> APPLIED`  
`CREATED | PENDING_PAYMENT -> CANCELLED | EXPIRED`  
`PENDING_PAYMENT -> FAILED`  
`APPLIED -> REFUNDED` only through an approved refund policy and successful platform refund.

## Required Constraints and Indexes

- Unique active property configuration per `hotel_id`.
- Unique idempotency key scoped by context/actor or aggregate, plus stored payload hash.
- Unique provider event and provider transaction identity scoped by provider/environment/merchant.
- Unique financial transaction effect identity.
- Unique invoice number and one finalized invoice per checkout aggregate unless versioned correction policy says otherwise.
- Unique subscription application effect per successful order/payment.
- Check constraints for positive amounts, VND currency, valid states and property/system scope.
- Composite property indexes beginning with `hotel_id` for reservation, invoice, transaction, refund and report filters.
- Foreign keys from refunds to original successful transactions and from invoice allocations to immutable transactions.

## Migration and Backfill Rules

1. Create new context tables and indexes without dropping legacy tables.
2. Run preflight queries for orphaned payments, duplicate references, null ownership and ambiguous subscription/booking classification.
3. Backfill only records with deterministic context and owner mapping; record ambiguous rows in a migration exception report and stop before production execution.
4. Add uniqueness and not-null constraints after successful backfill validation.
5. Keep legacy reads behind a temporary compatibility adapter until reconciliation proves parity.
6. Document forward recovery; production rollback means disabling new writes and restoring from verified backup, not silently deleting financial evidence.

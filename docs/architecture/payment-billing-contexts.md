# Feature 007 Payment and Billing Contexts

Verified against source and migrations: 2026-08-03
Feature: 007-payment-billing-completion
Status: canonical context design and persistence are documented; production payment remains disabled and provider-dependent journeys remain subject to the readiness gates listed below.

## 1. Scope

Feature 007 has two financial bounded contexts inside the existing Spring Boot application:

1. Property Commerce: guest-to-property booking money, deposit/manual transfer, checkout folio, invoice, property refund and property revenue.
2. Platform Billing: owner-to-LuxeStay subscription money, system merchant configuration, subscription orders, contracts, entitlements, history, platform refund and platform revenue.

The contexts share only money/value types, provider verification primitives, idempotency storage and audit infrastructure. A Property Commerce payment never activates a Platform Billing entitlement. A Platform Billing payment never settles a reservation.

Design source: [Feature 007 plan](../../specs/007-payment-billing-completion/plan.md), [Feature 007 data model](../../specs/007-payment-billing-completion/data-model.md), [ERD-06/07](../ERD.md) and [UML-20/24](../UML.md).

## 2. Context map

~~~mermaid
flowchart LR
    Guest[Guest/customer] --> PropertyAPI[Property Commerce API]
    Owner[Property owner] --> PlatformAPI[Platform Billing API]
    Staff[Property staff] --> PropertyAPI
    Admin[System administrator] --> PlatformAPI

    subgraph PropertyCommerce[Property Commerce - hotel scoped]
        PropertyAPI --> PropertyConfig[Property payment configuration]
        PropertyAPI --> PropertyAttempt[Payment attempt]
        PropertyAPI --> PropertyCheckout[Checkout / folio / invoice]
        PropertyAPI --> PropertyRefund[Property refund]
        PropertyAttempt --> PropertyLedger[(Property financial ledger)]
        PropertyCheckout --> PropertyLedger
        PropertyRefund --> PropertyLedger
        PropertyCheckout --> PropertyRevenue[Property revenue report]
        PropertyLedger --> PropertyRevenue
    end

    subgraph PlatformBilling[Platform Billing - system merchant]
        PlatformAPI --> PlatformConfig[Platform payment configuration]
        PlatformAPI --> SubscriptionOrder[Subscription order]
        SubscriptionOrder --> PlatformAttempt[Payment attempt]
        PlatformAttempt --> PlatformLedger[(Platform financial ledger)]
        PlatformLedger --> Contract[Software contract]
        Contract --> Entitlement[Current entitlement]
        SubscriptionOrder --> History[Subscription history]
        PlatformAPI --> PlatformRefund[Platform refund]
        PlatformRefund --> PlatformLedger
        PlatformLedger --> PlatformRevenue[Platform revenue report]
    end

    Shared[Provider verification + idempotency + FinancialAuditEvent]
    Shared -.-> PropertyAttempt
    Shared -.-> PlatformAttempt
    Shared -.-> PropertyRefund
    Shared -.-> PlatformRefund
    PropertyLedger -. no cross-context writes .-> PlatformLedger
    PlatformLedger -. never settles .-> Reservation[Reservation aggregate]
    PropertyLedger -. never activates .-> Entitlement
~~~

## 3. Ownership and persistence

| Concern | Property Commerce | Platform Billing | Boundary rule |
| --- | --- | --- | --- |
| Merchant/configuration | property_payment_configurations, property_payment_configuration_methods | platform_payment_configurations | Property config has hotel_id; platform config has provider/environment and no property merchant reference. |
| Mutable processing | PropertyPaymentAttempt | PlatformPaymentAttempt | Attempt rows hold expected amount, environment, provider refs, expiry, request hash and version. |
| Authoritative money | PropertyFinancialTransaction | PlatformFinancialTransaction | Successful charges/refunds/credits are immutable ledger effects with unique economic identities. |
| Booking/entitlement projection | BookingFinancialSummary | SubscriptionEntitlement | Projections may be recalculated/replaced only from canonical evidence and snapshots. |
| Billing snapshot | PropertyInvoice, PropertyInvoiceLine, allocations | SoftwareContract and plan/feature snapshots | Finalized invoices/contracts are append-only; later corrections create credit or replacement records. |
| Refund | PropertyRefundRequest, PropertyRefundAttempt | PlatformRefundRequest, PlatformRefundAttempt | Refund requests bind to an original successful debit and cannot exceed its refundable balance. |
| Audit | financial_audit_events with context=PROPERTY_COMMERCE and hotel_id | Same table with context=PLATFORM_BILLING and null system scope | Audit rows contain actor/source, previous/new state, reason, provider/idempotency identity and correlation ID. |
| Reporting | PropertyRevenueService and property export | PlatformRevenueService and platform export | Reports read only context-owned successful evidence; platform APIs do not accept a property filter. |

Canonical source packages:

- Property Commerce: [com.hotel.propertycommerce](../../backend/src/main/java/com/hotel/propertycommerce), especially [payment](../../backend/src/main/java/com/hotel/propertycommerce/payment), [folio](../../backend/src/main/java/com/hotel/propertycommerce/folio), [invoice](../../backend/src/main/java/com/hotel/propertycommerce/invoice), [checkout](../../backend/src/main/java/com/hotel/propertycommerce/checkout), and [refund](../../backend/src/main/java/com/hotel/propertycommerce/refund).
- Platform Billing: [com.hotel.platformbilling](../../backend/src/main/java/com/hotel/platformbilling), especially [order](../../backend/src/main/java/com/hotel/platformbilling/order), [payment](../../backend/src/main/java/com/hotel/platformbilling/payment), [subscription](../../backend/src/main/java/com/hotel/platformbilling/subscription), [refund](../../backend/src/main/java/com/hotel/platformbilling/refund), and [reporting](../../backend/src/main/java/com/hotel/platformbilling/reporting).
- Shared evidence: [FinancialAuditEvent](../../backend/src/main/java/com/hotel/paymentprovider/audit/FinancialAuditEvent.java), [FinancialTransitionPolicy](../../backend/src/main/java/com/hotel/paymentprovider/domain/FinancialTransitionPolicy.java), [FinancialStates](../../backend/src/main/java/com/hotel/paymentprovider/domain/FinancialStates.java), and [VndMoney](../../backend/src/main/java/com/hotel/paymentprovider/domain/VndMoney.java).

## 4. Aggregate transitions

### 4.1 Payment attempts

Both contexts use the same validated attempt state machine:

~~~text
CREATED -> PENDING | PENDING_VERIFICATION | CANCELLED | EXPIRED
PENDING -> PROCESSING | SUCCESS | FAILED | CANCELLED | EXPIRED
PENDING_VERIFICATION -> SUCCESS | FAILED | CANCELLED | EXPIRED
PROCESSING -> SUCCESS | FAILED | CANCELLED | EXPIRED
SUCCESS -> PARTIALLY_REFUNDED -> REFUNDED
~~~

FAILED, CANCELLED and EXPIRED are terminal. Repeating the same state is idempotent; a disallowed or conflicting state is rejected. The executable policy is [FinancialTransitionPolicy](../../backend/src/main/java/com/hotel/paymentprovider/domain/FinancialTransitionPolicy.java), and the enums are [FinancialStates](../../backend/src/main/java/com/hotel/paymentprovider/domain/FinancialStates.java).

### 4.2 Property booking financial state

BookingFinancialSummary is derived from charges, successful debits and refund credits. It is independent from the reservation operational status:

~~~text
UNPAID | PARTIALLY_PAID | DEPOSIT_PAID | PAID | OVERPAID
       | PARTIALLY_REFUNDED | REFUNDED
~~~

The summary is recomputed under the reservation lock. The client cannot supply the authoritative amount paid, deposit, balance or state.

### 4.3 Property checkout

1. Lock the reservation aggregate and load authoritative room, service, surcharge, tax, discount, payment and refund evidence.
2. Append immutable ReservationChargeLine snapshots for new service/surcharge effects; corrections use a reversal/adjustment line.
3. Compute the remaining balance. Normal checkout rejects debt; an authorized debt override requires a reason and actor in CheckoutOverride.
4. Finalize one PropertyInvoice with immutable PropertyInvoiceLine rows and allocate prior successful debit transactions exactly once.
5. Create any final payment, release assigned rooms, set rooms dirty and create housekeeping work once using the checkout effect key.
6. Append audit evidence. Any failure rolls back the checkout transaction while retaining prior immutable financial rows.

The persistence boundary is implemented by [CheckoutOperationsService](../../backend/src/main/java/com/hotel/propertycommerce/checkout/CheckoutOperationsService.java), [InvoiceFinalizationService](../../backend/src/main/java/com/hotel/propertycommerce/invoice/InvoiceFinalizationService.java) and [BookingFinancialSummaryService](../../backend/src/main/java/com/hotel/propertycommerce/booking/BookingFinancialSummaryService.java).

### 4.4 Refunds and corrections

Property and platform refunds follow the same evidence rule. Property requests use REQUESTED, PENDING_APPROVAL, PENDING_PROVIDER, SUCCEEDED, FAILED and CANCELLED. Platform requests may additionally enter POLICY_BLOCKED when no approved entitlement/refund policy exists:

~~~text
REQUESTED -> PENDING_APPROVAL | POLICY_BLOCKED | PENDING_PROVIDER
PENDING_APPROVAL -> PENDING_PROVIDER | CANCELLED
PENDING_PROVIDER -> SUCCEEDED | FAILED | CANCELLED
~~~

The service locks the original successful debit, calculates the remaining refundable balance including active reservations, and rejects over-refunds before provider mutation. Success creates a new immutable credit transaction linked by original_transaction_id; the original debit remains unchanged. Property invoice corrections use PropertyCreditNote and PropertyCreditNoteLine, never an invoice update.

Property implementation: [PropertyRefundService](../../backend/src/main/java/com/hotel/propertycommerce/refund/PropertyRefundService.java). Platform implementation: [PlatformRefundService](../../backend/src/main/java/com/hotel/platformbilling/refund/PlatformRefundService.java) with [PlatformRefundEntitlementPolicy](../../backend/src/main/java/com/hotel/platformbilling/refund/PlatformRefundEntitlementPolicy.java). No default platform refund policy is registered; unsupported subscription refund/proration is fail-closed.

### 4.5 Platform subscription

~~~text
CREATED -> PENDING_PAYMENT -> PAID -> APPLIED
CREATED | PENDING_PAYMENT -> CANCELLED | EXPIRED
PENDING_PAYMENT -> FAILED
APPLIED -> REFUNDED (approved policy only)
~~~

The order snapshots backend plan code/version/name, price, billing period, duration and feature limits. Only a verified successful platform debit can create a SoftwareContract, update the current SubscriptionEntitlement and append SubscriptionHistory. Equivalent callback replay returns the stored application result. The implementation is [SubscriptionApplicationService](../../backend/src/main/java/com/hotel/platformbilling/subscription/SubscriptionApplicationService.java) and [PlatformPaymentCallbackService](../../backend/src/main/java/com/hotel/platformbilling/payment/PlatformPaymentCallbackService.java).

## 5. Consistency and isolation rules

- Server authority: amount, currency, provider, merchant, order/booking binding, expiry, refundable balance, plan terms and entitlement effects are resolved from backend state.
- Idempotency: payment creation, callbacks, checkout, refund requests and subscription application persist an idempotency key and request hash. Equivalent replay returns the stored result; a payload mismatch is rejected.
- Append-only evidence: financial transactions, finalized invoice/header/lines, credit notes, subscription contracts and subscription history reject application update/delete. Mutable attempts and projections retain version metadata.
- Tenant scope: property entities carry hotel_id and are filtered by the authenticated property scope. Platform records use explicit system scope; target_hotel_id is entitlement target metadata, not a property revenue partition.
- Environment safety: SIMULATOR, SANDBOX and PRODUCTION are explicit. Production requires approval evidence and remains disabled by default; no simulator callback is labelled live.
- Audit: material transitions record context, aggregate, previous/new state, actor/source, reason, provider/idempotency identity, correlation ID and timestamp in financial_audit_events.

## 6. Migration and recovery

Feature 007 migrations are additive and forward-only:

| Migration | Context/evidence |
| --- | --- |
| [V21](../../backend/src/main/resources/db/migration/V21__property_commerce_foundation.sql) | Property configuration, attempts, ledger and booking financial summary. |
| [V22](../../backend/src/main/resources/db/migration/V22__property_checkout_invoice.sql) | Charge lines, property invoice, invoice lines/allocations, credit notes and checkout overrides. |
| [V23](../../backend/src/main/resources/db/migration/V23__property_refund_audit.sql) | Property refunds and shared financial audit events. |
| [V24](../../backend/src/main/resources/db/migration/V24__platform_billing_foundation.sql) | Platform merchant configuration, subscription orders, attempts and ledger. |
| [V25](../../backend/src/main/resources/db/migration/V25__platform_contract_refund.sql) | Contracts, entitlements, history and platform refunds. |
| [V26](../../backend/src/main/resources/db/migration/V26__financial_context_backfill.sql) | Legacy backfill and unresolved mapping exceptions. |
| [V27](../../backend/src/main/resources/db/migration/V27__financial_integrity_indexes.sql) | Provider/effect uniqueness and report indexes. |
| [V29](../../backend/src/main/resources/db/migration/V29__financial_idempotency.sql) | Shared request idempotency records. |
| [V30](../../backend/src/main/resources/db/migration/V30__booking_deposit_policy_snapshot.sql) | Reservation deposit policy snapshot. |
| [V31](../../backend/src/main/resources/db/migration/V31__property_attempt_transfer_content_uniqueness.sql) | Active transfer-content uniqueness. |
| [V32](../../backend/src/main/resources/db/migration/V32__credit_note_line_tenant_ownership.sql) | Credit-note-line tenant ownership. |
| [V33](../../backend/src/main/resources/db/migration/V33__housekeeping_checkout_idempotency.sql) | One housekeeping effect per checkout. |
| [V34](../../backend/src/main/resources/db/migration/V34__legacy_subscription_entitlement_projection.sql) | Compatibility projection for legacy subscription data. |

Before production execution, run duplicate/orphan/null-owner preflight checks. Ambiguous legacy rows are written to financial_migration_exceptions and block production backfill. Recovery disables new writes and replays verified callbacks from retained immutable evidence; it does not delete financial rows or silently reverse a migration.

## 7. Validation evidence and current limits

| Area | Evidence | Status |
| --- | --- | --- |
| Property payment config and tenant boundary | [PROPERTY_PAYMENT_AUDIT](../audit/financial/PROPERTY_PAYMENT_AUDIT.md) | COMPLETE_VERIFIED for simulator/readiness contract; sandbox/production external gates remain blocked. |
| Property attempt and ledger persistence | [payment-attempt-ledger-persistence](../testing/evidence/007/property-commerce/payment-attempt-ledger-persistence.md) | 9 tests passed, 0 failures/errors/skips (2026-07-31). |
| Property invoice/folio/refund | [PROPERTY_REVENUE_RECONCILIATION](../audit/financial/PROPERTY_REVENUE_RECONCILIATION.md), [refund-services](../testing/evidence/007/refunds/refund-services.md) | Deterministic reconciliation and service tests documented; browser/full-worktree completion remains separate. |
| Platform order, callback, contract and entitlement | [PLATFORM_BILLING_AUDIT](../audit/financial/PLATFORM_BILLING_AUDIT.md), [platform-billing-model](../testing/evidence/007/platform-billing/platform-billing-model.md), [subscription-application](../testing/evidence/007/platform-billing/subscription-application.md) | Core simulator/deterministic flows verified; production merchant and downgrade/proration policy are blocked external. |
| Platform revenue isolation | [PLATFORM_REVENUE_RECONCILIATION](../audit/financial/PLATFORM_REVENUE_RECONCILIATION.md) | Deterministic report/export reconciliation documented; no property filter accepted. |
| Production safety | [PRODUCTION_READINESS_CHECKLIST](../testing/PRODUCTION_READINESS_CHECKLIST.md) | Production remains disabled; no credentials or real money used. |

This architecture document records the implemented canonical ownership and transition contracts. It does not mark blocked external provider work, callback-abuse gaps, Angular compile failures or final-worktree regression as complete.

# Feature Specification: Payment, Billing, Checkout and Revenue Completion

**Feature Branch**: `[007-payment-billing-completion]`

**Created**: 2026-07-31

**Status**: Draft - audit and planning only; production payment remains disabled until a separate readiness approval.

**Input**: Audit and complete two independent financial bounded contexts: Property Commerce, where guests pay an accommodation property, and Platform Billing, where property owners pay LuxeStay for SaaS subscriptions. Include checkout, invoices, refunds, reconciliation, reporting, full-system traceability and final-worktree verification without using real money or production credentials.

## User Scenarios & Testing

### User Story 1 - Keep property money and platform money separate (Priority: P1)

Finance operators and auditors can identify whether every charge, refund, invoice and report belongs to a property or to the LuxeStay platform, with explicit lifecycle and ownership rules.

**Why this priority**: Mixing the two revenue domains can misstate property income, platform income, tax obligations and refund liability.

**Independent Test**: Create one property booking payment and one subscription payment, then prove that each uses its own merchant/configuration scope, ledger, report and authorization boundary.

**Acceptance Scenarios**:

1. **Given** a guest pays for a booking, **When** the transaction succeeds, **Then** it appears only in Property Commerce for the booked property.
2. **Given** an owner purchases a subscription, **When** the transaction succeeds, **Then** it appears only in Platform Billing and never in property revenue.
3. **Given** any financial transition, **When** it is inspected, **Then** its previous state, new state, actor/source, reason and audit record are available.

---

### User Story 2 - Pay a property deposit safely (Priority: P1)

A guest can create a booking, receive the correct property-specific deposit amount, scan a test QR or use an enabled sandbox gateway, and see a truthful pending, verification, success, failure or expiry state.

**Why this priority**: Deposit errors directly affect inventory, guest trust and the property's cash collection.

**Independent Test**: Book one room against a configured test property, generate a unique manual-transfer instruction and payment attempt, then verify that reload/retry cannot duplicate the attempt and only an authoritative event can confirm it.

**Acceptance Scenarios**:

1. **Given** a property has a fixed or percentage deposit policy, **When** a booking is created, **Then** the server calculates and snapshots the required deposit.
2. **Given** manual transfer is enabled, **When** payment instructions open, **Then** the QR and manual details contain the property's bank identity, exact amount, unique transfer content and expiry.
3. **Given** a guest reloads or retries, **When** the same idempotency key is used, **Then** the original payment attempt is returned.
4. **Given** a public client claims success without valid proof, **When** the request is processed, **Then** no financial or booking state changes.

---

### User Story 3 - Settle a stay from check-in to invoice (Priority: P1)

Authorized property staff can assign rooms, check in, add services and surcharges, collect one or more payments, settle the remaining balance, check out and issue a final invoice without double-counting the deposit.

**Why this priority**: Checkout combines inventory, room state, payment, invoice and housekeeping, so partial failure can corrupt several operational records at once.

**Independent Test**: Run booking -> deposit -> check-in -> service -> surcharge -> remaining payment -> checkout and reconcile the final invoice, payments, room state and housekeeping task.

**Acceptance Scenarios**:

1. **Given** a checked-in reservation, **When** a service or surcharge is added, **Then** its name/type, unit price, quantity, tax, total, actor and timestamp are snapshotted.
2. **Given** prior deposits and payments exist, **When** checkout is calculated, **Then** the remaining balance is server-derived and overpayment is reported explicitly.
3. **Given** debt remains, **When** normal checkout is attempted, **Then** checkout is blocked; an authorized override requires a reason and audit event.
4. **Given** checkout succeeds, **When** the transaction commits, **Then** the invoice is finalized, assigned rooms become dirty and housekeeping tasks are created exactly once.
5. **Given** any checkout step fails, **When** the transaction rolls back, **Then** reservation, payment, invoice, room and housekeeping states remain consistent.

---

### User Story 4 - Purchase and manage a SaaS subscription (Priority: P1)

A property owner can create a platform subscription order, pay with a platform test merchant, and activate, renew or upgrade the subscription exactly once from an authoritative successful payment.

**Why this priority**: Subscription billing controls access to paid features and is a separate source of platform revenue.

**Independent Test**: Create an order from a server-owned plan snapshot, complete a sandbox callback, and verify exactly one subscription/contract/history change and one Platform Billing transaction.

**Acceptance Scenarios**:

1. **Given** an owner selects a plan, **When** an order is created, **Then** price, duration, billing type and features are snapshotted from the active backend plan.
2. **Given** a valid successful platform callback, **When** it is replayed, **Then** activation or renewal occurs exactly once.
3. **Given** payment fails, expires or is cancelled, **When** the event is recorded, **Then** no subscription feature is activated.
4. **Given** an upgrade, downgrade or refund is requested, **When** policy does not support the transition, **Then** the action is blocked with a truthful explanation rather than silently recalculating entitlement.

---

### User Story 5 - Refund safely and partially (Priority: P1)

Authorized actors can request full or partial refunds for property payments or platform subscription payments without exceeding the refundable balance or creating duplicate effects.

**Why this priority**: Refunds reverse money, loyalty/entitlements and recognized revenue and therefore require strict original-payment linkage.

**Independent Test**: Submit repeated and concurrent partial/full refund requests and prove that cumulative success never exceeds the original successful charge.

**Acceptance Scenarios**:

1. **Given** a successful payment with remaining refundable balance, **When** a partial refund succeeds, **Then** the original payment remains immutable and both balances are updated exactly once.
2. **Given** refund requests exceed the refundable balance, **When** they are submitted, **Then** they are rejected before provider mutation.
3. **Given** a manual refund, **When** an unauthorized employee attempts it, **Then** the action is denied and audited without changing financial records.

---

### User Story 6 - Reconcile property and platform reports (Priority: P2)

Property owners see only their property's collected, invoiced and net revenue, while system administrators see only platform subscription revenue in the SaaS report.

**Why this priority**: Dashboards and exports are useful only when their totals reconcile to authoritative transactions and invoice lines.

**Independent Test**: Compare report API totals and exported files directly with successful payments, finalized invoice lines, refunds and credits for a fixed date range.

**Acceptance Scenarios**:

1. **Given** property transactions, **When** a property report is generated, **Then** gross, refunds and net reconcile and exclude platform subscription money.
2. **Given** subscription transactions, **When** a SaaS report is generated, **Then** new purchase, renewal, upgrade, refund and net reconcile and exclude booking money.
3. **Given** pending, failed, cancelled or expired transactions, **When** cash-collected totals are calculated, **Then** those transactions are excluded.
4. **Given** an export request, **When** it completes, **Then** the file uses the same filters, rows and totals as the report API.

---

### User Story 7 - Verify the complete system from the final worktree (Priority: P2)

The project owner receives a master function inventory, traceability matrix, error catalog and manual test guide covering every reachable role/module, with each capability classified from evidence rather than code appearance.

**Why this priority**: Financial completion can still fail when authentication, permissions, inventory, notifications, reporting or responsive states remain partial or disconnected.

**Independent Test**: Rebuild a clean test database from migrations, execute automated and browser journeys for all supported roles, and reconcile the resulting evidence with the inventory and task list.

**Acceptance Scenarios**:

1. **Given** a reachable menu, route, API or documented feature, **When** inventory is generated, **Then** it maps through UI, service, database, permission, tests, status and evidence.
2. **Given** a capability classified PARTIAL, PLACEHOLDER, BROKEN or MISSING, **When** planning finishes, **Then** an actionable task exists or the item is explicitly BLOCKED_EXTERNAL with contract/simulator guidance.
3. **Given** the final worktree, **When** release verification runs, **Then** historical test results are not substituted for fresh results.

### Edge Cases

- A callback is valid but arrives after a booking/order/payment attempt expired or was cancelled.
- Two callbacks or staff confirmations arrive concurrently for the same payment.
- The gateway reports success with the wrong amount, currency, merchant, property or order reference.
- A QR expires, a guest transfers too little or too much, or transfer content does not match uniquely.
- A plan price/features change while an order is awaiting payment.
- A property attempts to use platform merchant configuration or a subscription uses property bank details.
- Multiple payment methods settle one invoice and one method later requires a partial refund.
- Checkout fails after payment persistence but before invoice, room or housekeeping updates.
- An invoice is already finalized when a service correction or refund is required.
- Two users refund the same charge concurrently or cumulative partial refunds exceed the available amount.
- Production mode lacks a required secret or provider health check.
- A property owner, receptionist or customer requests another property's payment, invoice, refund, report or order.
- Export generation times out or returns rows different from the visible report filter.

## Requirements

### Functional Requirements

- **FR-001**: The system MUST maintain independent Property Commerce and Platform Billing bounded contexts, ledgers, merchant/configuration scopes and revenue reports.
- **FR-002**: Property Commerce MUST classify financial events as booking deposit, room payment, service payment, surcharge, manual adjustment or refund.
- **FR-003**: Platform Billing MUST classify financial events as subscription purchase, renewal, upgrade, downgrade credit or subscription refund.
- **FR-004**: Payment lifecycle MUST support CREATED, PENDING, PENDING_VERIFICATION, PROCESSING, SUCCESS, FAILED, CANCELLED, PARTIALLY_REFUNDED, REFUNDED and EXPIRED with validated transitions.
- **FR-005**: Booking financial state MUST be separate from reservation status and support UNPAID, PARTIALLY_PAID, DEPOSIT_PAID, PAID, OVERPAID, PARTIALLY_REFUNDED and REFUNDED.
- **FR-006**: Every financial state transition MUST record actor/source, previous/new state, reason, timestamps, idempotency identity and audit evidence.
- **FR-007**: Every amount-changing operation MUST validate amount, VND currency, ownership, context, reference and current refundable/payable balance on the server.
- **FR-008**: Each property MUST have tenant-owned payment configuration for enablement, environment mode, allowed methods, bank identity, deposit policy, expiry, transfer template, QR provider, bilingual instructions and masked merchant status.
- **FR-009**: Secrets and full sensitive account identifiers MUST NOT be returned to clients or written to logs; production secrets MUST come from approved environment, encrypted configuration or secret storage.
- **FR-010**: Production payment MUST fail closed, remain disabled by default, never fall back to sandbox/simulator and never be labelled live while using a test environment.
- **FR-011**: All Property Commerce payment, invoice, refund and report operations MUST be property-scoped and authorized from the authenticated principal rather than caller-controlled tenant identity.
- **FR-012**: Manual transfer instructions MUST include the receiving property's bank identity, exact amount, unique transfer content, booking/payment reference and expiry, with equivalent manual text when QR cannot be scanned.
- **FR-013**: Manual transfer MUST remain PENDING_VERIFICATION until an authentic webhook or an actor with explicit confirmation permission approves it.
- **FR-014**: Creating or reloading a payment flow MUST be idempotent and MUST NOT create a duplicate attempt for the same payload/key.
- **FR-015**: Booking deposit MUST be calculated from a server-owned fixed/percentage property policy and snapshotted with the booking/payment attempt.
- **FR-016**: Property Commerce MUST support multiple successful payments and methods while preserving each transaction as an immutable financial record.
- **FR-017**: Reservation services MUST snapshot service identity, description, unit price, quantity, tax, total, actor and usage time.
- **FR-018**: Surcharges MUST use explicit types, description, amount, actor, time, permission and audit records; corrections MUST create adjustment lines rather than mutate prior payments.
- **FR-019**: Checkout MUST recompute room, service, surcharge, tax, discount, deposit, prior payments, refund and remaining balance from authoritative server data.
- **FR-020**: Checkout MUST detect underpayment and overpayment, support multiple methods, and require an authorized reasoned override before checking out with debt.
- **FR-021**: Successful checkout MUST atomically finalize the invoice, release assignments, move rooms to dirty state and create housekeeping work exactly once; failure MUST roll back the aggregate.
- **FR-022**: Final invoices MUST contain immutable pricing/tax/fee/discount/service/surcharge/payment snapshots; later corrections MUST use a credit note or adjustment mechanism.
- **FR-023**: Customers and authorized staff MUST be able to view and export/email the correct final invoice without cross-account or cross-property access.
- **FR-024**: Refunds MUST reference an original successful payment, support full/partial amounts, preserve the original payment and reject cumulative refunds above the refundable balance.
- **FR-025**: Refund requests and provider attempts MUST be idempotent, transition-controlled and record reason, actor, approver, provider reference, timestamps and audit evidence.
- **FR-026**: Manual refund and debt-override actions MUST require separate permissions and MUST be auditable.
- **FR-027**: Platform Billing MUST create a subscription order before payment and snapshot backend-owned plan price, billing period, duration and features.
- **FR-028**: Platform Billing MUST use system-owned merchant/bank/provider configuration that is separate from every property configuration.
- **FR-029**: Only an authoritative successful platform payment MAY activate, renew or upgrade a subscription; failure, cancellation or expiry MUST NOT enable features.
- **FR-030**: Replayed or concurrent subscription callbacks MUST NOT duplicate subscription duration, contracts, history or platform revenue.
- **FR-031**: Subscription lifecycle MUST explicitly support purchase, renewal, upgrade, downgrade policy, revoke, expire, refund, payment history, contract and entitlement history.
- **FR-032**: Upgrade/downgrade proration or credit rules MUST be explicit; unsupported transitions MUST be blocked and explained.
- **FR-033**: Subscription refunds MUST update payment, order, contract, entitlement and history according to an approved policy without using Property Commerce transactions.
- **FR-034**: Property reports MUST expose gross revenue, refunds, net revenue, room/service/surcharge/tax/fee/discount, cash collected, unpaid balance, held deposits, method, room type, top services, transactions and reconciliation queues.
- **FR-035**: Platform reports MUST expose new purchase, renewal, upgrade, refunds/credits, net revenue, plan mix, subscription status counts, success/failure rate and unreconciled transactions; recurring metrics apply only to recurring plans.
- **FR-036**: Reports MUST distinguish cash collected, invoiced revenue and net revenue and MUST exclude non-successful transactions from collected totals.
- **FR-037**: Property and platform exports MUST use the same date/provider/property filters, rows and totals as their report APIs.
- **FR-038**: Automated reconciliation MUST compare report totals with authoritative payments, finalized invoice lines, refunds/credits and exported files, without double-counting deposits.
- **FR-039**: Property-scoped financial entities MUST carry property ownership and enforce automatic tenant filtering plus authenticated property access; platform billing records MUST use explicit system scope.
- **FR-040**: Customers, property roles and system administrators MUST be restricted to their authorized financial resources and actions, including confirmation, refund, debt override and report access.
- **FR-041**: Provider callbacks MUST validate signature, merchant, amount, currency, transaction, order/booking and replay identity without relying on customer JWT, and status polling/callbacks MUST have appropriate abuse controls.
- **FR-042**: Financial schema changes MUST use new non-destructive migrations, idempotent backfill, duplicate pre-checks, unique constraints/indexes and documented rollback/recovery; production migration requires separate approval.
- **FR-043**: A master function inventory MUST map every reachable system feature through route/menu, UI, API, backend service, database, permission, automated/manual tests, status and evidence.
- **FR-044**: Every PARTIAL, PLACEHOLDER, BROKEN or MISSING inventory item MUST produce an implementation task; BLOCKED_EXTERNAL items MUST still provide simulator/adapter/contract/configuration guidance.
- **FR-045**: Final verification MUST rebuild a clean test database, run migrations from the beginning, seed deterministic data and execute backend, frontend, browser, security, tenant, concurrency, export and reconciliation tests on the final worktree.
- **FR-046**: A non-technical manual test guide and error expectation catalog MUST document roles, data, UI steps, expected HTTP/database/state/audit effects, retry behavior, screenshots and safe reset instructions.

### Key Entities

- **PropertyPaymentConfiguration**: Tenant-owned payment modes, bank identity, deposit policy, expiry, QR/instruction and masked gateway configuration.
- **FinancialTransaction**: Immutable context-owned charge, payment, adjustment, credit or refund record with amount, currency, method, lifecycle and idempotency identity.
- **PaymentAttempt**: Provider/manual-transfer attempt bound to a payable aggregate, expected amount, environment, expiry and provider references.
- **BookingFinancialSummary**: Server-derived deposit, successful payments, refunds, remaining balance and financial state for a reservation.
- **ReservationChargeLine**: Snapshotted room, service, surcharge, tax, fee, discount or adjustment line.
- **Invoice / InvoiceLine / CreditNote**: Finalized financial snapshot and post-finalization correction records.
- **RefundRequest / RefundAttempt**: Original-payment-bound full/partial refund lifecycle and provider/manual processing evidence.
- **SubscriptionOrder**: Owner/platform purchase or lifecycle order with immutable plan/price/duration/feature snapshot.
- **PlatformPaymentConfiguration**: System-owned merchant/provider/environment configuration separate from property accounts.
- **SubscriptionPayment / SoftwareContract / SubscriptionHistory**: Platform billing payment, contract and entitlement lifecycle evidence.
- **FinancialAuditEvent**: Actor/source transition record for material financial operations.
- **RevenueReportSnapshot**: Reconciliation-ready property or platform reporting result with recognition basis and filters.
- **FunctionInventoryItem**: Traceability and evidence classification for a reachable product capability.

## Success Criteria

### Measurable Outcomes

- **SC-001**: 100% of financial records and reports are classified into exactly one of Property Commerce or Platform Billing.
- **SC-002**: Payment, booking-financial and refund transition suites reject every disallowed transition and produce an audit event for every material accepted transition.
- **SC-003**: Repeated and concurrent payment/refund/subscription callbacks create exactly one financial and entitlement effect.
- **SC-004**: A property-specific test booking displays the correct deposit, bank identity, unique transfer content, expiry and environment label; no public UI action can self-confirm success.
- **SC-005**: Checkout reconciliation matches room, service, surcharge, tax, discount, prior payment, refund and remaining balance to the smallest VND unit for every fixture.
- **SC-006**: A forced failure at each checkout persistence boundary leaves reservation, invoice, payment, room and housekeeping state consistent.
- **SC-007**: Cumulative successful refunds never exceed the refundable balance under sequential, replayed or concurrent requests.
- **SC-008**: Valid subscription purchase/renewal callbacks activate or extend entitlement exactly once; failed/expired/tampered callbacks activate zero features.
- **SC-009**: Property gross minus property refunds equals property net, and platform gross minus platform refunds/credits equals platform net for every report fixture.
- **SC-010**: Report API totals, detail rows and Excel/PDF exports match authoritative database assertions for the same filter.
- **SC-011**: Cross-property/customer IDOR tests return denial/not-found and create no unauthorized financial mutation; platform records never use property merchant configuration.
- **SC-012**: Production mode fails closed when required configuration is absent and remains disabled until a separately approved readiness checklist passes.
- **SC-013**: The master inventory classifies 100% of reachable routes, menus, APIs and documented modules with evidence; every non-complete in-scope item has a task or explicit external blocker.
- **SC-014**: Mandatory backend, frontend, clean-migration, browser, security, tenant, concurrency, export and reconciliation checks run on the final worktree with no skipped required test.
- **SC-015**: The manual guide covers the five mandatory end-to-end journeys and documents positive, negative, concurrent, timeout and rollback expectations for every in-scope module.

## Assumptions

- Settlement currency remains VND; multi-currency conversion is outside this feature.
- Property Commerce pays the booked property directly by default; platform collection and downstream property settlement require a separate future specification.
- Simulator and official sandbox adapters are acceptable for development evidence; no real money or production merchant account is used.
- Automatic downgrade/proration and subscription-refund entitlement effects remain policy-gated until explicitly approved; unsupported behavior must be blocked truthfully.
- Tax/fee rules are snapshot-driven and configurable; no new tax policy is invented by implementation.
- Existing reservation locking, payment provider adapters, refund lifecycle and subscription entitlement reads are reused where they satisfy the new contracts.
- Existing dirty worktree changes and Feature 006 artifacts are preserved; this feature is a separate planning stream.

## Approved Hotel Ownership And Subscription Policy

The policy supplied on 2026-08-04 is authoritative for T241-T244:

- Each hotel has exactly one active `PRIMARY_OWNER`, zero or more active `CO_OWNER` memberships, and at most 10 active owners by default through configuration. A user cannot hold multiple active owner memberships for the same hotel.
- Only the primary owner may invite/remove co-owners, grant/revoke `BILLING_ADMIN`, initiate primary transfer, manage legal/billing identity, manage the subscription, or request hotel closure. A co-owner may operate the hotel and may leave voluntarily, but cannot exercise primary-owner authority by default.
- Co-owner invitations use a one-time token whose hash only is persisted, expire after 7 days, grant no authority while pending, and require the authenticated verified account to match the invited email and accept owner terms.
- A newly accepted owner has a 7-day cooling period before billing administration, owner administration, primary transfer eligibility, or hotel closure authority becomes effective.
- Primary ownership transfer is a two-party, 7-day request. The current primary owner re-authenticates to initiate it; an eligible active co-owner accepts after reviewing subscription responsibility. Acceptance atomically promotes the recipient and demotes the former primary owner to co-owner, invalidates affected authorization state, audits the transition, and notifies owners.
- Transfer is blocked by overdue subscription invoices, open subscription disputes/chargebacks, pending subscription refunds, or a pending software-contract change. Active or future property bookings do not block transfer.
- A primary owner cannot leave or be removed. A co-owner may leave or be removed unless they are the recipient of a pending transfer. Membership history is retained with `LEFT`, `REMOVED`, or `SUSPENDED` status and actor/time/reason evidence.
- The subscription belongs to the hotel, never to the primary-owner account. Ownership changes do not create a subscription, change the paid term/features/expiry, refund, or prorate. Issued invoice snapshots and original transaction/refund/chargeback responsibility remain immutable.
- A personal payment method belonging to the former owner is never transferred automatically. The new primary owner must confirm a valid billing profile/payment method before renewal; already-paid access remains through the current term.

T241 implements the ownership lifecycle and subscription-preservation boundary required above. Hotel close/delete workflow and exceptional Super Admin debt-assumption override are deferred because they are not required to complete PROP-SUB-015 and require separate operational workflows; they must not be approximated by partial behavior.

# Research: Payment, Billing, and Full-System Completion

## Current Baseline Findings

The repository already contains useful foundations: server-owned payment sessions, reservation-owner binding, callback signature checks for VNPay/MoMo/ZaloPay, a signed simulator, refund request/provider-attempt records, reservation holds and concurrency tests. These components should be reused after their ownership and context contracts are tightened.

The audit also found blocking gaps:

- Property-specific bank/QR/deposit configuration is not modeled end to end.
- Existing `Payment` data is not consistently property-owned or context-classified.
- Hibernate filter annotations are present in places, but no reliable request-bound `enableFilter(...)` activation was found.
- Invoice data lacks immutable item, tax, discount, service, surcharge, payment and correction snapshots.
- Checkout trusts caller-supplied financial values in parts of the flow, permits debt without a formal override policy, and lacks rollback-boundary tests.
- Subscription billing lacks a complete public lifecycle, authoritative order snapshots, strong callback binding, idempotent renewal/upgrade behavior and explicit policy handling.
- Analytics revenue is hard-coded/mock and does not reconcile to financial records.
- Existing Flyway history does not fully describe every foundational table, so clean-schema and legacy-schema validation are both necessary.

## Decision 1: Separate ledgers and APIs

**Decision**: Use separate Property Commerce and Platform Billing aggregates, tables, repositories, services, permissions, API namespaces and reports. Shared provider primitives cannot decide domain effects.

**Rationale**: The payer, payee, tenant scope, merchant configuration, revenue recognition and entitlement effects differ. Separation prevents a booking callback from affecting subscriptions and prevents tenant bank details from collecting platform fees.

**Alternatives considered**:

- One `payments` table with a nullable `hotel_id`: rejected because nullability and type branching are easy to bypass and make tenant filtering/reporting fragile.
- One payment service with strategy flags: rejected because authorization and domain side effects remain coupled.

## Decision 2: Attempts are mutable; ledger evidence is append-only

**Decision**: Provider/manual attempts have controlled statuses. Successful payment, refund, credit and adjustment effects create immutable financial transaction rows and audit events. Existing rows are never rewritten to represent a different economic event.

**Rationale**: Provider processing changes over time, but accounting evidence must remain traceable and independently reconcilable.

**Alternatives considered**:

- Mutate a single payment row through charge and refund states: rejected because partial refunds and multiple payment methods lose information.
- Full event sourcing: rejected as excessive for the current codebase; append-only financial events plus aggregate summaries provide the required evidence with lower migration risk.

## Decision 3: VND uses scale-zero exact arithmetic

**Decision**: Persist money as `decimal(19,0)` and use validated `BigDecimal` values with scale zero. Reject fractional VND, negative charge quantities and client-calculated totals.

**Rationale**: Exact integer VND removes floating-point and rounding ambiguity. Server-owned calculations remain deterministic.

**Alternatives considered**:

- `double`: rejected due to precision errors.
- Minor-unit `long`: viable, but the project already uses decimal monetary fields and SQL reporting is clearer with `decimal(19,0)`.

## Decision 4: Idempotency has business and provider identities

**Decision**: Store a caller idempotency key plus a provider event/transaction identity. Enforce unique constraints within the owning context and payable aggregate. Hash request payloads and reject key reuse with a different payload.

**Rationale**: Replays can originate from UI retries, network retries, callback delivery or concurrent workers. One identifier cannot safely cover all sources.

**Alternatives considered**:

- In-memory replay cache: rejected because it fails across restarts and multiple application instances.
- Provider transaction ID only: rejected because it does not protect pre-provider session creation or manual confirmation.

## Decision 5: Tenant scope is established server-side and filter-backed

**Decision**: Resolve accessible/current `hotel_id` from the authenticated principal through `PropertyAccessService`, enable the Hibernate tenant filter for the transaction/request, and still perform aggregate ownership checks on financial mutations. Platform Billing uses explicit system scope and never enables a property merchant path.

**Rationale**: Filter activation provides default query isolation, while explicit ownership validation protects native queries, callbacks and privileged workflows.

**Alternatives considered**:

- Caller-supplied property ID: rejected as an IDOR risk.
- Repository method naming only: rejected by the constitution and prone to omissions.

## Decision 6: Callback verification is adapter-level; effects are application-level

**Decision**: Provider adapters normalize and verify signature, merchant, amount, currency, reference and event identity. Context services then lock the attempt/order and apply exactly one domain effect. Callbacks do not require customer JWT, but use rate limits, replay checks and structured audit logs.

**Rationale**: Providers call the API independently of user sessions. Separating cryptographic verification from domain effects keeps adapters testable and prevents provider payloads from bypassing business rules.

**Alternatives considered**:

- Callback controllers directly updating reservations/subscriptions: rejected because it duplicates logic and weakens transaction boundaries.

## Decision 7: Checkout is one locked aggregate transaction

**Decision**: Checkout recomputes the complete folio from server-owned room charges, snapshotted service/surcharge lines, taxes, discounts, successful payments and refunds. It locks the reservation, validates balance/override, finalizes the invoice, releases assignments, marks rooms dirty and creates housekeeping tasks in one transaction.

**Rationale**: Partial completion creates financial and operational corruption. Boundary failure injection is required to prove rollback.

**Alternatives considered**:

- Multiple controller calls for pay, invoice, room and housekeeping: rejected because retry/failure can duplicate or omit steps.

## Decision 8: Final invoices are immutable snapshots

**Decision**: Finalization copies all descriptive and monetary values into invoice lines and payment allocations. Corrections use credit notes or adjustment lines. PDF/email renders from the finalized snapshot, never from mutable room/service catalogs.

**Rationale**: Historical invoices must not change when prices, names or taxes change.

**Alternatives considered**:

- Recalculate invoice on view: rejected because it breaks auditability and customer evidence.

## Decision 9: Subscription orders snapshot catalog data

**Decision**: Create an expiring `SubscriptionOrder` before payment. Snapshot plan ID/version/name, price, billing period, duration and features from the backend catalog. A verified Platform Billing success applies purchase/renew/upgrade exactly once and writes contract, entitlement and history evidence.

**Rationale**: Catalog changes during payment must not change the agreed purchase. Client-supplied price or duration is untrusted.

**Alternatives considered**:

- Activate directly from selected plan ID: rejected because callbacks can arrive after catalog changes and repeated callbacks can extend repeatedly.

## Decision 10: Unsupported commercial policy fails explicitly

**Decision**: Automatic proration, downgrade credits and subscription-refund entitlement effects remain blocked unless a versioned policy is approved. APIs return a stable policy error and no mutation.

**Rationale**: Inventing financial policy during implementation creates legal and accounting risk.

**Alternatives considered**:

- Silent best-effort proration: rejected because outcomes would be unreviewed and hard to reconcile.

## Decision 11: Reports have an explicit recognition basis

**Decision**: Every report request/response identifies context and basis (`CASH_COLLECTED`, `INVOICED`, or `NET`). Property reports combine successful property ledger events, final invoice lines and property refunds. Platform reports use successful subscription ledger events and platform refunds/credits. Exports consume the same query/result model as APIs.

**Rationale**: Revenue, cash and invoice totals are not interchangeable. A shared query model prevents export drift.

**Alternatives considered**:

- Reuse current mock `AnalyticsService`: rejected because it has no authoritative source.
- Generate exports with separate calculations: rejected because visible/API/file totals can diverge.

## Decision 12: Migration is additive and dual-validated

**Decision**: Add new Flyway migrations after the current highest version. Include preflight queries, idempotent backfill, ownership/context mapping, unique constraints and indexes. Validate both a clean schema from V1 and an upgrade fixture representative of existing data.

**Rationale**: The repository's schema history and current JPA-created state may differ. Both new installs and upgrades must work without deleting ambiguous data.

**Alternatives considered**:

- Let Hibernate update the schema: rejected because it is not reviewable, repeatable or safely recoverable.
- Rewrite old migrations: rejected because applied migration history must remain immutable.

## Decision 13: Full-system completion is evidence-driven

**Decision**: Generate a machine-assisted inventory from routes, menus, controllers, services, entities, repositories, migrations, permissions and tests; then manually verify reachable workflows by role. `COMPLETE_VERIFIED` requires fresh execution evidence, not source inspection alone.

**Rationale**: The supplied scope explicitly includes non-financial dependencies and forbids hiding/removing hard-to-test features.

**Alternatives considered**:

- Audit only payment files: rejected because booking, room state, auth, notifications and reporting determine whether financial journeys actually work.

## Decision 14: Production is a separate approval gate

**Decision**: Implement simulator and sandbox adapters/contracts first. Production mode is disabled by default and cannot run without complete secrets, provider health checks and an approved readiness record. No test uses a real merchant.

**Rationale**: This satisfies safe development and prevents accidental real-money behavior.

**Alternatives considered**:

- Automatically fall back to simulator when production fails: rejected because UI and operators could believe a live payment occurred.

## Decision 15: Upgrades use full price and preserve the remaining term

**Decision**: Apply versioned policy `FULL_PRICE_PRESERVE_REMAINING_TERM_V1`. A verified upgrade activates the target plan immediately, charges the full snapshotted target catalog price without credit or proration, preserves the complete remaining current term, and adds the target plan duration after the later of current expiry or payment time. Target limits cannot reduce any current limit and must support current property usage.

**Rationale**: This owner-approved rule is deterministic, avoids implicit credits and preserves value already purchased while still moving entitlement to the higher plan immediately.

**Alternatives considered**:

- Prorated credit for unused time: rejected because no proration/accounting policy has been approved.
- Replace the remaining term with only the new plan duration: rejected because it would discard paid entitlement.
- Allow a plan with lower limits when current usage happens to fit: rejected because an upgrade must be strictly non-decreasing and improve at least one limit.

## Decision 16: Canonical finalized invoice for every print/export path

**Decision**: Customer and staff invoice views, PDF downloads, email attachments and browser print previews all consume the same finalized `Invoice`/`InvoiceLine` snapshot. Legacy invoice-generation endpoints remain read-only compatibility adapters or are deprecated; they must not create a second invoice or calculate totals from mutable reservation totals.

**Rationale**: The audit found a legacy admin print path that omits consumed services and a PDF renderer that prints only aggregate totals. One immutable source prevents drift between customer and staff documents and preserves historical names/prices.

**Alternatives considered**:

- Keep separate legacy and property invoice renderers: rejected because service lines and tenant/immutability rules would continue to diverge.
- Recalculate from the current service catalog at print time: rejected because catalog prices/names can change after checkout.

## Decision 17: Property selection is explicit for multi-property service catalogs

**Decision**: Resolve the service catalog from the reservation's server-authorized property. If a caller can access multiple properties and no reservation context identifies one, require an explicit selected property and reject ambiguous requests; never infer a tenant from the first accessible property.

**Rationale**: A service catalog is tenant-owned. Silent inference can show one property's food/minibar catalog while charging another property's reservation or expose cross-property data.

**Alternatives considered**:

- Return all accessible property services and filter in the browser: rejected because client filtering is not a tenant boundary.
- Infer the first accessible property: rejected because ordering is not an authorization decision.

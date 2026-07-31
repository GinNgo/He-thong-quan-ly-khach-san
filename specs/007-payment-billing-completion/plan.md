# Implementation Plan: Payment, Billing, and Full-System Completion

**Feature**: `007-payment-billing-completion`  
**Active worktree branch**: `codex/ui-functional-audit-polish`  
**Date**: 2026-07-31  
**Spec**: [spec.md](./spec.md)

## Summary

Complete the project's financial workflows without mixing hotel revenue with SaaS revenue. The implementation introduces two explicit bounded contexts: **Property Commerce** for guest-to-property booking money and **Platform Billing** for owner-to-platform subscription money. Both contexts use server-owned prices, strict lifecycle transitions, idempotent provider callbacks, immutable financial evidence, tenant-safe authorization, sandbox-first provider adapters, and independent reconciliation reports.

The feature also requires a full-system inventory and evidence-based verification pass. Every reachable feature must be classified, traced from UI to database and permissions, and either verified complete or represented by an implementation task. Production payment remains disabled until a separate readiness approval.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.2.5; TypeScript 6.0, Angular 22 standalone components  
**Primary Dependencies**: Spring MVC, Spring Security, Spring Data JPA/Hibernate, Flyway, Jakarta Validation, SQL Server JDBC, Angular Router/Forms, RxJS, PrimeNG, ngx-translate, Chart.js, jsPDF, Playwright  
**Storage**: SQL Server 2022 in production-like environments; H2 only for isolated tests where SQL Server behavior is not under test  
**Testing**: JUnit 5, Spring Boot Test, Spring Security Test, repository/integration/concurrency tests, Vitest, Angular TestBed, Playwright E2E, clean Flyway migration validation  
**Target Platform**: Web application; Spring Boot API and Angular SPA, containerized SQL Server for integration validation  
**Project Type**: Full-stack multi-tenant SaaS and public hotel-booking web application  
**Performance Goals**: Payment callbacks and idempotent replays complete within 2 seconds at p95 in local integration fixtures; filtered revenue reports return within 3 seconds for 100,000 financial rows; UI financial actions acknowledge within 300 ms excluding provider latency  
**Constraints**: VND only; shared database/schema; tenant ownership derived from authenticated access; no real money or production merchant in automated/manual development tests; all schema changes are additive Flyway migrations; finalized financial evidence is immutable  
**Scale/Scope**: Two financial bounded contexts, seven feature stories, five mandatory E2E journeys, eight supported role families, at least 60 inventory capability groups, and all reachable routes/APIs found by inventory

## Constitution Check

*GATE: passed before research and re-checked after design.*

- [x] **Core stack**: Uses Java 21, Spring Boot 3, Angular 22 standalone components, SQL Server, JPA/Hibernate, Maven and npm.
- [x] **Multi-tenancy**: All Property Commerce entities carry `hotel_id`; authenticated property access and active Hibernate tenant filtering are foundational work, not optional repository conventions.
- [x] **RBAC**: Mutation and financial-data authorization is enforced by backend permissions; frontend guards only shape presentation.
- [x] **Inventory integrity**: Existing reservation locks are preserved and extended to payment, refund, checkout and subscription concurrency boundaries.
- [x] **Subscription rules**: Feature activation comes only from authoritative Platform Billing success; expired subscriptions retain policy-approved historical read/export access.
- [x] **Secrets and readiness**: Production credentials stay outside Git; missing production configuration fails closed and cannot fall back to sandbox/simulator.
- [x] **Validation and error handling**: Amount, ownership, state, idempotency, provider identity and transition errors have explicit validation and error-contract tasks.
- [x] **Real-system verification**: Simulator use is limited to the external provider boundary; application API, authorization, persistence, migrations, reports and UI journeys use real application code and deterministic databases.
- [x] **Evidence and regression**: Baseline, clean migration, unit, integration, concurrency, browser, security, export and reconciliation evidence are mandatory final-worktree outputs.

No constitution exception is required.

## Architecture Decisions

### 1. Bounded-context separation

Property Commerce and Platform Billing have separate configuration, entities, repositories, application services, API namespaces, permissions and reports. Shared code is restricted to value objects, provider SPI primitives, cryptographic verification helpers and common audit infrastructure. A property payment can never activate a subscription, and a platform payment can never settle a reservation.

### 2. Authoritative financial model

Mutable attempts represent provider/manual processing. Successful charges, refunds, credits and adjustments produce immutable ledger records. Booking and subscription summaries are derived from those records plus finalized snapshots; the client never supplies an authoritative price, duration, amount paid or entitlement effect.

### 3. Transaction and concurrency boundaries

- Reservation availability and booking creation retain pessimistic/constraint protection.
- Callback, manual confirmation, refund and subscription activation use unique idempotency identities plus row locking.
- Checkout locks the reservation aggregate, recomputes the balance, finalizes the invoice and updates room/housekeeping state in one transaction.
- Reports query successful immutable financial events and finalized invoice lines, not UI totals or hard-coded analytics.

### 4. Environment safety

Each context has an explicit `SIMULATOR`, `SANDBOX` or `PRODUCTION` mode. Production adapters require an enable flag, complete secrets, expected merchant identity and readiness approval marker. No automatic environment fallback is allowed. Simulator callbacks use the same verification/idempotency pipeline as real adapters.

### 5. Migration safety

Flyway migrations are additive and forward-only. They create new context-owned tables, constraints and indexes; preflight duplicate/orphan checks precede uniqueness constraints; existing records are backfilled idempotently; destructive cleanup and production execution require separate approval and documented recovery.

## Project Structure

### Documentation for this feature

```text
specs/007-payment-billing-completion/
|-- spec.md
|-- plan.md
|-- research.md
|-- data-model.md
|-- quickstart.md
|-- contracts/
|   `-- financial-api-contract.md
|-- checklists/
|   `-- requirements.md
`-- tasks.md

docs/audit/financial/
|-- PAYMENT_CAPABILITY_MATRIX.md
|-- PROPERTY_PAYMENT_AUDIT.md
|-- PLATFORM_BILLING_AUDIT.md
|-- PROPERTY_REVENUE_RECONCILIATION.md
`-- PLATFORM_REVENUE_RECONCILIATION.md

docs/audit/system/
|-- MASTER_FUNCTION_INVENTORY.md
|-- FULL_SYSTEM_TRACEABILITY_MATRIX.md
|-- FULL_SYSTEM_AUDIT_REPORT.md
|-- FULL_SYSTEM_TEST_MATRIX.md
|-- FULL_SYSTEM_ERROR_EXPECTATION_CATALOG.md
`-- LEGACY_TASK_RECONCILIATION_2026-07-31.md

docs/testing/
|-- FULL_SYSTEM_MANUAL_TEST_GUIDE.md
|-- SANDBOX_CONFIGURATION_GUIDE.md
|-- PRODUCTION_READINESS_CHECKLIST.md
`-- FINAL_WORKTREE_TEST_REPORT.md
```

### Source code

```text
backend/src/main/java/com/hotel/
|-- propertycommerce/        # property payment, charge, invoice, refund, reports
|-- platformbilling/         # subscription order, payment, contract, reports
|-- paymentprovider/         # shared provider SPI and verification primitives
|-- security/                # property access, tenant filter, permissions
|-- controllers/             # retained endpoints migrated/delegated incrementally
|-- services/                # retained reservation/checkout integration services
`-- entities/                # legacy entities kept during safe migration

backend/src/main/resources/db/migration/
backend/src/test/java/com/hotel/

frontend/src/app/
|-- core/services/           # typed API clients and auth/error handling
|-- features/client/         # booking payment, invoice and refund visibility
|-- features/management/     # property payment config, checkout and revenue
|-- features/admin/          # platform billing config and SaaS reports
`-- shared/                  # reusable financial status and money presentation

frontend/e2e/
docs/audit/
docs/testing/
```

**Structure Decision**: Keep the existing backend/frontend applications and introduce context-owned packages and feature folders inside them. This limits migration risk while making ownership boundaries enforceable in code, API contracts and database constraints.

## Delivery Phases

### Phase 0 - Baseline and inventory

Record the dirty-worktree baseline, builds, current tests and failures before fixes. Generate route/API/database/permission inventories and the initial capability classification. No existing change is reset, cleaned or broadly staged.

### Phase 1 - Security and data foundation

Activate tenant filters, add context ownership, define state machines, permissions, audit events, idempotency keys and additive migrations. Add simulator/sandbox safety gates before exposing new financial mutations.

### Phase 2 - Property Commerce

Implement property payment configuration, deposit/manual transfer/online attempts, immutable charges, service/surcharge snapshots, atomic checkout, invoice/credit note and property refund flows.

### Phase 3 - Platform Billing

Implement backend-owned subscription orders, platform payment sessions/callbacks, contract/history transitions, renewal/upgrade policy gates and platform refunds.

### Phase 4 - Reporting and exports

Replace mock analytics with reconciled context-specific report APIs and matching Excel/PDF exports. Add database and file-level reconciliation assertions.

### Phase 5 - Full-system completion and release evidence

Resolve every inventory item classified PARTIAL, PLACEHOLDER, BROKEN or MISSING, then rebuild from clean migrations and execute all role journeys on the final worktree. Production payment stays disabled unless a separately approved readiness review is performed.

## Stop Gates

- Stop before any destructive migration, cleanup of existing financial records or production database execution.
- Stop before adding or using production credentials, real merchant accounts or real-money transactions.
- Stop before enabling `PRODUCTION` payment mode or changing refund/proration business policy without explicit owner approval.
- Stop if a required backfill cannot map legacy records to one context and owner unambiguously; report affected records and propose a recovery decision.
- Stop if implementation would require reverting unrelated dirty-worktree changes.

## Post-Design Constitution Re-check

The data model assigns `hotel_id` to all property financial aggregates, isolates platform records, keeps backend authority over financial values, uses explicit permissions and append-only evidence, and provides clean-migration plus final-worktree validation. The design remains constitution-compliant.

## Complexity Tracking

| Added complexity | Why required | Simpler alternative rejected because |
|---|---|---|
| Two financial packages and ledgers | Money recipients, merchant configuration, permissions and revenue ownership differ | A shared undifferentiated payment model can mix tenant and platform revenue or activate the wrong domain |
| Append-only ledger plus mutable attempts | Provider processing and accounting evidence have different lifecycle needs | Mutating one payment row destroys history and makes refunds/reconciliation unreliable |
| Full-system inventory alongside financial work | Requirements demand evidence that supporting modules and role journeys are connected | Financial code can appear correct while auth, room state, notification, reports or UI remain broken |

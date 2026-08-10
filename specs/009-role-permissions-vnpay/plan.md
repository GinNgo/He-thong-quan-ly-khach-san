# Implementation Plan: Role Permissions and VNPay

**Branch**: `009-role-permissions-vnpay` | **Date**: 2026-08-09 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/009-role-permissions-vnpay/spec.md`

## Summary

Complete the existing dynamic RBAC model for Customer, Admin, Manager, Accountant and Receptionist by making the function catalog authoritative, enforcing `VIEW` dependencies, introducing a distinct task-execution bit, refreshing effective permissions on every protected request, and combining permission checks with authenticated property scope. Reuse the existing Property Commerce and Platform Billing payment contexts and shared VNPay adapter, while keeping booking money, subscription money, merchant configuration, callbacks, reconciliation and authorization strictly separated.

## Technical Context

**Language/Version**: Java 21 with Spring Boot 3.2.5; TypeScript 6.0 with Angular 22 standalone components  
**Primary Dependencies**: Spring MVC, Spring Security, Spring Data JPA/Hibernate, Flyway, Jakarta Validation, SQL Server JDBC, Angular Router/Forms, RxJS, PrimeNG, ngx-translate, Playwright  
**Storage**: SQL Server 2022; shared database/schema with property-owned data scoped by `hotel_id`; H2 limited to isolated tests that do not assert SQL Server behavior  
**Testing**: JUnit 5, Spring Boot Test, Spring Security Test, repository/integration/concurrency tests, Angular TestBed/Vitest, Playwright E2E, clean Flyway migration validation  
**Target Platform**: Spring Boot web API and Angular SPA, deployable with the repository's Docker/SQL Server environment  
**Project Type**: Full-stack, multi-tenant hotel-management SaaS and public booking application  
**Performance Goals**: Permission evaluation adds no more than 50 ms p95 to protected requests; permission updates become effective on the next protected request; callback processing completes within 2 seconds p95 for local integration fixtures; filtered reconciliation returns within 3 seconds for 100,000 attempts  
**Constraints**: VND only; production payment disabled by default; secrets outside Git; frontend guards are presentation only; no caller-controlled `hotel_id`; additive Flyway migrations; immutable financial/audit evidence; no physical delete of referenced operational or financial records  
**Scale/Scope**: Five role families, at least 16 function groups, seven action bits including retained `EXPORT`/`APPROVE`, two payment bounded contexts, two primary VNPay journeys, and role/property negative tests across all protected routes

## Constitution Check

*GATE: passed before research and re-checked after design.*

- [x] **Core stack**: Uses Java 21, Spring Boot 3, Angular 22 standalone components, SQL Server, JPA/Hibernate, Maven and npm.
- [x] **Multi-tenancy**: Property-owned roles, tasks, booking payments and reports carry or derive `hotel_id`; access comes from the authenticated property context and Hibernate tenant filtering.
- [x] **RBAC**: Extends the existing centralized bitmask and `PermissionInterceptor`; Angular guards only hide or disable presentation elements.
- [x] **Inventory integrity**: Reservation/payment/task transitions preserve optimistic or pessimistic concurrency and idempotency protections.
- [x] **Subscription rules**: Only authoritative Platform Billing success activates entitlements; expiry still permits policy-approved historical read/export.
- [x] **Secrets and readiness**: VNPay secrets remain environment-owned and production stays fail-closed without explicit readiness approval.
- [x] **Validation and error handling**: Permission dependencies, stale versions, property scope, amount/reference/signature and state transitions have explicit errors and correlation IDs.
- [x] **Real-system verification**: Tests exercise real services, authorization, persistence and sandbox/simulator provider boundaries; browser-return pages cannot self-confirm success.
- [x] **Evidence and regression**: Clean migration, backend, frontend, security, concurrency and browser evidence are required on the final worktree.

No constitution exception is required.

## Architecture Decisions

### 1. Extend the existing action bitmask

Retain `VIEW=1`, `CREATE=2`, `UPDATE=4`, `DELETE=8`, `EXPORT=16` and `APPROVE=32`, then add `TASK_EXECUTE=64`. This preserves stored masks and existing endpoint semantics. `TASK_EXECUTE` protects operational state transitions; `APPROVE` remains for approval/dual-control decisions.

Every non-zero mutation/execute mask requires `VIEW`. The backend normalizes or rejects invalid combinations before persistence, and the role-permission UI derives supported actions from function-catalog metadata rather than showing every bit for every function.

### 2. Resolve permissions per protected request

JWT continues to identify the user, while the existing authentication filter reloads `CustomUserDetails` and permission masks from current database state for each HTTP request. Keep this behavior and add a current-context endpoint/UI refresh path after permission errors or administrative changes. Local browser storage is never authoritative. Long-lived WebSocket connections must revalidate or disconnect when authorization context changes.

### 3. Combine function permission, task action and property scope

A protected operation succeeds only when the user has the required function/action bit, access to the target property, and any required subscription entitlement. Customer ownership checks remain separate from staff property scope. `TASK_EXECUTE` does not grant `UPDATE` and cannot bypass aggregate transition rules.

### 4. Model operational work with explicit assignment and versioning

Reuse domain-specific aggregates for check-in, checkout, refund and housekeeping transitions. Add a generic task projection only for cross-domain work queues and reassignment. Completion uses optimistic versioning plus a unique effect key so concurrent workers cannot create two effects. Revoking permission blocks the next transition but preserves assignment/history until an authorized reassignment.

### 5. Keep payment contexts separate while sharing provider primitives

Property Commerce owns booking payment attempts, property merchant selection and property reconciliation. Platform Billing owns subscription orders, platform merchant configuration and entitlement application. Both call the shared VNPay adapter for signing/verification primitives, but callbacks resolve the expected context and configuration before mutation; a property callback can never apply a subscription order and vice versa.

### 6. Authoritative callback and return behavior

VNPay browser return is display-only. Signed callbacks/IPN validate signature, merchant, environment, reference, amount, currency, state and replay identity. Row locks/versions and unique provider event/effect keys guarantee exactly-once business effects. Invalid or conflicting evidence creates an auditable reconciliation case without activating a booking payment or subscription.

## Project Structure

### Documentation (this feature)

```text
specs/009-role-permissions-vnpay/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── role-payment-api.md
├── checklists/
│   └── requirements.md
└── tasks.md                 # generated later by speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/com/hotel/
├── security/                # action/function codes, interceptor, effective permissions
├── services/                # role permission, property access and operational audit
├── entities/                # role/function/permission/audit and existing operational tasks
├── propertycommerce/        # reservation payment attempts/callback/reconciliation
├── platformbilling/         # subscription order/payment/entitlement/reconciliation
├── paymentprovider/         # shared VNPay provider adapter and verification primitives
└── controllers/             # role/function/menu and retained compatibility endpoints

backend/src/main/resources/db/migration/
backend/src/test/java/com/hotel/

frontend/src/app/
├── core/services/           # auth, effective permission and typed API clients
├── core/guards/             # permission/role/feature route guards
├── features/system/         # function catalog, role and permission matrix
├── features/management/     # task queues, booking finance, subscription billing
├── features/client/         # booking checkout and payment result
└── layout/                  # permission-filtered admin/management navigation

frontend/e2e/
```

**Structure Decision**: Keep the existing backend and frontend applications. Extend the current security, Property Commerce, Platform Billing and provider packages instead of creating parallel authorization or payment stacks.

## Delivery Phases

### Phase 0 - Baseline and permission inventory

Capture the current worktree/build baseline. Inventory function codes, routes, endpoint annotations, menu links, action bits, default role masks and property-scope checks. Classify mismatches such as wrong CRUD annotations, hard-coded role bypasses and frontend-only enforcement.

### Phase 1 - RBAC foundation

Add supported-action metadata and `TASK_EXECUTE`; enforce `VIEW` dependency; version effective permissions; invalidate permission caches after role/membership/property changes; seed default matrices for the five role families; preserve immutable core roles according to approved policy.

### Phase 2 - Route, UI and task enforcement

Map every protected API and navigation item to a function/action. Replace hard-coded role decisions where dynamic permission is required. Add task execution/reassignment contracts, concurrency protection, denial UX and audit events. Preserve domain-specific transitions and soft-delete rules.

### Phase 3 - Booking VNPay completion

Migrate the booking UI from the legacy reservation-bound payment-session path to the canonical Property Commerce payment-attempt flow. Add a VNPay-specific GET query ingress for IPN/return that delegates to the shared verification and context callback services, signed checkout URL generation, browser-return polling, replay/concurrency protection, expiry/cancellation and property reconciliation. Retain a time-bounded compatibility adapter for legacy callers.

### Phase 4 - Subscription VNPay completion

Expose platform subscription order/payment-attempt APIs for VNPay, snapshot plan terms, require package permissions, generate a signed platform checkout URL with a stable retry key/reference, add return polling and provider-specific IPN ingress, validate platform merchant callbacks and apply purchase/renewal entitlement exactly once. Add VNPay status-query recovery for lost/late IPN without creating a second debit.

### Phase 5 - Reconciliation and E2E evidence

Add separate booking and platform reconciliation views/exports, complete negative permission/property tests, run clean migrations and execute all five role journeys plus both VNPay journeys on the final worktree.

## Stop Gates

- Stop before destructive schema/data cleanup or production database execution.
- Stop before adding real credentials, enabling production mode or initiating real-money VNPay transactions.
- Stop if legacy permission masks cannot be migrated without changing existing granted behavior; report the affected masks and migration decision.
- Stop if a callback cannot be mapped unambiguously to exactly one payment context/configuration.
- Stop if implementation would require reverting unrelated dirty-worktree changes.

## Post-Design Constitution Re-check

The design preserves the mandated stack, derives tenant scope from authenticated access, extends centralized backend RBAC, keeps frontend guards non-authoritative, preserves booking concurrency, separates property/platform money, stores secrets outside Git and requires full real-system validation. All gates remain passed.

## Complexity Tracking

| Added complexity | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Versioned effective-permission lookup | Revocations must apply on the next request even when a JWT/browser cache is stale | Trusting JWT/local storage would leave revoked mutation rights active until refresh or login |
| Separate `TASK_EXECUTE` and `APPROVE` bits | Performing work and approving exceptional/financial decisions are distinct duties | Reusing `UPDATE` or `APPROVE` would overgrant access and weaken separation of duties |
| Two context-specific VNPay callback pipelines | Booking revenue and platform subscription revenue have different owners/configuration/effects | One generic mutation handler could mix merchants, ledgers or entitlement effects |

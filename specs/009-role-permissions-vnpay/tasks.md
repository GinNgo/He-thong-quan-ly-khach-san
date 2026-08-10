# Tasks: Role Permissions and VNPay

**Input**: Design documents from `specs/009-role-permissions-vnpay/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/role-payment-api.md`, `quickstart.md`

**Tests**: Required because the specification defines measurable permission, tenant-isolation, idempotency, concurrency, reconciliation and full E2E outcomes.

**Organization**: Tasks are grouped by user story so each story can be implemented and verified as an independent increment.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel because it targets independent files after its phase prerequisites are complete.
- **[Story]**: Maps the task to a user story from `spec.md`.
- Every task includes an exact repository-relative file path.

## Phase 1: Setup and Baseline

**Purpose**: Preserve the current worktree and establish an evidence-backed implementation baseline.

- [X] T001 Record branch, dirty-worktree status, build versions and known failures in `docs/testing/evidence/009/baseline/BASELINE.md`
- [ ] T002 Run the existing backend test/build baseline and record commands, pass/fail/skipped counts and blockers in `docs/testing/evidence/009/baseline/BACKEND_BASELINE.md`
- [ ] T003 [P] Run the existing frontend test/build and Playwright discovery baseline and record results in `docs/testing/evidence/009/baseline/FRONTEND_BASELINE.md`
- [X] T004 [P] Inventory current action bits, function codes, endpoint annotations, route guards and navigation links in `docs/audit/security/FEATURE_009_PERMISSION_INVENTORY.md`
- [X] T005 [P] Inventory legacy and canonical booking/platform VNPay paths, configurations, callbacks and reconciliation consumers in `docs/audit/financial/FEATURE_009_VNPAY_INVENTORY.md`
- [X] T006 Reconcile inventory gaps with `specs/009-role-permissions-vnpay/spec.md` and record in-scope/out-of-scope decisions in `docs/audit/FEATURE_009_TRACEABILITY.md`

---

## Phase 2: Foundational Prerequisites

**Purpose**: Establish shared action semantics, schema support, error contracts and test fixtures that block every user story.

**CRITICAL**: No user-story implementation begins until this phase passes clean migration and foundational tests.

- [X] T007 Add `TASK_EXECUTE = 64` without changing existing action values in `backend/src/main/java/com/hotel/security/ActionCode.java`
- [X] T008 [P] Mirror `TASK_EXECUTE = 64` and add `canExecuteTask()` in `frontend/src/app/core/services/permission.service.ts`
- [X] T009 Extend function catalog persistence with supported-action mask, scope type, active flag and version in `backend/src/main/java/com/hotel/entities/AppFunction.java`
- [X] T010 Create an additive Flyway migration for function metadata, reviewed default role masks and supporting constraints in `backend/src/main/resources/db/migration/V60__feature_009_permission_foundation.sql`
- [X] T011 Add preflight checks for unsupported legacy masks, duplicate role/function rows and missing function codes in `backend/src/main/resources/db/preflight/feature_009_permission_preflight.sql`
- [X] T012 [P] Extend function and permission DTO contracts with supported actions, scope and optimistic versions in `backend/src/main/java/com/hotel/dtos/AppFunctionDto.java` and `backend/src/main/java/com/hotel/dtos/UpdateRolePermissionsRequest.java`
- [ ] T013 Add stable permission, task, idempotency and reconciliation error codes to the correlation-aware error mapping in `backend/src/main/java/com/hotel/exceptions/GlobalExceptionHandler.java`
- [ ] T014 [P] Create deterministic Customer, Admin, Manager, Accountant and Receptionist fixtures across two properties in `backend/src/test/resources/fixtures/feature-009-role-property-fixture.sql`
- [ ] T015 [P] Create reusable VNPay signed callback, replay and mismatch fixtures in `backend/src/test/java/com/hotel/testsupport/VnpayFixtureFactory.java`
- [ ] T016 Add clean and upgrade migration tests for Feature 009 schema/backfill behavior in `backend/src/test/java/com/hotel/integration/Feature009MigrationIntegrationTest.java`
- [X] T017 Document the reviewed function-to-supported-action/default-role mapping in `docs/audit/security/FEATURE_009_DEFAULT_PERMISSION_MATRIX.md`

**Checkpoint**: Existing masks remain compatible, migrations pass on clean and upgrade fixtures, and shared constants/contracts are ready.

---

## Phase 3: User Story 1 - Admin Configures Dynamic Permissions (Priority: P1) MVP

**Goal**: Admin manages valid function/action matrices; revoked rights disappear from UI and are denied by the backend on the next request within the authorized property scope.

**Independent Test**: Grant then revoke Receptionist reservation permissions in an active session and verify menu, route, controls and direct API behavior change consistently without unauthorized mutation.

### Tests for User Story 1

- [X] T018 [P] [US1] Add service tests for supported-action validation, `VIEW` dependency, duplicate functions and stale role versions in `backend/src/test/java/com/hotel/services/RolePermissionServiceTest.java`
- [ ] T019 [P] [US1] Add request-fresh revocation and direct-API denial integration tests in `backend/src/test/java/com/hotel/security/DynamicPermissionRevocationIntegrationTest.java`
- [ ] T020 [P] [US1] Add cross-property permission-plus-scope IDOR tests for all five role fixtures in `backend/src/test/java/com/hotel/security/Feature009PropertyPermissionIntegrationTest.java`
- [X] T021 [P] [US1] Add frontend unit tests for dependent bit toggling and supported-action rendering in `frontend/src/app/features/admin/role-permission/role-permission.component.spec.ts`
- [ ] T022 [P] [US1] Add route/menu/control revocation tests for admin and management layouts in `frontend/src/app/core/guards/permission.guard.spec.ts` and `frontend/src/app/layout/management-layout/management-layout.spec.ts`

### Implementation for User Story 1

- [X] T023 [US1] Enforce the global supported mask, per-function supported actions and `VIEW` dependency in `backend/src/main/java/com/hotel/services/RolePermissionService.java`
- [ ] T024 [US1] Return supported actions, role version and normalized permission tree from `backend/src/main/java/com/hotel/controllers/RolePermissionController.java`
- [ ] T025 [US1] Add a current effective-permission/property context endpoint backed by request-loaded user details in `backend/src/main/java/com/hotel/controllers/AuthController.java`
- [ ] T026 [US1] Extend role permission audit with reason, correlation ID and function-level differences in `backend/src/main/java/com/hotel/entities/RolePermissionAudit.java` and `backend/src/main/java/com/hotel/services/RolePermissionService.java`
- [X] T027 [P] [US1] Add supported-action checkboxes, automatic `VIEW` dependency behavior, version conflict handling and reason input in `frontend/src/app/features/admin/role-permission/role-permission.component.ts`
- [X] T028 [P] [US1] Update the permission matrix presentation and accessible disabled-state explanations in `frontend/src/app/features/admin/role-permission/role-permission.component.html`
- [X] T029 [US1] Refresh effective permissions after administrative changes and permission-related 403 responses without redirect loops in `frontend/src/app/core/services/auth.ts` and `frontend/src/app/core/interceptors/error-interceptor.ts`
- [ ] T030 [US1] Apply `permissionGuard` and explicit function/action route data to all in-scope admin and management routes in `frontend/src/app/app.routes.ts`
- [ ] T031 [US1] Filter admin quick links and navigation with the same function/action registry used by route guards in `frontend/src/app/layout/admin-layout/admin-layout.ts`
- [ ] T032 [US1] Correct mismatched CRUD permission annotations found by the inventory in `backend/src/main/java/com/hotel/controllers/AppFunctionController.java`, `backend/src/main/java/com/hotel/controllers/RoleController.java` and affected controllers listed in `docs/audit/security/FEATURE_009_PERMISSION_INVENTORY.md`
- [ ] T033 [US1] Add audited role-membership and property-assignment mutation coverage in `backend/src/main/java/com/hotel/services/RoleService.java` and `backend/src/main/java/com/hotel/services/PropertyAccessService.java`
- [ ] T034 [US1] Run the US1 backend/frontend permission suite and record next-request revocation evidence in `docs/testing/evidence/009/us1/PERMISSION_REVOCATION_REPORT.md`

**Checkpoint**: User Story 1 is independently usable as the MVP and all unauthorized direct requests are denied without mutation.

---

## Phase 4: User Story 2 - Staff Executes Authorized Operational Tasks (Priority: P1)

**Goal**: Manager, Accountant and Receptionist see property-scoped work queues and can claim, execute or reassign work only with the correct action and domain transition authority.

**Independent Test**: Assign one task to users with view-only, execute and revoked permissions; verify correct visibility, denial, reassignment, audit history and exactly-once completion.

### Tests for User Story 2

- [X] T035 [P] [US2] Add entity/state-transition tests for operational task assignment, blocking, reassignment and terminal states in `backend/src/test/java/com/hotel/operations/OperationalTaskModelTest.java`
- [ ] T036 [P] [US2] Add permission revocation, property isolation and assignee/manager authorization tests in `backend/src/test/java/com/hotel/operations/OperationalTaskPermissionIntegrationTest.java`
- [ ] T037 [P] [US2] Add concurrent claim/completion and idempotency integration tests in `backend/src/test/java/com/hotel/operations/OperationalTaskConcurrencyIntegrationTest.java`
- [ ] T038 [P] [US2] Add task queue/control rendering tests for view-only and execute users in `frontend/src/app/features/management/operational-tasks/operational-tasks.component.spec.ts`

### Implementation for User Story 2

- [X] T039 [P] [US2] Create tenant-filtered operational task and append-only history entities in `backend/src/main/java/com/hotel/operations/OperationalTask.java` and `backend/src/main/java/com/hotel/operations/OperationalTaskHistory.java`
- [X] T040 [P] [US2] Create task repositories with tenant-filtered queries, pessimistic claim locking and effect-key lookup in `backend/src/main/java/com/hotel/operations/OperationalTaskRepository.java` and `backend/src/main/java/com/hotel/operations/OperationalTaskHistoryRepository.java`
- [X] T041 [US2] Implement task visibility, claim, execute, block and authorized reassignment orchestration in `backend/src/main/java/com/hotel/operations/OperationalTaskService.java`
- [ ] T042 [US2] Delegate task execution to existing check-in, checkout, payment confirmation, refund and housekeeping domain services through `backend/src/main/java/com/hotel/operations/OperationalTaskHandlerRegistry.java`
- [X] T043 [US2] Expose property-scoped task list, claim, execute and reassign endpoints in `backend/src/main/java/com/hotel/operations/OperationalTaskController.java`
- [ ] T044 [US2] Replace state-transition annotations that misuse `UPDATE`/`APPROVE` with reviewed `TASK_EXECUTE`/`APPROVE` semantics in `backend/src/main/java/com/hotel/housekeeping/HousekeepingController.java` and reservation/payment lifecycle controllers listed in `docs/audit/security/FEATURE_009_PERMISSION_INVENTORY.md`
- [X] T045 [P] [US2] Create typed task API models and client methods in `frontend/src/app/core/services/operational-task.service.ts`
- [X] T046 [P] [US2] Build the responsive task queue, filters, claim/execute/reassign controls and denial states in `frontend/src/app/features/management/operational-tasks/operational-tasks.component.ts` and `frontend/src/app/features/management/operational-tasks/operational-tasks.component.html`
- [X] T047 [US2] Add the permission-filtered task route and management navigation entry in `frontend/src/app/app.routes.ts` and `frontend/src/app/layout/management-layout/management-layout.ts`
- [ ] T048 [US2] Add operational task events to the existing audit query surface in `backend/src/main/java/com/hotel/services/OperationalAuditService.java`
- [ ] T049 [US2] Run the US2 permission/concurrency suite and record exactly-once task evidence in `docs/testing/evidence/009/us2/TASK_EXECUTION_REPORT.md`

**Checkpoint**: User Story 2 works independently over existing domain services and does not create a second workflow source of truth.

---

## Phase 5: User Story 3 - Customer Pays a Booking through VNPay (Priority: P1)

**Goal**: Customer creates one canonical Property Commerce VNPay attempt, follows a signed checkout URL and receives a display-only result after authoritative IPN/callback processing.

**Independent Test**: Complete one sandbox booking payment, replay/concurrently deliver its IPN and verify exactly one property financial effect; mismatched evidence activates nothing.

### Tests for User Story 3

- [ ] T050 [P] [US3] Add signed checkout URL and stable reference tests for property VNPay attempts in `backend/src/test/java/com/hotel/propertycommerce/payment/PropertyVnpayCheckoutServiceTest.java`
- [ ] T051 [P] [US3] Add GET IPN signature/merchant/amount/reference/environment negative tests in `backend/src/test/java/com/hotel/propertycommerce/payment/PropertyVnpayIngressControllerTest.java`
- [ ] T052 [P] [US3] Extend replay and concurrent callback coverage for exactly-once booking effects in `backend/src/test/java/com/hotel/propertycommerce/payment/PropertyPaymentCallbackConcurrencyIntegrationTest.java`
- [ ] T053 [P] [US3] Add legacy-to-canonical compatibility and no-double-ledger tests in `backend/src/test/java/com/hotel/propertycommerce/payment/LegacyVnpayCompatibilityIntegrationTest.java`
- [ ] T054 [P] [US3] Add checkout redirect, retry, polling and display-only return tests in `frontend/src/app/features/client/booking-checkout/booking-checkout.component.spec.ts` and `frontend/src/app/features/client/payment-result/payment-result.spec.ts`

### Implementation for User Story 3

- [X] T055 [US3] Add context-neutral VNPay checkout URL generation from server-owned request snapshots in `backend/src/main/java/com/hotel/paymentprovider/vnpay/VnpayCheckoutUrlService.java`
- [X] T056 [US3] Return signed redirect URL, stable transaction reference and expiry from property payment-attempt creation in `backend/src/main/java/com/hotel/propertycommerce/payment/PropertyPaymentAttemptService.java`
- [X] T057 [US3] Add official VNPay GET IPN and browser-return ingress that delegates to canonical verification/orchestration in `backend/src/main/java/com/hotel/propertycommerce/payment/PropertyVnpayIngressController.java`
- [ ] T058 [US3] Create auditable reconciliation cases for unknown, mismatched, late and conflicting property callbacks in `backend/src/main/java/com/hotel/paymentprovider/reconciliation/ReconciliationCaseService.java`
- [ ] T059 [US3] Route booking VNPay creation through `/api/reservations/{reservationId}/payment-attempts` and preserve a stable retry key in `frontend/src/app/features/client/booking-checkout/booking-checkout.component.ts`
- [ ] T060 [US3] Make the payment-result page poll/read canonical attempt status and remove any client-side success authority in `frontend/src/app/features/client/payment-result/payment-result.ts`
- [ ] T061 [US3] Delegate legacy `/api/payments/sessions` VNPay callers to the canonical Property Commerce adapter with deprecation logging in `backend/src/main/java/com/hotel/controllers/PaymentController.java` and `backend/src/main/java/com/hotel/propertycommerce/payment/LegacyPropertyPaymentAdapter.java`
- [ ] T062 [US3] Add property VNPay configuration readiness, return/IPN URL validation and masked status exposure in `backend/src/main/java/com/hotel/propertycommerce/config/PropertyPaymentConfigurationService.java`
- [ ] T063 [US3] Run the US3 sandbox/simulator, replay, mismatch and tenant suites and record evidence in `docs/testing/evidence/009/us3/BOOKING_VNPAY_REPORT.md`

**Checkpoint**: Booking VNPay uses one canonical attempt/ledger path and the browser cannot self-confirm payment.

---

## Phase 6: User Story 4 - Manager Purchases a Subscription through VNPay (Priority: P1)

**Goal**: Authorized property representatives create a snapshotted subscription order, pay through the platform VNPay merchant and activate or renew entitlement exactly once.

**Independent Test**: Pay one sandbox subscription order, replay/concurrently deliver its IPN and verify one platform effect and one entitlement transition; failed or mismatched payments activate nothing.

### Tests for User Story 4

- [ ] T064 [P] [US4] Add package `VIEW | TASK_EXECUTE`, property scope and server-owned snapshot contract tests in `backend/src/test/java/com/hotel/platformbilling/SubscriptionOrderPermissionIntegrationTest.java`
- [ ] T065 [P] [US4] Add platform VNPay checkout URL, stable retry key and bind-once reference tests in `backend/src/test/java/com/hotel/platformbilling/payment/PlatformVnpayCheckoutServiceTest.java`
- [ ] T066 [P] [US4] Add platform GET IPN mismatch, replay and concurrent entitlement application tests in `backend/src/test/java/com/hotel/platformbilling/payment/PlatformVnpayCallbackIntegrationTest.java`
- [ ] T067 [P] [US4] Add lost/late IPN transaction-query recovery tests in `backend/src/test/java/com/hotel/platformbilling/payment/PlatformVnpayRecoveryServiceTest.java`
- [ ] T068 [P] [US4] Add enabled VNPay option, stable retry and result polling tests in `frontend/src/app/features/management/subscription-billing/platform-payment-panel.component.spec.ts`

### Implementation for User Story 4

- [ ] T069 [US4] Require package function permission and property access when creating subscription orders in `backend/src/main/java/com/hotel/platformbilling/order/SubscriptionOrderService.java`
- [ ] T070 [US4] Generate platform VNPay redirect URLs and persist stable order/provider/method idempotency identities in `backend/src/main/java/com/hotel/platformbilling/payment/PlatformPaymentAttemptService.java`
- [ ] T071 [US4] Add official platform VNPay GET IPN and display-only return ingress in `backend/src/main/java/com/hotel/platformbilling/payment/PlatformVnpayIngressController.java`
- [ ] T072 [US4] Enforce platform merchant/environment/order snapshot bindings and exactly-once entitlement effects in `backend/src/main/java/com/hotel/platformbilling/payment/PlatformPaymentCallbackService.java`
- [ ] T073 [US4] Implement read-only VNPay transaction query recovery for unresolved platform attempts in `backend/src/main/java/com/hotel/platformbilling/payment/PlatformVnpayRecoveryService.java`
- [ ] T074 [US4] Register VNPay recovery scheduling with safe retry/backoff and no new debit creation in `backend/src/main/java/com/hotel/services/PaymentProviderRecoveryService.java`
- [ ] T075 [P] [US4] Enable the VNPay package option, persist retry identity across reload and redirect to checkout in `frontend/src/app/features/management/subscription-billing/platform-payment-panel.component.ts`
- [ ] T076 [P] [US4] Add platform payment result polling, success/failure/expiry states and entitlement refresh in `frontend/src/app/features/management/subscription-billing/subscription-payment-result.component.ts`
- [ ] T077 [US4] Add the subscription payment result route with package permission guards in `frontend/src/app/app.routes.ts`
- [ ] T078 [US4] Validate platform VNPay configuration/secret references and production fail-closed readiness in `backend/src/main/java/com/hotel/platformbilling/config/PlatformPaymentConfigurationService.java`
- [ ] T079 [US4] Run the US4 sandbox/simulator, recovery, replay and entitlement suites and record evidence in `docs/testing/evidence/009/us4/SUBSCRIPTION_VNPAY_REPORT.md`

**Checkpoint**: Subscription VNPay is independently operational and cannot use property merchant configuration or duplicate entitlement duration.

---

## Phase 7: User Story 5 - Accountant Reconciles Property Revenue (Priority: P2)

**Goal**: Accountant views and exports property-scoped transactions, invoices, refunds and reconciliation cases without receiving operational mutation or platform billing access by default.

**Independent Test**: Reconcile a fixed property dataset to one VND, export matching rows/totals and verify operational/platform requests are denied.

### Tests for User Story 5

- [ ] T080 [P] [US5] Add property/platform report-separation and exact-total tests in `backend/src/test/java/com/hotel/paymentprovider/reporting/Feature009ReconciliationIntegrationTest.java`
- [ ] T081 [P] [US5] Add Accountant default permission, property IDOR, refund denial and platform-scope denial tests in `backend/src/test/java/com/hotel/security/AccountantPermissionIntegrationTest.java`
- [ ] T082 [P] [US5] Add reconciliation case resolution permission/version/immutability tests in `backend/src/test/java/com/hotel/paymentprovider/reconciliation/ReconciliationCaseServiceTest.java`
- [ ] T083 [P] [US5] Add accountant filter, export and forbidden-control UI tests in `frontend/src/app/features/management/payment-reconciliation/payment-reconciliation.component.spec.ts`

### Implementation for User Story 5

- [ ] T084 [P] [US5] Create tenant-filtered reconciliation case entity/repository and additive migration in `backend/src/main/java/com/hotel/paymentprovider/reconciliation/ReconciliationCase.java`, `backend/src/main/java/com/hotel/paymentprovider/reconciliation/ReconciliationCaseRepository.java` and `backend/src/main/resources/db/migration/V62__feature_009_reconciliation_cases.sql`
- [ ] T085 [US5] Implement property and platform separated queries, exact totals and immutable case resolution in `backend/src/main/java/com/hotel/paymentprovider/reporting/FinancialReconciliationService.java` and `backend/src/main/java/com/hotel/paymentprovider/reconciliation/ReconciliationCaseService.java`
- [ ] T086 [US5] Expose property-scoped accountant reconciliation, export and resolution endpoints in `backend/src/main/java/com/hotel/paymentprovider/reconciliation/ReconciliationController.java`
- [ ] T087 [US5] Expose platform-only reconciliation endpoints with explicit platform permission scope in `backend/src/main/java/com/hotel/platformbilling/reporting/PlatformReconciliationController.java`
- [ ] T088 [P] [US5] Create typed reconciliation/filter/export API client models in `frontend/src/app/core/services/payment-reconciliation.service.ts`
- [ ] T089 [P] [US5] Build the accountant reconciliation page with filters, totals, cases, export and permission-aware actions in `frontend/src/app/features/management/payment-reconciliation/payment-reconciliation.component.ts` and `frontend/src/app/features/management/payment-reconciliation/payment-reconciliation.component.html`
- [ ] T090 [US5] Add the Accountant navigation/route and keep platform billing and operational mutation controls hidden by default in `frontend/src/app/layout/management-layout/management-layout.ts` and `frontend/src/app/app.routes.ts`
- [ ] T091 [US5] Run exact VND reconciliation/export and denial suites and record evidence in `docs/testing/evidence/009/us5/ACCOUNTANT_RECONCILIATION_REPORT.md`

**Checkpoint**: User Story 5 reconciles property money independently and preserves separation of duties and platform revenue isolation.

---

## Phase 8: Polish and Cross-Cutting Verification

**Purpose**: Complete security hardening, documentation, full-system regression and final real-system evidence.

- [ ] T092 [P] Add redaction checks for VNPay secrets, terminal codes and provider payloads in `backend/src/test/java/com/hotel/security/Feature009SensitiveDataRedactionTest.java`
- [ ] T093 [P] Add abuse/rate-limit and provider-safe acknowledgement tests for both VNPay IPN endpoints in `backend/src/test/java/com/hotel/security/Feature009VnpayIngressSecurityTest.java`
- [ ] T094 [P] Add WebSocket authorization refresh/disconnect coverage for revoked roles/property access in `backend/src/test/java/com/hotel/security/WebSocketPermissionRevocationIntegrationTest.java`
- [ ] T095 Update environment contracts with separate property/platform VNPay sandbox variables and no secrets in `.env.example`
- [ ] T096 Update provider ingress, recovery and sandbox configuration guidance in `docs/testing/SANDBOX_CONFIGURATION_GUIDE.md`
- [ ] T097 Update RBAC action semantics, default-role behavior and revoked-task handling documentation in `docs/architecture/permission-model.md`
- [ ] T098 Run all backend unit, integration, SQL Server, tenant, security and concurrency suites and record results in `docs/testing/evidence/009/final/BACKEND_FINAL_REPORT.md`
- [ ] T099 [P] Run all frontend unit tests and production build and record results in `docs/testing/evidence/009/final/FRONTEND_FINAL_REPORT.md`
- [ ] T100 Run the five Playwright role journeys and both VNPay sandbox/simulator journeys on desktop and mobile in `frontend/e2e/feature-009-role-permissions-vnpay.spec.ts`
- [ ] T101 Execute every scenario in `specs/009-role-permissions-vnpay/quickstart.md` and record pass/fail evidence in `docs/testing/evidence/009/final/QUICKSTART_VALIDATION.md`
- [ ] T102 Reconcile all requirements, routes, APIs, entities and evidence back to FR/SC identifiers in `docs/audit/FEATURE_009_TRACEABILITY.md`
- [ ] T103 Record remaining known issues, resolutions and production blockers without enabling real money in `docs/testing/evidence/009/final/FINAL_READINESS_REPORT.md`

---

## Dependencies and Execution Order

### Phase Dependencies

- **Phase 1 - Setup**: Starts immediately and does not mutate product behavior.
- **Phase 2 - Foundational**: Depends on Phase 1 and blocks every user story.
- **Phase 3 - US1**: Depends on Phase 2; recommended MVP and prerequisite for the clearest dynamic-permission UX.
- **Phase 4 - US2**: Depends on Phase 2 and action semantics from US1 tasks T023-T025; task UI can proceed after contracts stabilize.
- **Phase 5 - US3**: Depends on Phase 2; can proceed in parallel with US1/US2 after shared action/error/VNPay fixtures exist.
- **Phase 6 - US4**: Depends on Phase 2 and shared VNPay URL/ingress primitives from T055/T057; order/payment work can otherwise proceed in parallel with US3.
- **Phase 7 - US5**: Depends on Phase 2 and reconciliation-case production from US3/US4; UI/report tests can start against deterministic fixtures earlier.
- **Phase 8 - Polish**: Depends on every user story selected for release.

### User Story Dependency Graph

```text
Setup -> Foundation -> US1 (MVP)
                    -> US2
                    -> US3 -> US4 shared VNPay primitives
                           -> US5 reconciliation inputs
                    -> US4
                    -> US5

US1 + US2 + US3 + US4 + US5 -> Polish and Final Verification
```

### User Story Independence

- **US1**: Independently proves dynamic role permissions, request-fresh revocation and tenant-safe enforcement.
- **US2**: Independently proves task assignment/execution/reassignment using existing domain services; it requires only foundational action semantics.
- **US3**: Independently proves customer booking payment through canonical Property Commerce VNPay.
- **US4**: Independently proves platform subscription purchase/renewal through VNPay; it may reuse the shared URL/transport adapter produced in US3.
- **US5**: Independently proves Accountant property reconciliation using fixed fixtures even before every live provider path is enabled.

### Within Each User Story

- Write and run story tests first; confirm they fail for the intended missing behavior.
- Apply migrations/entities before repositories and services.
- Complete services before controllers and frontend integration.
- Complete backend authorization before relying on UI hiding/disabling.
- Run the story checkpoint before moving to another phase.

## Parallel Execution Examples

### User Story 1

```text
T018 RolePermissionService tests
T019 Request-fresh revocation integration tests
T020 Property permission/IDOR tests
T021 Permission editor unit tests
T022 Route/menu tests
```

After T023-T026 stabilize backend contracts, T027-T028 can proceed together while T030-T031 cover independent route/layout files.

### User Story 2

```text
T035 Task model tests
T036 Task permission tests
T037 Task concurrency tests
T038 Task UI tests
T039 Task entities
T040 Task repositories
```

### User Story 3

```text
T050 Checkout URL tests
T051 IPN ingress tests
T052 Callback concurrency tests
T053 Legacy compatibility tests
T054 Frontend checkout/result tests
```

### User Story 4

```text
T064 Order permission tests
T065 Platform checkout tests
T066 Platform IPN/concurrency tests
T067 Recovery tests
T068 Platform payment UI tests
```

### User Story 5

```text
T080 Reconciliation totals tests
T081 Accountant permission tests
T082 Case resolution tests
T083 Reconciliation UI tests
T084 Reconciliation persistence
T088 Typed frontend client
```

## Implementation Strategy

### MVP First

1. Complete Phase 1 baseline.
2. Complete Phase 2 foundation and clean/upgrade migration gate.
3. Complete Phase 3 User Story 1.
4. Stop and validate permission editing, next-request revocation, direct API denial and cross-property isolation.
5. Demo the dynamic permission matrix before expanding operational/payment scope.

### Incremental Delivery

1. **MVP**: US1 dynamic permissions.
2. **Operations increment**: US2 authorized task execution.
3. **Customer revenue increment**: US3 canonical booking VNPay.
4. **Platform revenue increment**: US4 subscription VNPay.
5. **Finance increment**: US5 Accountant reconciliation.
6. Run Phase 8 only after the selected increments pass their independent checkpoints.

### Suggested Parallel Team Strategy

After Phase 2:

- Security stream: US1 and later permission hardening.
- Operations stream: US2 task model and domain adapters.
- Property payments stream: US3 booking VNPay.
- Platform payments stream: US4 after shared VNPay primitives stabilize.
- Finance/UI stream: US5 fixtures, reports and reconciliation UI.

## Notes

- Do not use real merchant credentials or real-money transactions.
- Do not trust frontend permission state, browser return parameters or caller-supplied property identity.
- Preserve unrelated dirty-worktree changes.
- Keep finalized financial and audit evidence immutable.
- Any production payment enablement, destructive migration or ambiguous legacy backfill requires separate approval.

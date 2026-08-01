# Tasks: Payment, Billing, and Full-System Completion

**Input**: Design documents in `specs/007-payment-billing-completion/`  
**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/financial-api-contract.md`, `quickstart.md`

## Task Execution Contract

Every task is part of Feature `007-payment-billing-completion` and must preserve unrelated dirty-worktree changes. A task is complete only when its mapped requirement/acceptance criterion is satisfied, affected permissions are enforced, automated tests appropriate to its layer pass, manual verification/evidence is recorded under `docs/testing/evidence/007/`, and a safe rollback/forward-recovery note exists for schema/configuration changes. `N/A` must be stated in evidence when a test layer, permission or migration does not apply. Production credentials, production database mutation, real-money execution, destructive cleanup and production enablement are stop gates requiring separate approval.

## Phase 1: Baseline and Planning Setup

**Purpose**: Capture evidence before fixes and create the audit/test scaffolding required by the supplied scope.

- [x] T001 Record branch, dirty-worktree file list, diff statistics and tool versions without modifying existing changes in `docs/testing/evidence/007/baseline/worktree.md`
- [x] T002 Run and record the unmodified backend test/package baseline, including pass/fail/skipped and environment, in `docs/testing/evidence/007/baseline/backend.md`
- [x] T003 Run and record the unmodified frontend unit/build/Playwright-list baseline in `docs/testing/evidence/007/baseline/frontend.md`
- [x] T004 [P] Create the financial audit directory and evidence naming convention in `docs/audit/financial/README.md`
- [x] T005 [P] Create the system audit directory and status definitions `COMPLETE_VERIFIED/PARTIAL/PLACEHOLDER/BROKEN/MISSING/BLOCKED_EXTERNAL/NOT_APPLICABLE` in `docs/audit/system/README.md`
- [x] T006 [P] Create the deterministic evidence index template with command, commit/worktree fingerprint, role, fixture and artifact checksum fields in `docs/testing/evidence/007/README.md`
- [x] T007 Build a source inventory extractor for Angular routes/menus/services, Spring controllers/services/repositories/entities, migrations, permissions and tests in `backend/tools/full-system-inventory.ps1`
- [x] T008 Run the extractor and store the unclassified baseline inventory in `docs/audit/system/inventory-source-baseline.json`
- [x] T009 [P] Create the initial two-context capability matrix mapped to FR-001 through FR-046 in `docs/audit/financial/PAYMENT_CAPABILITY_MATRIX.md`
- [x] T010 [P] Create the initial source-evidence gap audit, including existing strengths and confirmed blockers, in `docs/audit/financial/PAYMENT_AUDIT_REPORT_2026-07-31.md`
- [x] T011 [P] Create the full error-case catalog skeleton and stable error-code columns in `docs/audit/system/FULL_SYSTEM_ERROR_EXPECTATION_CATALOG.md`
- [x] T012 Record secret/environment drift, then remove hard-coded database/provider secrets through `.env.example`, `docker-compose.yml` and `backend/src/main/resources/application.yml`; document compatibility and rollback in `docs/audit/financial/PAYMENT_ENVIRONMENT_BASELINE.md`

---

## Phase 2: Foundational Security, Data, and Provider Boundaries

**Purpose**: Blocking controls required before any story-level financial mutation.

**Critical**: All tasks in this phase must complete before User Stories 1-6.

- [x] T013 Create additive SQL Server preflight checks for orphaned ownership, duplicate provider references and ambiguous legacy payment context in `backend/src/main/resources/db/preflight/feature007_financial_preflight.sql` (FR-042)
- [x] T014 Add Property Commerce configuration, payment-attempt and immutable ledger tables with VND/state/idempotency constraints in `backend/src/main/resources/db/migration/V21__property_commerce_foundation.sql` (FR-001, FR-002, FR-004, FR-008, FR-039, FR-042)
- [x] T015 Add reservation charge, invoice, invoice-line, allocation, credit-note and checkout-override tables in `backend/src/main/resources/db/migration/V22__property_checkout_invoice.sql` (FR-017-FR-023, FR-042)
- [x] T016 Add property refund and financial audit tables while preserving existing refund history in `backend/src/main/resources/db/migration/V23__property_refund_audit.sql` (FR-006, FR-024-FR-026, FR-042)
- [x] T017 Add Platform Billing configuration, order, payment-attempt and immutable ledger tables in `backend/src/main/resources/db/migration/V24__platform_billing_foundation.sql` (FR-001, FR-003, FR-027-FR-030, FR-039)
- [x] T018 Add software-contract, entitlement/history and platform-refund tables in `backend/src/main/resources/db/migration/V25__platform_contract_refund.sql` (FR-031-FR-033, FR-042)
- [x] T019 Add idempotent legacy backfill, ownership/context mapping, compatibility views and exception capture in `backend/src/main/resources/db/migration/V26__financial_context_backfill.sql` (FR-001, FR-039, FR-042)
- [x] T020 Add tenant/report/provider identity indexes and post-backfill uniqueness constraints in `backend/src/main/resources/db/migration/V27__financial_integrity_indexes.sql` (FR-014, FR-030, FR-039, FR-041, FR-042)
- [x] T021 Add clean-schema and upgrade-fixture SQL Server validation for V21-V29 in `backend/src/test/java/com/hotel/integration/FinancialMigrationIntegrationTest.java` and `backend/tools/feature007-sqlserver-validation.ps1` (SC-014)
- [x] T022 Add legacy ambiguity/preflight failure tests proving no silent context or tenant assignment in `backend/src/test/java/com/hotel/integration/FinancialBackfillSafetyIntegrationTest.java` (FR-042)
- [x] T023 [P] Implement scale-zero VND money validation/value helpers in `backend/src/main/java/com/hotel/paymentprovider/domain/VndMoney.java` and tests in `backend/src/test/java/com/hotel/paymentprovider/domain/VndMoneyTest.java` (FR-007)
- [x] T024 [P] Implement payment, booking-financial, refund and subscription-order state enums in `backend/src/main/java/com/hotel/paymentprovider/domain/FinancialStates.java` (FR-004, FR-005, FR-031)
- [x] T025 Implement explicit transition policies and accepted-transition audit hooks in `backend/src/main/java/com/hotel/paymentprovider/domain/FinancialTransitionPolicy.java` and `backend/src/test/java/com/hotel/paymentprovider/domain/FinancialTransitionPolicyTest.java` (FR-004-FR-006, SC-002)
- [x] T026 Audit tenant-owned business entities, add the constitution-required Hibernate filters, and activate/clear filters from authenticated property access in `backend/src/main/java/com/hotel/entities/` and `backend/src/main/java/com/hotel/security/TenantFilterInterceptor.java` (FR-011, FR-039)
- [x] T027 Add filter-coverage architecture tests plus cross-property repository tests for financial and non-financial business entities in `backend/src/test/java/com/hotel/security/TenantFilterArchitectureTest.java` and `backend/src/test/java/com/hotel/integration/FinancialTenantFilterIntegrationTest.java` (FR-039, SC-011)
- [x] T028 Define separate backend permission codes for configuration, manual confirmation, surcharge, invoice adjustment, debt override, property refund, platform billing and reports in `backend/src/main/java/com/hotel/security/FunctionCode.java` and seed migration `backend/src/main/resources/db/migration/V28__financial_permissions.sql` (FR-026, FR-040)
- [x] T029 Enforce the new financial permissions centrally in `backend/src/main/java/com/hotel/security/PermissionInterceptor.java` and test them in `backend/src/test/java/com/hotel/security/FinancialPermissionIntegrationTest.java` (FR-040)
- [x] T030 Implement append-only redacted financial audit events in `backend/src/main/java/com/hotel/paymentprovider/audit/FinancialAuditService.java` and tests in `backend/src/test/java/com/hotel/paymentprovider/audit/FinancialAuditServiceTest.java` (FR-006, FR-009)
- [x] T031 Implement persisted idempotency/payload-hash handling in `backend/src/main/java/com/hotel/paymentprovider/idempotency/FinancialIdempotencyService.java` and concurrency tests in `backend/src/test/java/com/hotel/paymentprovider/idempotency/FinancialIdempotencyServiceTest.java` (FR-014, FR-025, FR-030)
- [x] T032 Define shared provider SPI, normalized callback, verification result and retry classification in `backend/src/main/java/com/hotel/paymentprovider/spi/PaymentProviderAdapter.java` (FR-041)
- [x] T033 Implement fail-closed environment/readiness validation with no production fallback in `backend/src/main/java/com/hotel/paymentprovider/config/PaymentEnvironmentGuard.java` and tests in `backend/src/test/java/com/hotel/paymentprovider/config/PaymentEnvironmentGuardTest.java` (FR-009, FR-010, SC-012)
- [x] T034 Extend global errors with the stable financial error contract and secret-safe logging in `backend/src/main/java/com/hotel/controllers/GlobalExceptionHandler.java` and `backend/src/test/java/com/hotel/controllers/FinancialErrorContractTest.java` (FR-007, FR-009, FR-041, FR-046)
- [x] T035 [P] Add shared Angular money/status/error models and locale-safe presenters in `frontend/src/app/shared/financial/financial.models.ts` and tests in `frontend/src/app/shared/financial/financial.models.spec.ts` (FR-004, FR-005, FR-046)
- [x] T036 Add the Angular idempotency/correlation/error handling interceptor in `frontend/src/app/core/interceptors/financial-request.interceptor.ts` and register it in `frontend/src/app/app.config.ts` (FR-014, FR-046)

**Checkpoint**: Tenant isolation, permissions, transition rules, idempotency, migration safety and environment gates are testable before feature UI work.

---

## Phase 3: User Story 1 - Configure Property Payments (Priority: P1) - MVP

**Goal**: An authorized property user configures deposit, test bank/QR methods and bilingual instructions without exposing secrets or affecting other properties.

**Independent Test**: Save two different tenant configurations, retrieve masked values, validate environment readiness and prove cross-property access is denied.

- [x] T037 [P] [US1] Implement `PropertyPaymentConfiguration` entities with active tenant filtering in `backend/src/main/java/com/hotel/propertycommerce/config/PropertyPaymentConfiguration.java` (FR-008, FR-039)
- [x] T038 [P] [US1] Implement tenant-safe configuration repositories in `backend/src/main/java/com/hotel/propertycommerce/config/PropertyPaymentConfigurationRepository.java` (FR-011, FR-039)
- [x] T039 [US1] Implement bank/deposit/provider validation, account masking and production gate logic in `backend/src/main/java/com/hotel/propertycommerce/config/PropertyPaymentConfigurationService.java` (FR-008-FR-010)
- [x] T040 [US1] Expose view/update/validate endpoints from the financial API contract in `backend/src/main/java/com/hotel/propertycommerce/config/PropertyPaymentConfigurationController.java` (FR-008, FR-011)
- [x] T041 [P] [US1] Add configuration validation/masking/unit tests in `backend/src/test/java/com/hotel/propertycommerce/config/PropertyPaymentConfigurationServiceTest.java` (SC-004, SC-012)
- [x] T042 [US1] Add tenant and permission integration tests for configuration endpoints in `backend/src/test/java/com/hotel/propertycommerce/config/PropertyPaymentConfigurationIntegrationTest.java` (SC-011)
- [x] T043 [P] [US1] Add typed property-payment configuration API methods in `frontend/src/app/core/services/property-payment-configuration.service.ts` (FR-008)
- [x] T044 [US1] Build responsive payment configuration form with method readiness and masked bank/provider fields in `frontend/src/app/features/management/property-payment-configuration/property-payment-configuration.component.ts` (FR-008-FR-010)
- [x] T045 [US1] Add Vietnamese/English strings and validation messages in `frontend/src/assets/i18n/vi.json` and `frontend/src/assets/i18n/en.json` (FR-008, FR-046)
- [x] T046 [P] [US1] Add Angular form, masking, permission and responsive tests in `frontend/src/app/features/management/property-payment-configuration/property-payment-configuration.component.spec.ts` (FR-008-FR-011)
- [x] T047 [US1] Add management route/menu permission wiring in `frontend/src/app/app.routes.ts` and `frontend/src/app/layout/management-layout/management-layout.ts` (FR-040)
- [x] T048 [US1] Add Playwright tenant-isolation and configuration-readiness journey in `frontend/e2e/property-payment-configuration.spec.ts` (SC-004, SC-011, SC-012)
- [x] T049 [US1] Record configuration capability evidence and remaining external blockers in `docs/audit/financial/PROPERTY_PAYMENT_AUDIT.md` (FR-043, FR-044)

**Checkpoint**: Property payment setup is independently usable in simulator/sandbox and production remains disabled.

---

## Phase 4: User Story 2 - Book and Pay a Property Safely (Priority: P1)

**Goal**: A customer creates an authoritative booking/deposit attempt, receives correct property instructions, and obtains exactly one financial effect from valid confirmation.

**Independent Test**: Execute the booking/deposit journey with replay, concurrency, wrong amount/signature, expiry and cross-property cases.

- [x] T050 [P] [US2] Implement immutable deposit-policy snapshots on booking/payment attempts in `backend/src/main/java/com/hotel/propertycommerce/booking/DepositPolicySnapshot.java` (FR-015)
- [x] T051 [US2] Integrate server-owned deposit calculation into booking creation in `backend/src/main/java/com/hotel/services/ReservationService.java` and tests in `backend/src/test/java/com/hotel/services/ReservationServiceTest.java` (FR-007, FR-015)
- [x] T052 [P] [US2] Implement property payment-attempt and ledger entities/repositories in `backend/src/main/java/com/hotel/propertycommerce/payment/` (FR-002, FR-004, FR-016, FR-039)
- [x] T053 [US2] Implement booking financial summary calculation from charges, successful transactions and refunds in `backend/src/main/java/com/hotel/propertycommerce/booking/BookingFinancialSummaryService.java` (FR-005, FR-016)
- [x] T054 [US2] Implement idempotent attempt creation with exact amount, expiry, receiver snapshot and unique transfer content in `backend/src/main/java/com/hotel/propertycommerce/payment/PropertyPaymentAttemptService.java` (FR-012-FR-015)
- [x] T055 [P] [US2] Adapt VNPay/MoMo/ZaloPay/simulator providers to the shared verification SPI in `backend/src/main/java/com/hotel/paymentprovider/adapters/` (FR-010, FR-041)
- [x] T056 [US2] Implement property callback orchestration with locking and exactly-once ledger effects in `backend/src/main/java/com/hotel/propertycommerce/payment/PropertyPaymentCallbackService.java` (FR-006, FR-014, FR-041)
- [x] T057 [US2] Implement permissioned manual-transfer confirmation that cannot self-confirm from public UI in `backend/src/main/java/com/hotel/propertycommerce/payment/ManualTransferConfirmationService.java` (FR-013, FR-026)
- [x] T058 [US2] Expose financial summary, attempt, cancel, manual-confirm and property callback endpoints in `backend/src/main/java/com/hotel/propertycommerce/payment/PropertyPaymentController.java` (FR-011-FR-014, FR-041)
- [x] T059 [US2] Add a read-only compatibility adapter from legacy `PaymentSession`/`Payment` records during migration in `backend/src/main/java/com/hotel/propertycommerce/payment/LegacyPropertyPaymentAdapter.java` (FR-042)
- [x] T060 [P] [US2] Add attempt/instruction/deposit unit tests in `backend/src/test/java/com/hotel/propertycommerce/payment/PropertyPaymentAttemptServiceTest.java` (SC-004)
- [x] T061 [P] [US2] Add callback signature/merchant/amount/currency/reference/expiry contract tests in `backend/src/test/java/com/hotel/paymentprovider/PropertyProviderContractTest.java` (FR-041)
- [x] T062 [US2] Add replay and concurrent callback integration tests in `backend/src/test/java/com/hotel/propertycommerce/payment/PropertyPaymentCallbackConcurrencyIntegrationTest.java` (SC-003)
- [x] T063 [US2] Add manual confirmation permission, audit and cross-property tests in `backend/src/test/java/com/hotel/propertycommerce/payment/ManualTransferConfirmationIntegrationTest.java` (FR-013, FR-026, SC-011)
- [x] T064 [P] [US2] Add typed booking financial/attempt API methods in `frontend/src/app/core/services/property-payment.service.ts` (FR-012-FR-016)
- [x] T065 [US2] Replace caller-authoritative checkout payment data with server-owned attempt creation in `frontend/src/app/features/client/booking-checkout/booking-checkout.component.ts` (FR-007, FR-014)
- [x] T066 [US2] Build accessible QR/manual instructions, expiry, environment label, polling and safe retry states in `frontend/src/app/features/client/booking-checkout/property-payment-panel.component.ts` (FR-010, FR-012-FR-014)
- [x] T067 [P] [US2] Add Angular payment-panel unit tests for pending/success/failure/expired/retry and bilingual display in `frontend/src/app/features/client/booking-checkout/property-payment-panel.component.spec.ts` (FR-004, FR-046)
- [x] T068 [US2] Add Playwright booking/deposit success, replay and concurrency journey in `frontend/e2e/property-booking-payment.spec.ts` (SC-003-SC-005)
- [x] T069 [US2] Add Playwright invalid date/capacity/price-tamper/expiry/signature/IDOR cases in `frontend/e2e/property-booking-payment-negative.spec.ts` (SC-011)

---

## Phase 5: User Story 3 - Complete the Stay, Checkout, and Invoice (Priority: P1)

**Goal**: Staff add immutable charges, settle through multiple payments, atomically checkout and issue a stable invoice without leaving inconsistent room/housekeeping state.

**Independent Test**: Reconcile a complete folio to one VND, block underpayment, then inject failures at every checkout boundary and prove rollback.

- [x] T070 [P] [US3] Implement reservation charge-line entities/repositories with service identity, price, tax, actor and usage snapshots in `backend/src/main/java/com/hotel/propertycommerce/folio/` (FR-017, FR-018)
- [x] T071 [US3] Implement server-priced service/minibar charge creation and append-only corrections in `backend/src/main/java/com/hotel/propertycommerce/folio/ReservationChargeService.java` (FR-017)
- [x] T072 [US3] Implement typed surcharge/adjustment creation with separate negative-adjustment permission in `backend/src/main/java/com/hotel/propertycommerce/folio/SurchargeService.java` (FR-018, FR-026)
- [x] T073 [US3] Implement authoritative folio calculation for room/service/surcharge/tax/fee/discount/payment/refund/balance in `backend/src/main/java/com/hotel/propertycommerce/checkout/FolioCalculationService.java` (FR-019, SC-005)
- [x] T074 [US3] Implement checkout preview and server-owned settlement validation in `backend/src/main/java/com/hotel/propertycommerce/checkout/CheckoutPreviewService.java` (FR-019, FR-020)
- [x] T075 [US3] Implement debt/overpayment policy and reasoned override evidence in `backend/src/main/java/com/hotel/propertycommerce/checkout/CheckoutOverrideService.java` (FR-020, FR-026)
- [x] T076 [P] [US3] Implement immutable invoice/line/allocation entities and repositories in `backend/src/main/java/com/hotel/propertycommerce/invoice/` (FR-022)
- [x] T077 [US3] Implement invoice finalization from the locked folio and allocated successful transactions in `backend/src/main/java/com/hotel/propertycommerce/invoice/InvoiceFinalizationService.java` (FR-021, FR-022)
- [x] T078 [US3] Implement credit-note/adjustment workflow without rewriting finalized invoices in `backend/src/main/java/com/hotel/propertycommerce/invoice/CreditNoteService.java` (FR-022, FR-026)
- [x] T079 [US3] Refactor checkout into one locked transaction covering reservation, invoice, assignments, rooms and housekeeping in `backend/src/main/java/com/hotel/services/ReservationService.java` (FR-020, FR-021)
- [x] T080 [US3] Add exactly-once dirty-room and housekeeping-task behavior to checkout in `backend/src/main/java/com/hotel/propertycommerce/checkout/CheckoutOperationsService.java` (FR-021)
- [x] T081 [US3] Expose charge, surcharge, preview, checkout, invoice, PDF/email and credit-note endpoints in `backend/src/main/java/com/hotel/propertycommerce/checkout/PropertyCheckoutController.java` and `backend/src/main/java/com/hotel/propertycommerce/invoice/PropertyInvoiceController.java` (FR-017-FR-023)
- [x] T082 [P] [US3] Add folio/service/surcharge unit tests in `backend/src/test/java/com/hotel/propertycommerce/folio/FolioCalculationServiceTest.java` (SC-005)
- [x] T083 [US3] Add underpayment/overpayment/debt-override integration tests in `backend/src/test/java/com/hotel/propertycommerce/checkout/CheckoutBalanceIntegrationTest.java` (FR-020)
- [x] T084 [US3] Add failure injection at each checkout persistence boundary in `backend/src/test/java/com/hotel/propertycommerce/checkout/CheckoutRollbackIntegrationTest.java` (SC-006)
- [x] T085 [US3] Add invoice immutability, allocation and credit-note tests in `backend/src/test/java/com/hotel/propertycommerce/invoice/InvoiceImmutabilityIntegrationTest.java` (FR-022)
- [x] T086 [US3] Add customer/staff invoice IDOR, PDF and email-recipient tests in `backend/src/test/java/com/hotel/propertycommerce/invoice/InvoiceAccessIntegrationTest.java` (FR-023, SC-011)
- [x] T087 [P] [US3] Add management folio/checkout API clients in `frontend/src/app/core/services/property-checkout.service.ts` (FR-017-FR-023)
- [x] T088 [US3] Build responsive service/surcharge and checkout-preview UI in `frontend/src/app/features/admin/reservation-management/reservation-checkout.component.ts` (FR-017-FR-020)
- [x] T089 [US3] Build invoice detail, PDF download and email states in `frontend/src/app/features/client/my-invoices/my-invoices.component.ts` (FR-023)
- [x] T090 [P] [US3] Add Angular folio/override/invoice state tests in `frontend/src/app/features/admin/reservation-management/reservation-checkout.component.spec.ts` (FR-020, FR-023)
- [x] T091 [US3] Add the complete check-in/services/multi-payment/checkout/invoice/housekeeping Playwright journey in `frontend/e2e/stay-checkout-invoice.spec.ts` (SC-005, SC-006)

---

## Phase 6: User Story 4 - Purchase and Manage a SaaS Subscription (Priority: P1)

**Goal**: An owner pays the platform merchant for a backend-snapshotted order and obtains exactly one eligible contract/entitlement transition.

**Independent Test**: Purchase, renew and upgrade through platform sandbox with replay/concurrency while failed, expired, tampered or unsupported transitions activate nothing.

- [x] T092 [P] [US4] Implement platform configuration, order, attempt, ledger, contract and history entities/repositories in `backend/src/main/java/com/hotel/platformbilling/` (FR-027-FR-033)
- [x] T093 [US4] Implement backend catalog snapshot and expiring order creation in `backend/src/main/java/com/hotel/platformbilling/order/SubscriptionOrderService.java` (FR-027)
- [x] T094 [US4] Implement masked system-merchant configuration/readiness validation and system-merchant-only payment attempt creation in `backend/src/main/java/com/hotel/platformbilling/config/PlatformPaymentConfigurationService.java` and `backend/src/main/java/com/hotel/platformbilling/payment/PlatformPaymentAttemptService.java` (FR-009, FR-010, FR-028)
- [x] T095 [US4] Implement verified platform callback orchestration and exactly-once financial effect in `backend/src/main/java/com/hotel/platformbilling/payment/PlatformPaymentCallbackService.java` (FR-029, FR-030, FR-041)
- [ ] T096 [US4] Implement idempotent contract, entitlement and history application in `backend/src/main/java/com/hotel/platformbilling/subscription/SubscriptionApplicationService.java` (FR-029-FR-031)
- [ ] T097 [US4] Implement renewal order/application rules without hard-coded one-year duration in `backend/src/main/java/com/hotel/platformbilling/subscription/SubscriptionRenewalService.java` (FR-027, FR-031)
- [ ] T098 [US4] Implement approved upgrade validation/application and catalog usage-limit checks in `backend/src/main/java/com/hotel/platformbilling/subscription/SubscriptionUpgradeService.java` (FR-031, FR-032)
- [ ] T099 [US4] Implement explicit `POLICY_NOT_CONFIGURED` blocking for unapproved downgrade/proration behavior in `backend/src/main/java/com/hotel/platformbilling/subscription/SubscriptionPolicyService.java` (FR-032)
- [ ] T100 [US4] Expose masked platform configuration/readiness, catalog, order, attempt, cancel, renewal, upgrade, downgrade and history endpoints in `backend/src/main/java/com/hotel/platformbilling/PlatformBillingController.java` (FR-009, FR-010, FR-027-FR-032)
- [ ] T101 [P] [US4] Add order snapshot/expiry/policy unit tests in `backend/src/test/java/com/hotel/platformbilling/SubscriptionOrderServiceTest.java` (SC-008)
- [ ] T102 [P] [US4] Add platform provider signature/merchant/amount/order contract tests in `backend/src/test/java/com/hotel/paymentprovider/PlatformProviderContractTest.java` (FR-041)
- [ ] T103 [US4] Add replay/concurrent callback and exactly-once entitlement integration tests in `backend/src/test/java/com/hotel/platformbilling/PlatformCallbackConcurrencyIntegrationTest.java` (SC-003, SC-008)
- [ ] T104 [US4] Add order-owner/property access and property-merchant separation tests in `backend/src/test/java/com/hotel/platformbilling/PlatformBillingSecurityIntegrationTest.java` (SC-011)
- [ ] T105 [P] [US4] Add typed catalog/order/payment/history API methods in `frontend/src/app/core/services/platform-billing.service.ts` (FR-027-FR-032)
- [ ] T106 [US4] Refactor subscription billing UI to display backend snapshot, environment, expiry and truthful policy blockers, and build the separate system-admin merchant readiness panel in `frontend/src/app/features/management/subscription-billing/subscription-billing.component.ts` and `frontend/src/app/features/admin/platform-payment-configuration/platform-payment-configuration.component.ts` (FR-009, FR-010, FR-027-FR-032)
- [ ] T107 [US4] Add platform payment simulator/sandbox status handling without client activation controls in `frontend/src/app/features/management/subscription-billing/platform-payment-panel.component.ts` (FR-029, FR-030)
- [ ] T108 [P] [US4] Add Angular purchase/renew/upgrade/failure/policy and platform-readiness masking tests in `frontend/src/app/features/management/subscription-billing/subscription-billing.component.spec.ts` and `frontend/src/app/features/admin/platform-payment-configuration/platform-payment-configuration.component.spec.ts` (SC-008, SC-012)
- [ ] T109 [US4] Add owner registration/property approval/plan purchase/activation Playwright journey in `frontend/e2e/platform-subscription-purchase.spec.ts` (SC-008)
- [ ] T110 [US4] Add tampered price, wrong merchant, replay, expiry, cancellation and unsupported downgrade Playwright cases in `frontend/e2e/platform-subscription-negative.spec.ts` (SC-008, SC-011)
- [ ] T111 [US4] Record platform lifecycle evidence, policy blockers and integration variables in `docs/audit/financial/PLATFORM_BILLING_AUDIT.md` (FR-043, FR-044)

---

## Phase 7: User Story 5 - Refund Safely and Partially (Priority: P1)

**Goal**: Property and platform refunds preserve original payments, never exceed refundable balance and apply exactly one context-correct effect.

**Independent Test**: Submit sequential, repeated and concurrent full/partial refund requests, including unauthorized and wrong-context cases.

- [ ] T112 [P] [US5] Implement property refund aggregate/repositories against original property ledger transactions in `backend/src/main/java/com/hotel/propertycommerce/refund/` (FR-024, FR-025)
- [ ] T113 [P] [US5] Implement platform refund aggregate/repositories isolated from Property Commerce in `backend/src/main/java/com/hotel/platformbilling/refund/` (FR-025, FR-033)
- [ ] T114 [US5] Implement locked refundable-balance validation and exactly-once property refund effects in `backend/src/main/java/com/hotel/propertycommerce/refund/PropertyRefundService.java` (FR-024-FR-026)
- [ ] T115 [US5] Implement platform refund processing with approved entitlement-policy gate in `backend/src/main/java/com/hotel/platformbilling/refund/PlatformRefundService.java` (FR-032, FR-033)
- [ ] T116 [US5] Adapt provider refund attempts/callbacks to the shared SPI in `backend/src/main/java/com/hotel/paymentprovider/refund/` (FR-025, FR-041)
- [ ] T117 [US5] Expose property/platform refund request, approval and status endpoints in `backend/src/main/java/com/hotel/propertycommerce/refund/PropertyRefundController.java` and `backend/src/main/java/com/hotel/platformbilling/refund/PlatformRefundController.java` (FR-024-FR-026, FR-033)
- [ ] T118 [P] [US5] Add refundable-balance, transition and policy unit tests in `backend/src/test/java/com/hotel/propertycommerce/refund/PropertyRefundServiceTest.java` and `backend/src/test/java/com/hotel/platformbilling/refund/PlatformRefundServiceTest.java` (SC-007)
- [ ] T119 [US5] Add concurrent/replayed/excessive refund integration tests in `backend/src/test/java/com/hotel/integration/FinancialRefundConcurrencyIntegrationTest.java` (SC-003, SC-007)
- [ ] T120 [US5] Add cross-property, wrong-context and manual-refund permission tests in `backend/src/test/java/com/hotel/integration/FinancialRefundSecurityIntegrationTest.java` (SC-011)
- [ ] T121 [P] [US5] Add typed property/platform refund API clients in `frontend/src/app/core/services/refund.service.ts` (FR-024-FR-026, FR-033)
- [ ] T122 [US5] Build customer and property-role refund status/request UI in `frontend/src/app/features/client/profile/refund-history.component.ts` and `frontend/src/app/features/admin/reservation-management/refund-management.component.ts` (FR-024-FR-026)
- [ ] T123 [US5] Build system-admin platform refund UI with policy blocker states in `frontend/src/app/features/admin/platform-refunds/platform-refunds.component.ts` (FR-033)
- [ ] T124 [US5] Add cancellation/refund success, provider failure, fake callback, replay and concurrency Playwright journey in `frontend/e2e/refund-lifecycle.spec.ts` (SC-007, SC-011)

---

## Phase 8: User Story 6 - Reconcile Property and Platform Reports (Priority: P2)

**Goal**: Context-specific APIs and exports reconcile to authoritative payments, invoice lines and refunds without double-counting deposits.

**Independent Test**: For fixed filters, compare API totals/rows and exported files with database assertions to one VND.

- [ ] T125 [P] [US6] Define recognition basis, normalized filters and report result models in `backend/src/main/java/com/hotel/paymentprovider/reporting/RevenueReportModels.java` (FR-034-FR-038)
- [ ] T126 [US6] Implement tenant-filtered Property Commerce report queries in `backend/src/main/java/com/hotel/propertycommerce/reporting/PropertyRevenueRepository.java` (FR-034, FR-036)
- [ ] T127 [US6] Implement property gross/refund/net, cash/invoiced/unpaid and allocation-safe reconciliation in `backend/src/main/java/com/hotel/propertycommerce/reporting/PropertyRevenueService.java` (FR-034, FR-036, FR-038)
- [ ] T128 [US6] Expose property revenue/report detail endpoints in `backend/src/main/java/com/hotel/propertycommerce/reporting/PropertyRevenueController.java` (FR-034, FR-037)
- [ ] T129 [US6] Implement system-scoped Platform Billing report queries/service/endpoints in `backend/src/main/java/com/hotel/platformbilling/reporting/PlatformRevenueService.java` and `backend/src/main/java/com/hotel/platformbilling/reporting/PlatformRevenueController.java` (FR-035, FR-036)
- [ ] T130 [US6] Implement shared Excel/PDF/CSV export from the same report result model in `backend/src/main/java/com/hotel/paymentprovider/reporting/RevenueExportService.java` (FR-037)
- [ ] T131 [US6] Implement property/platform reconciliation runners with mismatch queues in `backend/src/main/java/com/hotel/paymentprovider/reporting/FinancialReconciliationService.java` (FR-038)
- [ ] T132 [P] [US6] Add property report database reconciliation tests in `backend/src/test/java/com/hotel/propertycommerce/reporting/PropertyRevenueReconciliationIntegrationTest.java` (SC-009, SC-010)
- [ ] T133 [P] [US6] Add platform report database reconciliation tests in `backend/src/test/java/com/hotel/platformbilling/reporting/PlatformRevenueReconciliationIntegrationTest.java` (SC-009, SC-010)
- [ ] T134 [US6] Add export row/filter/total/checksum reconciliation tests in `backend/src/test/java/com/hotel/paymentprovider/reporting/RevenueExportIntegrationTest.java` (SC-010)
- [ ] T135 [US6] Add report permission and property/platform separation tests in `backend/src/test/java/com/hotel/integration/FinancialReportingSecurityIntegrationTest.java` (SC-011)
- [ ] T136 [P] [US6] Add typed report/export API clients in `frontend/src/app/core/services/revenue-report.service.ts` (FR-034-FR-037)
- [ ] T137 [US6] Replace mock property analytics with responsive reconciled cards/charts/tables/filters in `frontend/src/app/features/management/property-revenue/property-revenue.component.ts` (FR-034, FR-036)
- [ ] T138 [US6] Build separate SaaS revenue dashboard and export states in `frontend/src/app/features/admin/platform-revenue/platform-revenue.component.ts` (FR-035-FR-037)
- [ ] T139 [US6] Add Playwright report filter/export/context-isolation journey in `frontend/e2e/financial-reporting.spec.ts` (SC-009-SC-011)
- [ ] T140 [US6] Document executed equations, fixtures, mismatches and evidence in `docs/audit/financial/PROPERTY_REVENUE_RECONCILIATION.md` and `docs/audit/financial/PLATFORM_REVENUE_RECONCILIATION.md` (FR-038)

---

## Phase 9: User Story 7 - Verify and Complete the Full System (Priority: P2)

**Goal**: Inventory every reachable capability, create tasks for every non-complete item, and prove the final worktree through fresh role-based evidence.

**Independent Test**: Rebuild from clean migrations, execute all mandatory role journeys and confirm every inventory row has traceability, status, evidence and a remediation task when needed.

- [ ] T141 [P] [US7] Audit authentication, registration, login/logout/refresh, password, profile/avatar and social-login/email flows in `docs/audit/system/inventory/auth-account.md` (FR-043, FR-044)
- [ ] T142 [P] [US7] Audit property onboarding, approval, suspension, subscription plans/limits and owner lifecycle in `docs/audit/system/inventory/property-subscription.md` (FR-043, FR-044)
- [ ] T143 [P] [US7] Audit staff, roles, permissions, property data, images, amenities, policies, rooms, room states, maintenance and services in `docs/audit/system/inventory/property-operations.md` (FR-043, FR-044)
- [ ] T144 [P] [US7] Audit public search, autocomplete, filters/sort/pagination, details, availability, capacity, pricing and booking/multi-room behavior in `docs/audit/system/inventory/public-booking.md` (FR-043, FR-044)
- [ ] T145 [P] [US7] Audit booking changes/cancellation/no-show, check-in, assignment, stay charges, checkout, housekeeping, review, favorite and voucher behavior in `docs/audit/system/inventory/stay-lifecycle.md` (FR-043, FR-044)
- [ ] T146 [P] [US7] Audit notifications, email, chat/support, dashboards, exports, audit log, loading/error/empty states, responsive layout and accessibility in `docs/audit/system/inventory/cross-cutting.md` (FR-043-FR-046)
- [ ] T147 [US7] Consolidate all discovered routes/APIs/modules into `docs/audit/system/MASTER_FUNCTION_INVENTORY.md` with evidence-based status and no source-only `COMPLETE_VERIFIED` classification (SC-013)
- [ ] T148 [US7] Build route-to-UI-to-service-to-API-to-database-to-permission-to-test mapping in `docs/audit/system/FULL_SYSTEM_TRACEABILITY_MATRIX.md` (FR-043)
- [ ] T149 [US7] Append a concrete dependency-ordered remediation task for every `PARTIAL`, `PLACEHOLDER`, `BROKEN` or `MISSING` inventory row to `specs/007-payment-billing-completion/tasks.md`; document `BLOCKED_EXTERNAL` adapter/simulator/contract/config work (FR-044, SC-013)
- [ ] T150 [US7] Run SpecKit converge after inventory implementation and capture newly appended task IDs in `docs/audit/system/CONVERGENCE_REPORT.md` (FR-044)
- [ ] T151 [US7] Execute all appended remediation tasks and update their inventory evidence/status in `docs/audit/system/MASTER_FUNCTION_INVENTORY.md` (FR-044)
- [ ] T152 [US7] Produce the final module/role/severity/root-cause audit with zero unsupported completion claims in `docs/audit/system/FULL_SYSTEM_AUDIT_REPORT.md` (FR-043-FR-045)
- [ ] T153 [P] [US7] Create positive/negative/concurrent/timeout/rollback test coverage mapping in `docs/audit/system/FULL_SYSTEM_TEST_MATRIX.md` (FR-045, FR-046)
- [ ] T154 [P] [US7] Complete UI message, HTTP status, error code, database mutation, retry and audit expectations in `docs/audit/system/FULL_SYSTEM_ERROR_EXPECTATION_CATALOG.md` (FR-046)
- [ ] T155 [US7] Write the non-technical role-based manual guide with five mandatory journeys, concrete inputs, screenshots and safe reset steps in `docs/testing/FULL_SYSTEM_MANUAL_TEST_GUIDE.md` (FR-046, SC-015)
- [ ] T156 [P] [US7] Document simulator/sandbox variables, provider contract checks and no-real-money rules in `docs/testing/SANDBOX_CONFIGURATION_GUIDE.md` (FR-010, FR-041)
- [ ] T157 [P] [US7] Create the production-disabled readiness checklist with secret, migration, provider, rollback, monitoring and approval gates in `docs/testing/PRODUCTION_READINESS_CHECKLIST.md` (FR-010, FR-042)
- [ ] T158 [US7] Rebuild an empty SQL Server test database through every Flyway migration and deterministic seed, recording evidence in `docs/testing/evidence/007/final/clean-migration.md` (FR-045)
- [ ] T159 [US7] Run all backend unit/integration/security/tenant/concurrency/reconciliation tests on the final worktree and record results in `docs/testing/evidence/007/final/backend.md` (FR-045, SC-014)
- [ ] T160 [US7] Run Angular unit tests and production build on the final worktree and record results in `docs/testing/evidence/007/final/frontend.md` (FR-045, SC-014)
- [ ] T161 [US7] Run Playwright for public, customer, owner, receptionist, staff, housekeeping, admin and super-admin journeys and store traces/screenshots in `docs/testing/evidence/007/final/playwright/` (FR-045, SC-014)
- [ ] T162 [US7] Verify exported financial files and all report equations against final database fixtures in `docs/testing/evidence/007/final/reconciliation.md` (FR-038, FR-045)
- [ ] T163 [US7] Publish final counts for statuses/tests/known issues and worktree fingerprint in `docs/testing/FINAL_WORKTREE_TEST_REPORT.md` (SC-013-SC-015)

---

## Phase 10: Polish and Cross-Cutting Release Controls

**Purpose**: Remove residual UX/documentation inconsistencies without weakening financial or tenant controls.

- [ ] T164 [P] Normalize Vietnamese/English financial terminology and status labels in `frontend/src/assets/i18n/vi.json` and `frontend/src/assets/i18n/en.json` (FR-046)
- [ ] T165 [P] Audit responsive behavior at 320/375/768/1024/1440 widths for all new financial screens and record fixes in `docs/testing/evidence/007/final/responsive.md` (FR-046)
- [ ] T166 [P] Add keyboard, focus, label, contrast, reduced-motion and screen-reader checks for financial forms/dialogs in `frontend/e2e/financial-accessibility.spec.ts` (FR-046)
- [ ] T167 Remove hard-coded/mock revenue and misleading live-payment labels after parity verification in `backend/src/main/java/com/hotel/services/AnalyticsService.java` and affected Angular dashboards (FR-010, FR-034-FR-038)
- [ ] T168 Verify logs, exports, screenshots and error responses contain no secrets/full account identifiers in `docs/testing/evidence/007/final/privacy-review.md` (FR-009)
- [ ] T169 Verify callback polling/rate-limit/replay abuse controls in `backend/src/test/java/com/hotel/security/PaymentCallbackAbuseIntegrationTest.java` (FR-041)
- [ ] T170 Verify production profiles fail closed and simulator/sandbox cannot be mislabeled as live in `backend/src/test/java/com/hotel/integration/ProductionPaymentSafetyIntegrationTest.java` (SC-012)
- [ ] T171 Update API documentation for the two bounded contexts and stable errors in `docs/API_SPEC.md` (FR-001, FR-046)
- [ ] T172 Update ERD/UML/architecture documentation for the final entities and transitions in `docs/ERD.md`, `docs/UML.md` and `docs/architecture/payment-billing-contexts.md` (FR-001-FR-006)
- [ ] T173 Cross-check every FR-001-FR-046 and SC-001-SC-015 against task/evidence IDs in `docs/audit/system/FEATURE_007_REQUIREMENT_TRACEABILITY.md`
- [ ] T174 Confirm required test skips, P0/P1 issues and in-scope `PARTIAL/PLACEHOLDER/BROKEN/MISSING` counts are zero or stop release in `docs/testing/FINAL_WORKTREE_TEST_REPORT.md`
- [ ] T175 Confirm no production credential, real merchant, real-money evidence or production-enable change is present; leave production disabled in `docs/testing/PRODUCTION_READINESS_CHECKLIST.md`
- [ ] T176 Validate callback p95, 100,000-row report p95 and browser financial-action timing budgets from `plan.md` in `backend/src/test/java/com/hotel/performance/FinancialPerformanceIntegrationTest.java` and `frontend/e2e/financial-performance.spec.ts`

---

## Phase 11: Legacy Backlog Reconciliation

**Purpose**: Keep older feature task files connected to the current implementation stream without deleting, silently closing or duplicating their work. See `docs/audit/system/LEGACY_TASK_RECONCILIATION_2026-07-31.md`.

- [ ] T177 Re-run the legacy reconciliation against the final worktree and update `docs/audit/system/LEGACY_TASK_RECONCILIATION_2026-07-31.md` with evidence for every `MERGED_PENDING` item
- [ ] T178 Complete Feature 01 RBAC acceptance and `PROPERTY_OWNER` migration evidence if Feature 007 T027-T029/T174 do not cover it in `backend/src/test/java/com/hotel/security/` and `backend/src/main/resources/db/migration/`
- [ ] T179 Complete Feature 02B shell/navigation implementation and route/responsive/security regression not covered by Feature 007 T141-T166 in `frontend/src/app/layout/` and `frontend/e2e/`
- [ ] T180 Complete Feature 04 thesis DOCX/PDF render/review deliverables and attach final artifact evidence in `docs/export/` and `docs/audit/`
- [ ] T181 Complete Feature 06 promotion stacking, membership tier, sponsored placement, quote consistency and admin lifecycle work after OQ-002 to OQ-005 policy approval in `backend/src/main/java/com/hotel/`, `frontend/src/app/` and `specs/006-booking-marketplace-roadmap/`
- [ ] T182 Complete the approved Facebook/Zalo tenant support adapter and management UI only after OQ-006 to OQ-009 decisions and sandbox credentials in `backend/src/main/java/com/hotel/`, `frontend/src/app/` and `specs/006-booking-marketplace-roadmap/`; otherwise keep it `BLOCKED_EXTERNAL`
- [ ] T183 Run final legacy task/status convergence, update every original task file with evidence-backed status and prepare a separate commit/push recommendation in `docs/audit/system/LEGACY_TASK_RECONCILIATION_2026-07-31.md`; do not push automatically

---

## Dependencies and Execution Order

### Phase dependencies

- Phase 1 has no feature dependency and records the baseline before fixes.
- Phase 2 depends on Phase 1 and blocks all financial implementation.
- US1 depends on Phase 2.
- US2 depends on Phase 2 and the property configuration contract from US1.
- US3 depends on Phase 2 and Property Commerce ledger/summary behavior from US2.
- US4 depends on Phase 2 but can proceed in parallel with US2/US3 because it owns separate packages/tables/APIs.
- US5 depends on successful immutable transactions from US2 and US4.
- US6 depends on US2-US5 financial evidence.
- US7 inventory tasks can begin after Phase 1, but convergence, remediation and final regression depend on all implementation stories.
- Phase 10 depends on all story phases and final inventory convergence.

### User story graph

```text
Baseline -> Foundation -> US1 -> US2 -> US3 --+
                       \-> US4 --------------+-> US5 -> US6 -> US7 final regression -> Polish
Baseline ----------------------------------------> US7 inventory
```

## Parallel Opportunities

- T004-T006 and T009-T011 create independent documentation scaffolds.
- T023-T024, T030, T032, T035 can proceed in separate files after migration contracts are fixed.
- US1 backend entities, Angular client and translations can proceed in parallel before integration.
- US2 provider-adapter contract work can proceed alongside property-attempt models and frontend presentation.
- US3 invoice models, folio models and Angular API client can proceed in parallel before checkout orchestration.
- US4 is structurally parallel to US2/US3 after foundational boundaries are stable.
- Property and platform refund aggregates/tests can proceed in parallel.
- Property/platform report queries/tests can proceed in parallel while sharing only normalized report models.
- T141-T146 are independent inventory slices and should be merged before T147-T150.

## Implementation Strategy

### MVP first

1. Finish baseline evidence and foundational tenant/security/migration/provider controls.
2. Deliver US1 property payment configuration as the smallest independently testable slice.
3. Keep production disabled; validate only simulator/sandbox readiness.

### Incremental delivery

1. US2 establishes authoritative booking payment and immutable property ledger evidence.
2. US3 completes operational checkout and invoice correctness.
3. US4 completes independent SaaS billing.
4. US5 adds safe reversal behavior for both contexts.
5. US6 replaces mock analytics with reconciled reporting.
6. US7 converges the rest of the product and executes final-worktree regression.

### Stop and approval boundaries

- Do not execute production migrations, destructive cleanup or ambiguous backfill.
- Do not add/use production secrets, real merchant accounts or real money.
- Do not enable production payment or invent proration/refund entitlement policy.
- Do not reset/revert unrelated worktree changes.

## Format Validation

All executable tasks use `- [ ] T### [P?] [US?] Description with file path`. Setup/foundation/polish tasks omit story labels; user-story tasks include `[US1]` through `[US7]`.

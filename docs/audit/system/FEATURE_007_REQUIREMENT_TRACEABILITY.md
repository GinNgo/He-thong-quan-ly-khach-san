# Feature 007 Requirement Traceability

Date: 2026-08-03
Scope: Feature 007 (`FR-001` through `FR-046`, `SC-001` through `SC-015`)
Source of truth: [spec.md](../../../specs/007-payment-billing-completion/spec.md) and [tasks.md](../../../specs/007-payment-billing-completion/tasks.md)

## Status rules

- `VERIFIED`: executable or database-backed evidence directly proves the requirement's material acceptance branch. This is not a final-worktree release pass unless the row says so.
- `PARTIAL`: implementation/tasks exist, but evidence is source-only, narrower than the requirement, stale relative to the final worktree, or an acceptance branch remains open.
- `GAP`: required implementation, document, or final executable evidence is absent/open.
- `BLOCKED_EXTERNAL`: completion depends on an approval, policy, credential, provider, or production gate outside this worktree. Simulator/contract evidence does not remove that blocker.
- A checked task is traceability, not proof by itself. Historical command output and source inspection are never promoted to `VERIFIED` without an executable evidence artifact.

## Artifact ID registry

### Code and test IDs

| ID | Artifact |
|---|---|
| `C-MIG` | [V21 Property Commerce](../../../backend/src/main/resources/db/migration/V21__property_commerce_foundation.sql), [V22 checkout/invoice](../../../backend/src/main/resources/db/migration/V22__property_checkout_invoice.sql), [V23 refunds/audit](../../../backend/src/main/resources/db/migration/V23__property_refund_audit.sql), [V24 Platform Billing](../../../backend/src/main/resources/db/migration/V24__platform_billing_foundation.sql), [V25 contract/refund](../../../backend/src/main/resources/db/migration/V25__platform_contract_refund.sql), [V26 backfill](../../../backend/src/main/resources/db/migration/V26__financial_context_backfill.sql), [V27 integrity](../../../backend/src/main/resources/db/migration/V27__financial_integrity_indexes.sql) |
| `C-STATE` | [FinancialStates.java](../../../backend/src/main/java/com/hotel/paymentprovider/domain/FinancialStates.java), [FinancialTransitionPolicy.java](../../../backend/src/main/java/com/hotel/paymentprovider/domain/FinancialTransitionPolicy.java), [FinancialTransitionPolicyTest.java](../../../backend/src/test/java/com/hotel/paymentprovider/domain/FinancialTransitionPolicyTest.java) |
| `C-GUARD` | [VndMoney.java](../../../backend/src/main/java/com/hotel/paymentprovider/domain/VndMoney.java), [FinancialAuditService.java](../../../backend/src/main/java/com/hotel/paymentprovider/audit/FinancialAuditService.java), [FinancialIdempotencyService.java](../../../backend/src/main/java/com/hotel/paymentprovider/idempotency/FinancialIdempotencyService.java), [PaymentEnvironmentGuard.java](../../../backend/src/main/java/com/hotel/paymentprovider/config/PaymentEnvironmentGuard.java) |
| `C-TENANT` | [TenantFilterInterceptor.java](../../../backend/src/main/java/com/hotel/security/TenantFilterInterceptor.java), [PermissionInterceptor.java](../../../backend/src/main/java/com/hotel/security/PermissionInterceptor.java), [FinancialPermissionIntegrationTest.java](../../../backend/src/test/java/com/hotel/security/FinancialPermissionIntegrationTest.java) |
| `C-PC-CONFIG` | [PropertyPaymentConfigurationService.java](../../../backend/src/main/java/com/hotel/propertycommerce/config/PropertyPaymentConfigurationService.java), [PropertyPaymentConfigurationController.java](../../../backend/src/main/java/com/hotel/propertycommerce/config/PropertyPaymentConfigurationController.java), [property-payment-configuration.component.ts](../../../frontend/src/app/features/management/property-payment-configuration/property-payment-configuration.component.ts) |
| `C-PC-PAY` | [PropertyPaymentAttemptService.java](../../../backend/src/main/java/com/hotel/propertycommerce/payment/PropertyPaymentAttemptService.java), [PropertyPaymentCallbackService.java](../../../backend/src/main/java/com/hotel/propertycommerce/payment/PropertyPaymentCallbackService.java), [ManualTransferConfirmationService.java](../../../backend/src/main/java/com/hotel/propertycommerce/payment/ManualTransferConfirmationService.java), [property-payment-panel.component.ts](../../../frontend/src/app/features/client/booking-checkout/property-payment-panel.component.ts) |
| `C-FOLIO` | [ReservationChargeService.java](../../../backend/src/main/java/com/hotel/propertycommerce/folio/ReservationChargeService.java), [SurchargeService.java](../../../backend/src/main/java/com/hotel/propertycommerce/folio/SurchargeService.java), [FolioCalculationService.java](../../../backend/src/main/java/com/hotel/propertycommerce/checkout/FolioCalculationService.java), [ReservationService.java](../../../backend/src/main/java/com/hotel/services/ReservationService.java) |
| `C-INVOICE` | [InvoiceFinalizationService.java](../../../backend/src/main/java/com/hotel/propertycommerce/invoice/InvoiceFinalizationService.java), [CreditNoteService.java](../../../backend/src/main/java/com/hotel/propertycommerce/invoice/CreditNoteService.java), [PropertyInvoiceController.java](../../../backend/src/main/java/com/hotel/propertycommerce/invoice/PropertyInvoiceController.java) |
| `C-PB` | [PlatformBillingController.java](../../../backend/src/main/java/com/hotel/platformbilling/PlatformBillingController.java), [PlatformPaymentCallbackService.java](../../../backend/src/main/java/com/hotel/platformbilling/payment/PlatformPaymentCallbackService.java), [SubscriptionApplicationService.java](../../../backend/src/main/java/com/hotel/platformbilling/subscription/SubscriptionApplicationService.java), [SubscriptionPolicyService.java](../../../backend/src/main/java/com/hotel/platformbilling/subscription/SubscriptionPolicyService.java) |
| `C-REFUND` | [PropertyRefundService.java](../../../backend/src/main/java/com/hotel/propertycommerce/refund/PropertyRefundService.java), [PlatformRefundService.java](../../../backend/src/main/java/com/hotel/platformbilling/refund/PlatformRefundService.java), [FinancialRefundConcurrencyIntegrationTest.java](../../../backend/src/test/java/com/hotel/integration/FinancialRefundConcurrencyIntegrationTest.java) |
| `C-REPORT` | [PropertyRevenueService.java](../../../backend/src/main/java/com/hotel/propertycommerce/reporting/PropertyRevenueService.java), [PlatformRevenueService.java](../../../backend/src/main/java/com/hotel/platformbilling/reporting/PlatformRevenueService.java), [RevenueExportService.java](../../../backend/src/main/java/com/hotel/paymentprovider/reporting/RevenueExportService.java), [FinancialReconciliationService.java](../../../backend/src/main/java/com/hotel/paymentprovider/reporting/FinancialReconciliationService.java) |
| `C-API-UX` | [financial-request.interceptor.ts](../../../frontend/src/app/core/interceptors/financial-request.interceptor.ts), [property-payment.service.ts](../../../frontend/src/app/core/services/property-payment.service.ts), [platform-billing.service.ts](../../../frontend/src/app/core/services/platform-billing.service.ts), [revenue-report.service.ts](../../../frontend/src/app/core/services/revenue-report.service.ts) |

### Document IDs

| ID | Artifact |
|---|---|
| `D-CAP` | [Payment capability matrix](../financial/PAYMENT_CAPABILITY_MATRIX.md) |
| `D-PC` | [Property payment audit](../financial/PROPERTY_PAYMENT_AUDIT.md) |
| `D-PB` | [Platform billing audit](../financial/PLATFORM_BILLING_AUDIT.md) |
| `D-REC` | [Property reconciliation](../financial/PROPERTY_REVENUE_RECONCILIATION.md), [Platform reconciliation](../financial/PLATFORM_REVENUE_RECONCILIATION.md) |
| `D-INV` | [Master function inventory](MASTER_FUNCTION_INVENTORY.md), [Full traceability matrix](FULL_SYSTEM_TRACEABILITY_MATRIX.md), [Convergence report](CONVERGENCE_REPORT.md) |
| `D-TEST` | [Full-system test matrix](FULL_SYSTEM_TEST_MATRIX.md), [Error expectation catalog](FULL_SYSTEM_ERROR_EXPECTATION_CATALOG.md) |
| `D-MANUAL` | [Manual test guide](../../testing/FULL_SYSTEM_MANUAL_TEST_GUIDE.md), [Sandbox guide](../../testing/SANDBOX_CONFIGURATION_GUIDE.md), [Production readiness](../../testing/PRODUCTION_READINESS_CHECKLIST.md) |

### Executable evidence IDs

| ID | Artifact |
|---|---|
| `E-MIG` | [SQL Server migration validation](../../testing/evidence/007/foundation/sqlserver-migration-validation.md) |
| `E-PC-ATTEMPT` | [Attempt creation](../../testing/evidence/007/property-commerce/payment-attempt-creation.md), [deposit integration](../../testing/evidence/007/property-commerce/booking-deposit-integration.md), [financial summary](../../testing/evidence/007/property-commerce/booking-financial-summary.md) |
| `E-PC-CALLBACK` | [Provider contracts](../../testing/evidence/007/property-commerce/property-provider-contracts.md), [callback concurrency](../../testing/evidence/007/property-commerce/property-callback-concurrency.md), [manual confirmation](../../testing/evidence/007/property-commerce/manual-transfer-confirmation.md) |
| `E-FOLIO` | [Property folio and checkout journey](../../testing/evidence/007/property-commerce/property-folio.md), [atomic SQL Server checkout](../../testing/evidence/007/remediation/T211-atomic-checkout-sqlserver.md) |
| `E-PB` | [Order creation](../../testing/evidence/007/platform-billing/subscription-order-creation.md), [provider contract](../../testing/evidence/007/platform-billing/platform-provider-contract.md), [callback concurrency](../../testing/evidence/007/platform-billing/platform-callback-concurrency.md), [subscription application](../../testing/evidence/007/platform-billing/subscription-application.md) |
| `E-POLICY` | [Subscription policy blockers](../../testing/evidence/007/platform-billing/subscription-policy-blockers.md), [production readiness](../../testing/PRODUCTION_READINESS_CHECKLIST.md) |
| `E-REFUND` | [Refund service tests](../../testing/evidence/007/refunds/refund-service-tests.md), [refund concurrency](../../testing/evidence/007/refunds/refund-concurrency-integration.md), [refund security](../../testing/evidence/007/refunds/refund-security-integration.md), [refund Playwright](../../testing/evidence/007/refunds/refund-playwright.md) |
| `E-REPORT` | [Property DB reconciliation](../../testing/evidence/007/reporting/property-revenue-database-reconciliation.md), [Platform DB reconciliation](../../testing/evidence/007/reporting/platform-revenue-database-reconciliation.md), [export reconciliation](../../testing/evidence/007/reporting/revenue-export-reconciliation.md), [reporting Playwright](../../testing/evidence/007/reporting/financial-reporting-playwright.md) |
| `E-ENTITLE` | [Entitlement source bridge](../../testing/evidence/007/remediation/T197-entitlement-source-bridge.md), [property entitlement gates](../../testing/evidence/007/remediation/T198-property-entitlement-gates.md) |
| `E-SEC` | [Operations tenant/feature gate](../../testing/evidence/007/remediation/T186-operations-http-tenant-feature-gate.md), [stable errors](../../testing/evidence/007/remediation/T189-stable-error-envelope.md), [audit log](../../testing/evidence/007/remediation/T213-operational-audit-log.md) |

## Functional requirements

| Requirement | Task IDs | Code/docs/evidence IDs | Status | Cross-check and remaining gap |
|---|---|---|---|---|
| `FR-001` Separate Property Commerce and Platform Billing contexts | T009, T014, T017, T019, T129, T171-T172 | `C-MIG`, `C-PB`, `C-REPORT`, `D-CAP`, `E-MIG`, `E-REPORT` | VERIFIED | Separate property/system ledgers and report queries are database-backed; API/architecture document refresh remains open but does not merge the runtime contexts. |
| `FR-002` Classify Property Commerce financial events | T014, T052, T070-T073, T112 | `C-MIG`, `C-PC-PAY`, `C-FOLIO`, `C-REFUND`, `E-PC-ATTEMPT`, `E-FOLIO` | VERIFIED | Property transaction and charge classifications are persisted and exercised by payment/folio evidence. |
| `FR-003` Classify Platform Billing financial events | T017-T018, T092, T113, T129 | `C-MIG`, `C-PB`, `C-REFUND`, `C-REPORT`, `E-PB`, `E-REPORT` | VERIFIED | Purchase/refund/credit types are isolated in the platform ledger and report fixtures. |
| `FR-004` Full payment lifecycle and validated transitions | T014, T024-T025, T035, T052, T067, T172 | `C-STATE`, `C-MIG`, `C-PC-PAY`, `E-PC-CALLBACK`, `E-REFUND` | PARTIAL | Focused paths are executed, but no current evidence enumerates every allowed and disallowed transition across all payment types. |
| `FR-005` Separate booking financial lifecycle | T024-T025, T035, T053 | `C-STATE`, `C-PC-PAY`, `E-PC-ATTEMPT`, `E-FOLIO` | PARTIAL | Summary calculation is exercised; exhaustive booking-financial transition coverage is not attached. |
| `FR-006` Audit every material transition | T016, T025, T030, T056, T172, T213 | `C-STATE`, `C-GUARD`, `C-PC-PAY`, `E-PC-CALLBACK`, `E-SEC` | PARTIAL | Callback/manual/audit branches have evidence, but universal transition coverage and final redaction review are still open. |
| `FR-007` Server validation for every amount-changing operation | T023, T034, T051, T065, T210 | `C-GUARD`, `C-PC-PAY`, `C-FOLIO`, `E-PC-ATTEMPT`, `E-FOLIO` | PARTIAL | Deposit/payment/checkout paths are covered; a final cross-module validation sweep is absent. |
| `FR-008` Tenant-owned property payment configuration | T014, T037-T046 | `C-MIG`, `C-PC-CONFIG`, `D-PC`, `E-PC-ATTEMPT` | PARTIAL | Model/service/UI exist, but no final property-specific configuration journey is tied to the current worktree. |
| `FR-009` Secret and sensitive-identifier protection | T030, T033-T034, T039, T094, T100, T106, T168 | `C-GUARD`, `C-PC-CONFIG`, `C-PB`, `D-MANUAL`, `E-POLICY` | PARTIAL | Masking/fail-closed tests exist; T168 final logs/exports/screenshots/privacy review is open. |
| `FR-010` Production fail-closed and no sandbox fallback | T033, T039, T044, T055, T066, T094, T100, T106-T108, T156-T157, T167, T170, T175 | `C-GUARD`, `C-PC-CONFIG`, `C-PB`, `D-MANUAL`, `E-POLICY` | VERIFIED | Guard/readiness tests keep production disabled and simulator-labelled; production enablement remains a separate approval gate. |
| `FR-011` Principal-derived property scope and authorization | T026, T038, T040, T046, T058, T063, T086, T120, T135 | `C-TENANT`, `C-PC-CONFIG`, `C-PC-PAY`, `C-REFUND`, `E-PC-CALLBACK`, `E-REFUND`, `E-REPORT` | PARTIAL | Financial endpoint isolation is tested in several slices; no single final suite covers every payment/invoice/refund/report operation. |
| `FR-012` Complete manual transfer instructions | T054, T058, T060, T064, T066 | `C-PC-PAY`, `C-API-UX`, `E-PC-ATTEMPT` | VERIFIED | Exact amount, receiver snapshot, unique content, reference and expiry are exercised; QR has manual fallback text. |
| `FR-013` Manual transfer stays pending until authentic confirmation | T057-T058, T063, T066 | `C-PC-PAY`, `E-PC-CALLBACK` | VERIFIED | Permission, self-confirm denial, tenant denial, audit and replay behavior have executable evidence. |
| `FR-014` Payment flow idempotency | T020, T031, T036, T054, T056, T058, T064-T066, T205 | `C-GUARD`, `C-PC-PAY`, `C-API-UX`, `E-PC-CALLBACK`, `E-PB` | VERIFIED | Persisted keys/payload hashes and concurrent callback replays produce one financial effect. |
| `FR-015` Server-owned deposit policy snapshot | T050-T051, T054, T064 | `C-PC-PAY`, `E-PC-ATTEMPT` | VERIFIED | Fixed/percentage calculation and snapshot persistence have focused evidence. |
| `FR-016` Multiple immutable successful payments/methods | T052-T053, T064, T091 | `C-PC-PAY`, `C-FOLIO`, `E-PC-ATTEMPT`, `E-FOLIO` | VERIFIED | Multi-payment checkout journey passes and ledger records remain separate. |
| `FR-017` Immutable service charge snapshots | T015, T070-T071, T081, T087-T088, T209 | `C-MIG`, `C-FOLIO`, `E-FOLIO` | VERIFIED | Service identity, price, tax, actor and usage snapshots are persisted and used in the checkout journey. |
| `FR-018` Typed surcharges and append-only corrections | T015, T070, T072, T078, T081, T087-T088 | `C-MIG`, `C-FOLIO`, `C-INVOICE`, `E-FOLIO` | PARTIAL | Implementation/tests exist, but no standalone executed evidence proves every surcharge permission/correction branch. |
| `FR-019` Authoritative checkout recomputation | T015, T073-T074, T081-T082, T087-T088 | `C-FOLIO`, `C-INVOICE`, `E-FOLIO` | VERIFIED | Folio and browser evidence cover authoritative room/service/surcharge/tax/discount/payment/refund/balance calculation. |
| `FR-020` Under/overpayment and authorized debt override | T015, T074-T075, T079, T081, T083, T087-T090 | `C-FOLIO`, `C-INVOICE`, `E-FOLIO` | PARTIAL | Normal settlement is executed; complete underpayment/overpayment/override permission evidence is narrower than the requirement. |
| `FR-021` Atomic checkout and exactly-once operations | T015, T077, T079-T081, T084, T091, T211 | `C-FOLIO`, `C-INVOICE`, `E-FOLIO` | VERIFIED | Real SQL Server failure injection proves rollback and duplicate/restart-safe invoice/room/housekeeping effects. |
| `FR-022` Immutable final invoice and corrections | T015, T076-T078, T081, T085 | `C-MIG`, `C-INVOICE`, `E-FOLIO` | PARTIAL | Finalization is exercised; dedicated immutability/credit-note evidence is not separately attached to the current worktree. |
| `FR-023` Authorized invoice view/export/email | T015, T081, T086-T090, T323 | `C-INVOICE`, `C-TENANT`, `E-FOLIO` | PARTIAL | View/PDF UI exists; real email delivery and complete customer/staff access evidence remain incomplete. |
| `FR-024` Refund original payment and balance bound | T016, T112, T114, T117-T122 | `C-MIG`, `C-REFUND`, `E-REFUND` | VERIFIED | Sequential/concurrent fixtures preserve the debit and reject cumulative refunds above balance. |
| `FR-025` Idempotent, transition-controlled refund attempts | T016, T031, T112-T114, T116-T122 | `C-GUARD`, `C-REFUND`, `E-REFUND` | VERIFIED | Request replay, provider effect, reason/actor and exactly-once behavior have executable evidence. |
| `FR-026` Separate refund/debt/manual permissions and audit | T016, T028-T029, T057, T063, T072, T075, T078, T114, T117, T120-T122 | `C-TENANT`, `C-PC-PAY`, `C-FOLIO`, `C-REFUND`, `E-PC-CALLBACK`, `E-REFUND` | PARTIAL | Manual/refund permissions are executed; debt-override and every adjustment permission branch are not consolidated. |
| `FR-027` Backend-owned subscription order snapshot | T017, T092-T093, T097, T100, T105-T106 | `C-MIG`, `C-PB`, `E-PB` | VERIFIED | Order price/period/duration/features and expiry are server-snapshotted. |
| `FR-028` System-owned merchant configuration | T017, T094, T100, T104, T106 | `C-MIG`, `C-PB`, `E-PB` | VERIFIED | Provider contract binds to the system merchant and separation tests reject property merchant reuse. |
| `FR-029` Only authoritative platform success changes entitlement | T095-T100, T103, T107, T109-T110, T197 | `C-PB`, `E-PB`, `E-ENTITLE` | VERIFIED | Valid success applies once; failed/tampered/expired paths apply zero entitlement effects. |
| `FR-030` Replay/concurrent callbacks do not duplicate subscription effects | T017, T020, T031, T095-T096, T103, T107 | `C-GUARD`, `C-PB`, `E-PB` | VERIFIED | Concurrent fixture asserts one ledger, contract, entitlement and history row. |
| `FR-031` Complete subscription lifecycle/history | T018, T024, T092, T096-T100, T105-T106, T243 | `C-STATE`, `C-PB`, `E-PB`, `E-POLICY` | PARTIAL | Purchase/renewal/upgrade/history exist; explicit expiry/revoke and approved downgrade lifecycle remain open. |
| `FR-032` Explicit proration/credit policy or truthful block | T018, T092, T098-T100, T105-T106, T108, T110, T115 | `C-PB`, `E-POLICY` | BLOCKED_EXTERNAL | Unsupported downgrade/proration is correctly blocked, but completion needs an approved versioned product policy. |
| `FR-033` Platform refund updates platform lifecycle only | T018, T092, T113, T115, T117, T121, T123-T124 | `C-MIG`, `C-PB`, `C-REFUND`, `E-REFUND` | PARTIAL | Context isolation and UI/service paths exist; entitlement consequences remain policy-gated. |
| `FR-034` Property revenue report dimensions | T125-T128, T136-T137, T167, T334 | `C-REPORT`, `D-REC`, `E-REPORT` | VERIFIED | Database fixture reconciles property gross/refund/net and scoped rows; dashboard journey passes. |
| `FR-035` Platform revenue report dimensions | T125, T129, T136, T138, T167, T335 | `C-REPORT`, `D-REC`, `E-REPORT` | VERIFIED | Database fixture covers purchase/refund/credit/net/plan filtering in system scope. |
| `FR-036` Distinguish cash, invoiced and net revenue | T125-T127, T129, T136-T138, T167 | `C-REPORT`, `D-REC`, `E-REPORT` | VERIFIED | Shared result model and DB assertions exclude unsuccessful transactions from collected totals. |
| `FR-037` Export parity with report filters/rows/totals | T125, T128, T130, T136, T138-T139, T167, T334-T336 | `C-REPORT`, `E-REPORT` | VERIFIED | CSV/XLSX/PDF file-level tests compare filters, rows, totals and checksum. |
| `FR-038` Automated authoritative reconciliation | T125, T127, T131, T140, T162, T167 | `C-REPORT`, `D-REC`, `E-REPORT` | PARTIAL | Focused DB reconciliation passes; T162 final-database/export reconciliation is open. |
| `FR-039` Ownership/filtering for property entities; system scope for platform | T014, T017, T019-T020, T026-T027, T037-T038, T052 | `C-MIG`, `C-TENANT`, `C-PC-CONFIG`, `C-PC-PAY`, `E-SEC`, `E-REPORT` | PARTIAL | Financial filters and context separation have targeted evidence; final full-entity tenant sweep is not complete. |
| `FR-040` Role/action authorization for financial resources | T028-T029, T047, T063, T086, T104, T120, T135, T201 | `C-TENANT`, `E-PC-CALLBACK`, `E-REFUND`, `E-REPORT`, `E-SEC` | PARTIAL | Several denial matrices pass; no final all-role/all-action suite is attached. |
| `FR-041` Callback verification and abuse controls | T020, T032-T034, T055-T061, T095, T102, T116, T156, T169 | `C-GUARD`, `C-PC-PAY`, `C-PB`, `E-PC-CALLBACK`, `E-PB` | PARTIAL | Signature/merchant/amount/currency/reference/replay checks and T169 polling/callback abuse focused suite pass; broader final all-provider/security coverage remains open. |
| `FR-042` Safe additive migration/backfill/recovery | T013-T022, T059, T157-T158 | `C-MIG`, `D-MANUAL`, `E-MIG` | PARTIAL | V21-V29 clean/upgrade fixtures pass, but the evidence explicitly records the historical V1 empty-database bootstrap gap and T158 is open. |
| `FR-043` Master inventory maps reachable features end-to-end | T009, T049, T111, T141-T148, T152 | `D-CAP`, `D-PC`, `D-PB`, `D-INV` | PARTIAL | Inventory/traceability exist, but final audit T152 and final-worktree freshness are open. |
| `FR-044` Every non-complete inventory item has a task/blocker | T049, T111, T141-T151, T184-T345 | `D-INV`, `D-MANUAL`, `E-POLICY` | VERIFIED | Convergence generated explicit remediation tasks and retained external policy/provider blockers; execution of those tasks is separately open under T151. |
| `FR-045` Final clean database and mandatory final-worktree suite | T152-T153, T158-T162 | `D-TEST`, `E-MIG`, `E-REPORT` | GAP | T158-T162 are open; no `docs/testing/evidence/007/final/` clean-migration/backend/frontend/Playwright/reconciliation evidence exists. |
| `FR-046` Manual guide and stable error expectations | T034-T036, T045, T067, T146, T153-T155, T164-T166, T171 | `C-API-UX`, `D-TEST`, `D-MANUAL`, `E-SEC` | PARTIAL | Error catalog/test matrix exist; T155 manual journey completion and T165-T166 responsive/accessibility evidence remain open. |

## Success criteria

| Criterion | Task IDs | Code/docs/evidence IDs | Status | Cross-check and remaining gap |
|---|---|---|---|---|
| `SC-001` Every financial record/report belongs to exactly one context | T009, T014, T017, T129, T132-T133 | `C-MIG`, `C-REPORT`, `D-CAP`, `E-MIG`, `E-REPORT` | VERIFIED | Property-filtered and system-scoped database fixtures are distinct and reconcile independently. |
| `SC-002` Reject all disallowed transitions and audit accepted material transitions | T025, T030, T118 | `C-STATE`, `C-GUARD`, `E-REFUND` | PARTIAL | Focused transition tests exist, but no evidence enumerates all payment, booking-financial and refund transitions together. |
| `SC-003` Replay/concurrent callbacks create one effect | T062, T068, T103, T119 | `E-PC-CALLBACK`, `E-PB`, `E-REFUND` | VERIFIED | Property, platform and refund concurrency evidence asserts one financial/entitlement effect. |
| `SC-004` Property booking shows correct deposit/instructions and cannot self-confirm | T041, T048, T060, T063, T068 | `C-PC-CONFIG`, `C-PC-PAY`, `E-PC-ATTEMPT`, `E-PC-CALLBACK` | PARTIAL | Backend instruction/self-confirm rules are executed; no current real-backend property-specific browser artifact covers the complete display criterion. |
| `SC-005` Checkout reconciliation matches to one VND | T068, T073, T082, T091 | `C-FOLIO`, `E-FOLIO` | VERIFIED | Authoritative folio and end-to-end checkout evidence cover the required line categories and balance. |
| `SC-006` Failure at checkout boundaries leaves aggregate consistent | T084, T091, T211 | `C-FOLIO`, `C-INVOICE`, `E-FOLIO` | VERIFIED | SQL Server injected failure rolls back invoice, assignment, room and housekeeping state atomically. |
| `SC-007` Refunds never exceed refundable balance under replay/concurrency | T118-T119, T124 | `C-REFUND`, `E-REFUND` | VERIFIED | Sequential, replayed and concurrent tests pass without exceeding the original debit. |
| `SC-008` Subscription success applies once; invalid callbacks apply zero | T101, T103, T108-T110, T197 | `C-PB`, `E-PB`, `E-ENTITLE` | VERIFIED | Provider and concurrent application evidence proves one success effect and zero tampered/failed effects. |
| `SC-009` Gross minus refunds/credits equals net | T132-T133, T139 | `C-REPORT`, `D-REC`, `E-REPORT` | VERIFIED | Property and platform database assertions match to one VND. |
| `SC-010` API rows/totals and Excel/PDF exports match DB | T132-T134, T139, T162 | `C-REPORT`, `E-REPORT` | PARTIAL | Focused database and file-level fixtures pass; T162 final-database reconciliation remains open. |
| `SC-011` IDOR denial and merchant-context separation | T027, T042, T063, T069, T086, T104, T110, T120, T124, T135, T139 | `C-TENANT`, `E-PC-CALLBACK`, `E-PB`, `E-REFUND`, `E-REPORT`, `E-SEC` | PARTIAL | Targeted financial denials pass, but final all-role/customer/property HTTP/browser coverage is not complete. |
| `SC-012` Production fails closed until separately approved | T033, T041, T048, T108, T157, T170, T175 | `C-GUARD`, `D-MANUAL`, `E-POLICY` | VERIFIED | Production remains disabled without readiness and simulator/sandbox cannot be labelled live. |
| `SC-013` Inventory classifies reachable surfaces and assigns every gap | T147, T149-T150, T163, T184-T345 | `D-INV` | VERIFIED | Current inventories have status/evidence/task or external blocker mapping; this does not claim the remediation backlog is complete. |
| `SC-014` All mandatory checks run on the final worktree with no required skip | T021, T159-T161 | `D-TEST`, `E-MIG` | GAP | Only scoped/historical suites exist; final backend/frontend/browser/security/tenant/concurrency/export/reconciliation evidence is absent. |
| `SC-015` Manual guide covers five journeys and all failure classes | T155, T163 | `D-MANUAL`, `D-TEST` | GAP | Guide file exists, but T155/T163 are open and no final screenshots/reset/five-journey completion evidence is published. |

## Release-significant gaps

1. `FR-045` / `SC-014`: T158-T162 final clean migration and final-worktree suites are missing. Existing scoped evidence cannot be reused as the release pass.
2. `FR-046` / `SC-015`: T155, T165 and T166 leave the five-journey manual guide, screenshots/reset steps, responsive and accessibility proof incomplete.
3. `FR-042`: V21-V29 SQL Server validation passes, but the recorded V1 empty-database bootstrap gap prevents a clean-from-zero claim.
4. `FR-032` / `FR-033`: downgrade/proration and platform-refund entitlement behavior remain policy-gated; correct blocking is verified, policy completion is `BLOCKED_EXTERNAL`.
5. `FR-041`: callback contract verification passes, but T169 rate-limit/polling abuse controls lack executable evidence.
6. `FR-009`, `FR-011`, `FR-039`, `FR-040`, `SC-011`: targeted security evidence exists, but the final privacy and all-role/all-resource sweep is not complete.

## Coverage totals

- Functional requirements: 46/46 represented.
- Success criteria: 15/15 represented.
- Total requirement IDs: 61/61 represented.
- Status counts (61 requirement rows): `VERIFIED` 32, `PARTIAL` 25, `GAP` 3, `BLOCKED_EXTERNAL` 1.

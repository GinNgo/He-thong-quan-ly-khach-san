# Production Payment Readiness Checklist

**Feature**: `007-payment-billing-completion`  
**Task**: T157  
**Requirements**: FR-010, FR-042  
**Scope**: Property Commerce and Platform Billing production payment enablement

## Status and stop gate

This checklist is a release gate, not an approval to process real money. The repository baseline is **NOT READY / PRODUCTION DISABLED**. No item below is treated as complete without fresh evidence from the final worktree and the target deployment. Do not fill a missing item with an assumption, a unit-test result, or a historical run.

The default configuration must remain:

```text
PAYMENT_PRODUCTION_ENABLED=false
PAYMENT_PRODUCTION_APPROVED=false
```

Production enablement is prohibited until the project owner records a separate approval, the deployment owner supplies the evidence listed here, and the change is executed in an approved window. `PaymentEnvironmentGuard` must fail closed when a flag, credential, merchant identity, endpoint, or approval marker is missing. It must never fall back to `SANDBOX` or `SIMULATOR`.

## How to use

1. Run the checks against the exact commit/artifact intended for release; record command, timestamp, profile, database fingerprint and evidence path.
2. Mark a gate `PASS` only when the evidence is reproducible, redacted and linked. Use `BLOCKED_EXTERNAL` when provider, vault, network or approval evidence is unavailable.
3. Any `FAIL`, `BLOCKED_EXTERNAL`, unknown secret, unresolved migration exception, failed reconciliation, missing rollback, or skipped mandatory test is a release stop.
4. Keep production flags false while collecting evidence. The checklist does not authorize changing them.

## Gate summary

| Gate | Minimum evidence | Current baseline | Release result |
|---|---|---|---|
| Scope and approval | Named owner, change ticket, approved window, explicit production decision | No production approval is present in this worktree | `BLOCKED_EXTERNAL` |
| Secrets and configuration | Vault/secret references, complete provider fields, rotation owner, redacted readiness output | `.env.example` contains placeholders; production flags are false | `BLOCKED_EXTERNAL` |
| Environment isolation | Production profile, non-sandbox endpoints, isolated database/network, no test fixtures | Not established by repository evidence | `BLOCKED_EXTERNAL` |
| Migration and data safety | Preflight, clean/upgrade runs, unresolved-exception review, backup/restore proof | Migration scripts exist; final production execution is not evidenced | `BLOCKED_EXTERNAL` |
| Provider contract | Signature/merchant/amount/currency/reference/expiry/replay checks at HTTP boundary | Deterministic adapter contracts exist; live production contract is not evidenced | `BLOCKED_EXTERNAL` |
| Financial correctness | Exactly-once callbacks, refunds, ledger and reconciliation proofs | Local/unit evidence exists; production-like evidence is not established | `BLOCKED_EXTERNAL` |
| Monitoring and abuse controls | Health, metrics, alerts, callback limits, audit/correlation dashboards and runbooks | Actuator endpoints/log correlation exist; operational alert evidence is absent | `BLOCKED_EXTERNAL` |
| Rollback and recovery | Tested disable/forward-recovery plan, restore point, provider incident runbook | Application recovery notes exist; production rehearsal is absent | `BLOCKED_EXTERNAL` |
| Final verification | Fresh backend/frontend, migration, security, tenant, concurrency, browser, export and reconciliation evidence | Final-worktree gate remains outstanding | `BLOCKED_EXTERNAL` |

## T175 fresh evidence (2026-08-03)

Capture details below are from the current worktree. The short Git fingerprint was `b2196f5` at 19:30:04 +07:00; the worktree is dirty, so this is not a release-artifact digest.

- **Flags and endpoints:** `.env.example` records `PAYMENT_DEMO_ENABLED=false`, `PAYMENT_SANDBOX_ENABLED=false`, `PAYMENT_PRODUCTION_ENABLED=false` and `PAYMENT_PRODUCTION_APPROVED=false`; provider credential fields are blank and the default VNPay endpoint is the sandbox host. `.env.local` has the same four disabled flags and is ignored by `.gitignore:5`; its local secret values were not copied here.
- **Application defaults:** `backend/src/main/resources/application.yml:60-61` defaults production enabled/approved to `false`. `PaymentEnvironmentGuard` requires production enablement, explicit approval, complete credentials, merchant identity and a non-sandbox endpoint; a production profile rejects simulator/sandbox relabeling.
- **Process environment:** A fresh PowerShell inspection found all inspected payment flags, provider credentials, `PROPERTY_PAYMENT_ENCRYPTION_KEY`, `DB_PASSWORD` and `JWT_SECRET` `UNSET`. No secret values were emitted.
- **Repository search:** No production-enable `true` assignment (`PAYMENT_PRODUCTION_ENABLED=true`, `PAYMENT_PRODUCTION_APPROVED=true` or equivalent YAML) was found. This worktree contains no verifiable vault/provider registration, production merchant, callback ingress, TLS/network, backup/restore, monitoring-drill or explicit owner-approval evidence.
- **Targeted validation:** `Set-Location backend; ./mvnw.cmd '-Dtest=PaymentEnvironmentGuardTest,ProductionPaymentSafetyIntegrationTest' test` completed with `BUILD SUCCESS` at 19:27:36 +07:00. `PaymentEnvironmentGuardTest` passed 3/3 and `ProductionPaymentSafetyIntegrationTest` passed 5/5 (8 passed, 0 failures, 0 errors, 0 skipped); reports are under `backend/target/surefire-reports/`. These tests prove fail-closed environment labeling and missing/unsafe production configuration handling; they do not prove provider, deployment or operational gates.
- **Fresh result:** `NOT READY / BLOCKED_EXTERNAL`. Keep production disabled. Provider registration and credentials, deployment/network controls, database backup/restore and migration rehearsal, monitoring/incident drills, and named owner/security/operations approvals remain unavailable for verification.

## 1. Approval and change control

- [ ] Project owner explicitly approves production payment for the named context(s): Property Commerce, Platform Billing, or both. Approval must not be inferred from a merge, deployment or configuration edit.
- [ ] Security/data owner reviews money flow, tenant boundaries, callback exposure, secret handling and audit retention.
- [ ] Operations owner accepts the provider, database, monitoring and incident runbooks and names an on-call escalation path.
- [ ] Change ticket records commit/artifact digest, target environment, provider(s), merchant identity (masked), migration set, window, approvers and rollback decision points.
- [ ] Approval evidence is stored outside source secrets and linked from the release evidence index. Never commit approval tokens, credentials or signatures.

## 2. Secrets and configuration

- [ ] All production secrets are supplied through the approved vault/secret store or encrypted deployment configuration; no secret is in Git, Docker context, Angular assets, logs, screenshots or test evidence.
- [ ] `JWT_SECRET`, `PROPERTY_PAYMENT_ENCRYPTION_KEY`, `DB_PASSWORD` and mail credentials are present in the deployment secret store with owners and rotation dates. Values are not copied into this checklist.
- [ ] The selected provider has complete server-side credentials and a merchant identity. Required fields are provider-specific: VNPay hash secret; MoMo access/secret keys; ZaloPay key2 (and key1 for outbound create/refund operations); simulator signing secret is never a production credential.
- [ ] Platform Billing resolves only a system-owned `env:PREFIX`/`env://PREFIX` secret reference. Property Commerce never reuses the platform merchant, another property merchant, or a client-supplied secret.
- [ ] Property bank account data is encrypted at rest and only masked values are returned. Secret readiness responses, API errors and `VerificationRequest.toString()` contain no secrets or full identifiers.
- [ ] `PAYMENT_PRODUCTION_ENABLED` and `PAYMENT_PRODUCTION_APPROVED` remain `false` in all non-production environments. Production values are injected only in the approved change window after every gate passes.
- [ ] Provider endpoint hosts are reviewed against the approved production allowlist. A host containing `sandbox` is a blocker; an endpoint must not be accepted merely because credentials are non-empty.

## 3. Runtime and network isolation

- [ ] `SPRING_PROFILES_ACTIVE=production` is used only by the production deployment. Simulator/sandbox profiles are never pointed at production endpoints or production callbacks.
- [ ] Production database, object storage, queues and callback ingress are isolated from local, test and E2E infrastructure; demo/E2E fixtures and public demo data are disabled.
- [ ] CORS, callback URLs, TLS certificates, DNS and provider registration match the same environment. Provider callbacks use HTTPS and are routed to the intended deployment.
- [ ] Callback routes remain provider-authenticated and rate-limited; customer JWT is not used as a substitute for signature and merchant verification.
- [ ] A deployment smoke check proves the UI labels `PRODUCTION` only for a production-ready configuration and never labels simulator/sandbox as live.

## 4. Database and migration safety

- [ ] Take and verify a restorable backup/snapshot before migration. Record backup identifier, retention and restore owner without putting database credentials in evidence.
- [ ] Run `backend/src/main/resources/db/preflight/feature007_financial_preflight.sql` against the target schema. Resolve every `CRITICAL` row; the script must not be bypassed.
- [ ] Execute the clean-database and upgrade-database paths from `specs/007-payment-billing-completion/quickstart.md` on an isolated rehearsal database. The final schema must contain equivalent Property Commerce and Platform Billing contracts.
- [ ] Review Flyway migrations `V21`-`V44` that affect financial contexts, including V21-V25 foundations, V26 backfill/exception capture, V27-V29 integrity/idempotency, V30-V32 snapshots/ownership constraints and V44 legacy reconciliation. Record applied versions and checksums.
- [ ] Confirm V26 has no unresolved rows in `financial_migration_exceptions`/`v_feature007_migration_exceptions`; ambiguous ownership or context mapping stops the release.
- [ ] Confirm duplicate and ownership preconditions are clean before unique constraints. V31/V32 failures, orphan rows, duplicate provider identities or non-VND/invalid amounts stop the release.
- [ ] Do not edit applied migrations, reset the schema, delete financial history or run destructive cleanup to make a migration pass. Use a reviewed forward migration and preserve exception/audit rows.
- [ ] Record post-migration row counts, foreign-key/index checks, Flyway history and a restore/reconciliation result. Production migration requires the separate approval gate above.

Validation command references (run only with approved isolated credentials):

```powershell
Set-Location backend
./tools/feature007-sqlserver-validation.ps1
./mvnw.cmd '-Dtest=FinancialMigrationIntegrationTest,FinancialBackfillSafetyIntegrationTest' test
```

The SQL Server helper currently exercises a disposable database and V21-V29 repeatability; it is supporting evidence, not proof that a production database is safe. Add the later migration and deployment-specific evidence before release.

## 5. Provider contract and financial invariants

- [ ] At the HTTP callback boundary, verify provider identity, server-owned merchant, exact VND amount, currency, booking/order reference, provider transaction identity, expiry and signature before any domain mutation.
- [ ] Normalize a stable provider event/replay identity. Equivalent retries return the existing result; conflicting identity reuse is rejected. Concurrent delivery creates one ledger effect, one booking settlement or one entitlement transition.
- [ ] Invalid signature, merchant, amount, currency, reference, expiry or missing credential produces a stable denial and no successful ledger, invoice allocation, refund, contract or entitlement mutation.
- [ ] Provider status polling/recovery is bounded and idempotent. A timeout leaves the attempt recoverable; it does not create a second charge/refund or revive a cancelled/expired booking.
- [ ] Property and platform ledgers reconcile independently. Validate `property gross - property refunds = property net` and `platform gross - platform refunds/credits = platform net`; exclude pending/failed/cancelled/expired attempts and allocate deposits once.
- [ ] Refunds preserve the original transaction and reject cumulative refunds above the refundable balance under sequential, replayed and concurrent requests.
- [ ] Production contract evidence uses the provider's approved test/certification process or a controlled production readiness probe that cannot settle money. Do not use a real customer payment as a smoke test.

Required deterministic contract suite before external provider work:

```powershell
Set-Location backend
./mvnw.cmd '-Dtest=PropertyProviderContractTest,PlatformProviderContractTest,PaymentProviderAdaptersTest,PaymentEnvironmentGuardTest' -DforkCount=0 test
```

These tests validate adapter behavior with synthetic fixtures. They do not satisfy the production provider gate without redacted HTTP-boundary evidence and provider registration proof.

## 6. Monitoring, audit and abuse controls

- [ ] `/actuator/health`, liveness/readiness and metrics are reachable only through the approved operations path; health output does not disclose details or secrets.
- [ ] Alerts exist and are tested for callback signature/merchant/amount/reference failures, provider timeouts, elevated pending/expired attempts, recovery backlog, duplicate/replay attempts, ledger/reconciliation mismatch, migration failure and database saturation.
- [ ] Callback ingress has provider-route rate limits, strict request-size/timeouts, TLS, and provider IP allowlisting or mTLS where supported. Authenticated status polling has bounded rate/backoff behavior.
- [ ] Logs and audit events include correlation ID, opaque attempt/order/event identifiers, environment and outcome, while omitting signatures, credentials, bank/card data, full merchant identifiers and personal payloads.
- [ ] Financial audit and reconciliation queues are retained according to the approved policy and are queryable by authorized operators without cross-property access.
- [ ] A provider outage drill proves the system leaves attempts pending/retryable, does not double-apply effects, and surfaces an actionable alert.

## 7. Rollback and forward recovery

- [ ] The rollback plan names the exact reversible switch: disable the affected provider/configuration and stop new attempts without deleting attempts, ledgers, callbacks or audit rows.
- [ ] The recovery plan covers callback timeout, provider outage, bad endpoint/credential, duplicate delivery, migration failure and reconciliation mismatch. Each path has owner, escalation, evidence and safe retry behavior.
- [ ] Database rollback is restore/forward-recovery based; do not downgrade or edit applied Flyway migrations. If a migration partially applies, stop traffic, preserve logs/backup and use the reviewed recovery migration or restore procedure.
- [ ] Provider credentials can be revoked/rotated independently. Rotation is rehearsed with no secret value written to logs or source.
- [ ] A controlled rehearsal demonstrates disabling production payment leaves existing attempts and immutable evidence readable, prevents new financial mutation, and allows safe reconciliation after provider recovery.

## 8. Final verification and sign-off

- [ ] Run the final-worktree commands from `specs/007-payment-billing-completion/quickstart.md`: backend tests/package, frontend unit/build, Playwright, clean/upgrade migration, security, tenant, concurrency, checkout rollback, refund, export and reconciliation suites.
- [ ] No P0/P1 issue, mandatory skipped test, unresolved inventory item, cross-property access, reconciliation mismatch or unreviewed migration exception remains.
- [ ] Evidence records the exact final commit/worktree fingerprint and redacted outputs under `docs/testing/evidence/007/`. Historical evidence is clearly labelled and is not substituted for a fresh run.
- [ ] Deployment smoke checks cover readiness denial, provider callback rejection, idempotent replay, refund upper bound, report totals and production-disabled behavior before any production flag change.
- [ ] Obtain final owner, security and operations sign-off. Only then may the approved operator set the production flags during the recorded window; immediately verify readiness and monitoring alerts.

## Current blockers (do not waive)

1. Production credentials, merchant identity, callback registration and approval evidence are intentionally absent from this repository.
2. Property Commerce online-provider readiness remains blocked until a property-scoped secret/vault adapter and approved sandbox/provider credentials are supplied.
3. External callback rate-limit/allowlist/mTLS and polling-abuse evidence is deployment-specific and not established by source inspection alone.
4. A final production migration rehearsal, restore proof, monitoring drill and owner approval are not recorded here.

Until these blockers are resolved with evidence, keep production disabled and classify the release `BLOCKED_EXTERNAL`.

## Source references

- `.env.example`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-e2e.yml`
- `backend/src/test/resources/application-test.yml`
- `backend/src/main/java/com/hotel/paymentprovider/config/PaymentEnvironmentGuard.java`
- `backend/src/main/java/com/hotel/platformbilling/config/PlatformMerchantCredentialResolver.java`
- `backend/src/main/java/com/hotel/platformbilling/config/PlatformPaymentConfigurationService.java`
- `backend/src/main/resources/db/preflight/feature007_financial_preflight.sql`
- `backend/src/main/resources/db/migration/`
- `specs/007-payment-billing-completion/plan.md`
- `specs/007-payment-billing-completion/quickstart.md`
- `specs/007-payment-billing-completion/contracts/financial-api-contract.md`
- `docs/audit/financial/PAYMENT_ENVIRONMENT_BASELINE.md`
- `docs/audit/financial/PROPERTY_PAYMENT_AUDIT.md`
- `docs/audit/financial/PLATFORM_BILLING_AUDIT.md`
- `docs/architecture/payment-provider-recovery.md`

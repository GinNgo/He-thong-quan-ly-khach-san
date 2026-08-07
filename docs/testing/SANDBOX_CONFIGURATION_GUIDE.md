# Sandbox and Simulator Configuration Guide

**Feature**: `007-payment-billing-completion`  
**Task**: T156  
**Requirements**: FR-010, FR-041  
**Scope**: Property Commerce and Platform Billing test payment boundaries

## Purpose and stop gate

This guide describes the payment simulator, provider sandbox variables, callback contract checks, and the rules that prevent real-money use. It is a test-readiness guide, not approval to enable production payments.

The required default posture is:

- `PAYMENT_PRODUCTION_ENABLED=false`
- `PAYMENT_PRODUCTION_APPROVED=false`
- no production merchant, bank, API secret, callback URL, or real payment instrument in local, CI, E2E, screenshots, logs, or evidence;
- no fallback from `PRODUCTION` to `SANDBOX` or `SIMULATOR`;
- no UI or evidence may label a simulator or sandbox result as live/production money.

`PaymentEnvironmentGuard` enforces the runtime stop gate. Production requires both explicit enablement and explicit approval, complete merchant credentials, and an endpoint whose host is not a sandbox host. A production Spring profile rejects simulator or sandbox mode instead of silently changing environments.

## Environment modes

| Mode | Permitted use | Network/merchant | Required gate | Current status |
|---|---|---|---|---|
| `SIMULATOR` | Local deterministic callback, unit/integration/E2E testing | Local UI/API and synthetic merchant only | `PAYMENT_DEMO_ENABLED=true` plus a test-only signing secret | Supported |
| `SANDBOX` | Provider test account and provider test endpoints | Provider sandbox only | `PAYMENT_SANDBOX_ENABLED=true`, complete sandbox credentials, registered callback, contract checks | Platform Billing can be configured; Property Commerce online-provider readiness remains externally blocked |
| `PRODUCTION` | Real provider and real money | Prohibited by this guide | Separate readiness approval, secret provisioning, monitoring, rollback, and both production flags | Disabled and out of scope |

Always set the intended mode explicitly. `.env.example` sets sandbox to `false`, while `application.yml` has a framework fallback of `true`; test operators must not rely on the fallback value.

## Core variables

Copy `.env.example` to the ignored `.env.local` for local work. Never add a populated secret to a tracked file.

| Variable | Purpose | Safe test value/rule |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Runtime profile | Use `e2e` or another non-production profile. Never use `production` for simulator/sandbox work. |
| `JWT_SECRET` | Application JWT key and legacy fallback for demo signing | Use a long disposable test value. Prefer a separate `PAYMENT_DEMO_SIGNING_SECRET`. |
| `PROPERTY_PAYMENT_ENCRYPTION_KEY` | AES-GCM key for property bank account storage | Use a dedicated disposable test key; do not reuse a production key. |
| `PAYMENT_DEMO_ENABLED` | Enables `SIMULATOR` readiness | Set `true` only while running simulator tests; default/reset is `false`. |
| `PAYMENT_DEMO_SIGNING_SECRET` | HMAC-SHA256 simulator signature key | Synthetic, non-empty, backend-only, never committed or logged. |
| `PAYMENT_DEMO_BASE_URL` | Redirect target for the Angular simulator | Local test URL such as `http://localhost:4200/payment-simulator`. |
| `PAYMENT_SANDBOX_ENABLED` | Global sandbox readiness gate | Set explicitly to `true` only with an approved provider test account. |
| `PAYMENT_PRODUCTION_ENABLED` | Global production enablement gate | Must remain `false`. |
| `PAYMENT_PRODUCTION_APPROVED` | Independent production approval gate | Must remain `false`. |
| `PAYMENT_PROVIDER_RECOVERY_ENABLED` | Provider status/refund recovery scheduler | Disable for deterministic test profiles unless the recovery path itself is under test. |
| `PAYMENT_PROVIDER_RECOVERY_SCAN_MS` | Recovery scan period | Test-only timing; default `60000`. |
| `PAYMENT_PROVIDER_RECOVERY_MINIMUM_AGE_MS` | Minimum age before recovery | Test-only timing; default `60000`. |

The backend test profile already enables the signed demo adapter and disables provider recovery. The E2E profile uses H2 fixtures and disables provider recovery, but runtime payment gates must still be supplied explicitly when a journey requires them.

## Property Commerce configuration

Property payment configuration stores the selected environment and masked method/merchant metadata, not provider secrets. The property bank account is encrypted using `PROPERTY_PAYMENT_ENCRYPTION_KEY` and only a masked value is returned.

### Simulator

Property callbacks resolve these Spring properties:

| Spring property | Environment form | Rule |
|---|---|---|
| `payment.property.simulator.merchant-id` | `PAYMENT_PROPERTY_SIMULATOR_MERCHANT_ID` | Optional synthetic merchant; code fallback is `PROPERTY-SIMULATOR`. |
| `payment.demo.signing-secret` | `PAYMENT_DEMO_SIGNING_SECRET` | Required for signed callback verification. |
| `payment.demo.base-url` | `PAYMENT_DEMO_BASE_URL` | Must point to the local simulator, not a payment provider. |

The Angular `/payment-simulator` route receives only a signed token. The client does not submit an authoritative amount, merchant, currency, or success state.

### Provider sandbox credentials

The callback credential resolver recognizes the following server-side properties. Spring relaxed environment names are shown.

| Provider | Merchant and callback secret | Sandbox endpoint variables |
|---|---|---|
| VNPay | `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET` | `VNPAY_URL`, `VNPAY_RETURN_URL` |
| MoMo | `PAYMENT_MOMO_PARTNER_CODE`, `PAYMENT_MOMO_ACCESS_KEY`, `PAYMENT_MOMO_SECRET_KEY` | `PAYMENT_MOMO_CREATE_URL`, `PAYMENT_MOMO_QUERY_URL`, `PAYMENT_MOMO_REFUND_URL`, `PAYMENT_MOMO_REFUND_QUERY_URL`, `PAYMENT_MOMO_REDIRECT_URL`, `PAYMENT_MOMO_IPN_URL`, `PAYMENT_MOMO_REQUEST_TYPE`; code endpoint fallbacks target the MoMo test gateway |
| ZaloPay | `ZALOPAY_APP_ID`, `ZALOPAY_KEY2` for callback verification; `ZALOPAY_KEY1` for create/refund operations | `ZALOPAY_CREATE_URL`, `ZALOPAY_QUERY_URL`, `ZALOPAY_REFUND_URL`, `ZALOPAY_REFUND_QUERY_URL`, `ZALOPAY_REDIRECT_URL`, `ZALOPAY_CALLBACK_URL` |

`PAYMENT_PROPERTY_SIMULATOR_MERCHANT_ID` and the `PAYMENT_MOMO_*` variables are resolved by backend code but are not currently listed in `.env.example`. Add only empty placeholders to the template in a separately scoped configuration task; keep actual values in the ignored local environment or approved secret store. Their absence is a readiness blocker, not permission to invent values.

Current stop gate: `PropertyPaymentConfigurationService` deliberately returns no credentials for VNPay, MoMo, or ZaloPay readiness outside simulator mode because a property-scoped vault/secret-reference adapter has not been implemented. Global sandbox variables allow adapter contract testing, but they do not make a tenant-owned provider configuration production-ready. Do not bypass this blocker by copying a platform or another property's merchant secret into the property aggregate.

Manual/QR transfer testing must use test bank identity and a unique transfer content. Do not scan or submit the QR through a real banking application, and do not use a real beneficiary account.

## Platform Billing configuration

Platform Billing uses a system-owned `PlatformPaymentConfiguration` and an environment secret reference. A reference must be `env:PREFIX` or `env://PREFIX`, where `PREFIX` contains only uppercase letters, digits, and underscores.

For example, a simulator configuration with `secretReference=env://PLATFORM_SIMULATOR` resolves:

```text
PLATFORM_SIMULATOR_MERCHANT_ID=synthetic-platform-merchant
PLATFORM_SIMULATOR_SIGNING_SECRET=synthetic-test-secret
PLATFORM_SIMULATOR_ENDPOINT=http://localhost:4200/payment-simulator
```

For a provider sandbox, choose an unambiguous prefix such as `PLATFORM_VNPAY_SANDBOX` and populate the applicable suffixes:

| Suffix | Used by |
|---|---|
| `_MERCHANT_ID` | All providers |
| `_ENDPOINT` | Required for non-simulator modes; must be an absolute provider sandbox URI |
| `_SIGNING_SECRET` | `SIMULATOR` |
| `_ACCESS_KEY` and `_SECRET_KEY` | MoMo |
| `_HASH_SECRET` | VNPay |
| `_KEY2` | ZaloPay callback verification |
| `_SECRET` | Generic provider clients only when their contract explicitly requires it |

Platform readiness also requires:

- a registered provider adapter (`SIMULATOR`, `VNPAY`, `MOMO`, or `ZALOPAY`);
- exactly one enabled environment for a provider;
- merchant identity and provider-required secrets;
- an endpoint for sandbox mode;
- an absolute HTTPS callback URL for non-simulator modes;
- no Platform Billing configuration that reuses a property merchant.

Production configuration mutation is explicitly rejected by `PlatformPaymentConfigurationService` pending separate readiness approval.

### Browser E2E variables

The platform subscription Playwright journeys use synthetic simulator inputs:

| Variable | Purpose |
|---|---|
| `LUXESTAY_E2E_PLATFORM_MERCHANT_ID` | Synthetic merchant written into signed simulator payloads |
| `LUXESTAY_E2E_PLATFORM_SIGNING_SECRET` | Synthetic HMAC key used only by the E2E callback generator |
| `LUXESTAY_E2E_API_URL` | Optional API base URL |
| `LUXESTAY_E2E_PLATFORM_ORDER_EXPIRY_MINUTES=1` | Optional short-expiry test switch |

The E2E merchant and signing secret must match the corresponding backend simulator configuration. They must never contain provider production values.

## Provider callback contract

Provider callback endpoints intentionally do not require a customer JWT:

- `POST /api/payment-providers/property/{provider}/callback`
- `POST /api/payment-providers/platform/{provider}/callback`
- equivalent property/platform refund callback routes

Authentication is provider evidence: the server resolves the stored attempt/configuration and server-owned credentials, then verifies the callback. A callback must not be accepted merely because the caller is logged in.

### Shared checks

Every adapter contract must prove all of the following before a sandbox is considered usable:

1. The provider name resolves through the case-insensitive registry; an unknown provider fails closed.
2. The signature is verified using a server-side secret. Missing credentials return `PROVIDER_UNAVAILABLE`.
3. Callback merchant identity exactly matches the expected property or system merchant.
4. Callback amount exactly matches the server-owned attempt amount.
5. Currency is `VND` and matches the server-owned attempt currency.
6. Provider reference exactly matches the persisted payment attempt and booking/order reference.
7. A successful callback has a usable provider transaction identity.
8. A stable provider event/replay identity is normalized and used for exactly-once effects.
9. `receivedAt >= attemptExpiresAt` is rejected with `ATTEMPT_EXPIRED`; the instant before expiry remains valid.
10. Invalid or conflicting evidence creates no successful ledger, booking settlement, subscription contract, entitlement, or history mutation.
11. Equivalent replay returns the existing effect; conflicting reuse of an event identity is rejected.
12. Signatures and credentials remain redacted from `VerificationRequest.toString()`, API responses, logs, screenshots, and evidence.

### Provider-specific signature rules

| Provider | Signature and normalization contract |
|---|---|
| Simulator | HMAC-SHA256 over a deterministic canonical query excluding `signature`; only `SUCCESS`, `SUCCEEDED`, `FAILED`, `CANCELLED`, and `EXPIRED` statuses are accepted. Payload includes merchant, reference, transaction ID, event ID, amount, currency, occurrence time, and status. |
| VNPay | HMAC-SHA512 over the canonical VNPay query excluding secure-hash fields. `vnp_Amount` is converted from the provider's x100 representation to integer VND. Merchant, currency, reference, response code, transaction status, and successful transaction number are checked. |
| MoMo | HMAC-SHA256 over the official ordered callback fields using the server-side access/secret keys. Partner code, integer VND amount, order reference, result code, and successful transaction ID are checked. |
| ZaloPay | HMAC-SHA256 with `key2` over the exact raw `data` string, with callback `type=1`. The adapter parses app ID, `app_trans_id`, `zp_trans_id`, amount, and server-owned `VND`. |

Expected stable rejection codes include `CALLBACK_SIGNATURE_INVALID`, `CALLBACK_MERCHANT_MISMATCH`, `CALLBACK_AMOUNT_MISMATCH`, `INVALID_CURRENCY`, `CALLBACK_REFERENCE_MISMATCH`, `ATTEMPT_EXPIRED`, and `PROVIDER_UNAVAILABLE`.

## Contract test commands

Run deterministic local contracts before any provider sandbox call:

```powershell
Set-Location backend
.\mvnw.cmd '-Dtest=PropertyProviderContractTest,PlatformProviderContractTest,PaymentProviderAdaptersTest,PaymentEnvironmentGuardTest' -DforkCount=0 test
```

Evidence already recorded in `docs/testing/evidence/007/` shows:

- property adapter/contract fixtures for VNPay, MoMo, ZaloPay, and simulator;
- platform system-merchant simulator contract and no-mutation negative cases;
- expiry-boundary, invalid signature, missing credential, merchant, amount, currency, and reference checks;
- no provider network request, production credential, or real-money transaction.

For an approved external sandbox, repeat the contract at the HTTP boundary with a provider test account and record only redacted request keys, provider response code, correlation ID, attempt ID, environment, and final state. Do not record the signature, full payload if it contains personal data, merchant secret, or full account identity.

## Sandbox execution checklist

### Before execution

- Confirm the active Spring profile is not `production`.
- Confirm both production variables are explicitly `false`.
- Confirm simulator/sandbox credentials are synthetic or issued by a provider test account.
- Confirm every endpoint hostname is on the approved test allowlist; stop if a live hostname appears.
- Confirm callback URLs route to an isolated non-production deployment.
- Confirm the test database, property, users, merchant, booking/order, and bank identity are disposable fixtures.
- Disable provider recovery unless retry/poll recovery is the test subject.
- Capture `git status`, profile, variable names (not values), test command, timestamp, and evidence path.

### During execution

- Use only deterministic VND fixture amounts; never use a real card, bank account, wallet, or QR scanner.
- Send callback evidence through the provider sandbox or local signed generator only.
- Test accepted, failed, expired, invalid-signature, merchant mismatch, amount mismatch, currency mismatch, reference mismatch, replay, and concurrent replay cases.
- Verify negative cases leave attempts/orders pending or failed as specified and create no successful financial effect.
- Verify property and platform merchant identities never cross contexts.

### After execution

- Set `PAYMENT_DEMO_ENABLED=false` and `PAYMENT_SANDBOX_ENABLED=false` when the environment is no longer in use.
- Revoke or rotate disposable secrets according to the provider test-account policy.
- Preserve immutable financial/audit evidence; do not delete rows to make a rerun pass.
- Redact secrets, signatures, full account identifiers, tokens, and personal data from artifacts.
- Record blockers truthfully; do not mark a sandbox contract complete from mocked/unit evidence alone.

## No-real-money rules

The following actions are prohibited under Feature 007:

- enabling either production flag or changing a production-approval timestamp;
- using a production Spring profile for simulator or sandbox tests;
- entering a real merchant secret, real bank account, real card, real wallet, or real customer payment data;
- sending even a small-value test transaction to a live endpoint;
- scanning a generated QR in a real banking/wallet application;
- pointing a sandbox callback at a production deployment or a production callback at a test deployment;
- relabelling sandbox/simulator state as live, successful production revenue, or production readiness;
- falling back to simulator/sandbox after a production validation failure;
- committing `.env.local`, provider secrets, signatures, tokens, full payloads, or full account identifiers;
- bypassing the property-scoped credential blocker by reusing the Platform Billing merchant or another property's credentials.

If any endpoint, credential, merchant, QR, account, or provider console cannot be proven non-production, stop the test and classify it `BLOCKED_EXTERNAL` until the owner/provider supplies safe sandbox evidence.

## Abuse controls and known gaps

FR-041 also requires appropriate controls for public callbacks and status polling. The callback routes are public by design and are protected by signature/binding/replay checks, database locking, and unique financial effect identities. Authenticated attempt/status reads remain subject to ownership/tenant checks.

The repository evidence reviewed for T156 does not establish an application-level callback rate limiter, provider IP allowlist/mTLS policy, request-body size dedicated to callbacks, or an explicit polling-rate policy. Before exposing an external sandbox or production-like endpoint, the deployment must add and test:

- ingress/WAF rate limits per provider route and source;
- provider IP allowlisting or mTLS where the provider supports it;
- strict request-size and timeout limits;
- burst/replay monitoring keyed by provider, merchant, reference, event ID, and correlation ID;
- bounded authenticated status polling with `429`/backoff behavior;
- alerting for repeated signature, merchant, amount, currency, and reference failures.

These controls are deployment/readiness blockers, not a reason to weaken provider verification or require a customer JWT on callbacks.

## Current external blockers

1. Property Commerce online-provider sandbox enablement needs a property-scoped vault/secret-reference adapter and approved provider test credentials.
2. External provider HTTP evidence needs provider sandbox accounts, registered callback URLs, and disposable test merchants.
3. Callback and polling abuse controls need deployment-level configuration and executable evidence.
4. Production remains disabled and requires a separate readiness task and approval; this guide does not satisfy that gate.

## Source evidence

- `.env.example`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-e2e.yml`
- `backend/src/test/resources/application-test.yml`
- `backend/src/main/java/com/hotel/paymentprovider/config/PaymentEnvironmentGuard.java`
- `backend/src/main/java/com/hotel/paymentprovider/spi/PaymentProviderAdapter.java`
- `backend/src/main/java/com/hotel/paymentprovider/adapters/`
- `backend/src/main/java/com/hotel/propertycommerce/payment/PropertyPaymentCallbackCredentialsResolver.java`
- `backend/src/main/java/com/hotel/platformbilling/config/PlatformMerchantCredentialResolver.java`
- `backend/src/main/java/com/hotel/platformbilling/config/PlatformPaymentConfigurationService.java`
- `backend/src/test/java/com/hotel/paymentprovider/PropertyProviderContractTest.java`
- `backend/src/test/java/com/hotel/paymentprovider/PlatformProviderContractTest.java`
- `docs/testing/evidence/007/property-commerce/provider-adapters.md`
- `docs/testing/evidence/007/property-commerce/property-provider-contracts.md`
- `docs/testing/evidence/007/platform-billing/platform-merchant-attempt-readiness.md`
- `docs/testing/evidence/007/platform-billing/platform-payment-callback.md`
- `docs/testing/evidence/007/platform-billing/platform-provider-contract.md`

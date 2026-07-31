# Property Payment Configuration Audit

**Feature**: `007-payment-billing-completion`  
**Scope**: T037-T049 / User Story 1  
**Validated**: 2026-07-31  
**Financial context**: Property Commerce only

## Outcome

Property payment configuration is implemented end to end for an authorized property. The management API resolves property access on the server, Hibernate filters configuration rows by `hotel_id`, encrypts bank account numbers with AES-GCM, returns masked identifiers only, validates deposit and bilingual instruction rules, and records redacted audit events.

The Angular management page is available at `/management/payment-configuration?propertyId={id}`. It preserves the property selected in the management shell, supports read-only and update permissions, exposes method-level readiness, and keeps Simulator, Sandbox, and Production labels distinct. Production remains fail-closed.

## Capability Evidence

| Capability | Status | Evidence |
|---|---|---|
| Tenant-owned configuration and methods | `COMPLETE_VERIFIED` | `PropertyPaymentConfigurationIntegrationTest`; active Hibernate filter assertion |
| Dedicated view/update permission | `COMPLETE_VERIFIED` | Backend interceptor test, Angular route guard, permission-filtered management menu |
| Bank account encryption and masking | `COMPLETE_VERIFIED` | `PropertyPaymentConfigurationServiceTest.savesEncryptedAccountAndReturnsOnlyMaskedValue`; Angular masking test |
| Deposit NONE/FIXED/PERCENTAGE validation | `COMPLETE_VERIFIED` | Backend unit tests and typed Angular validation |
| Transfer content uniqueness placeholder | `COMPLETE_VERIFIED` | `{paymentCode}` backend and UI validation |
| Vietnamese/English instructions | `COMPLETE_VERIFIED` | Backend readiness rule plus `vi.json`/`en.json` UI |
| Simulator readiness | `COMPLETE_VERIFIED` | Signed simulator path is selectable; runtime still requires `PAYMENT_DEMO_ENABLED=true` |
| Sandbox provider readiness | `BLOCKED_EXTERNAL` | Backend now refuses a ready state without property-scoped provider credentials |
| Production readiness | `BLOCKED_EXTERNAL` | `PAYMENT_PRODUCTION_ENABLED=false` and `PAYMENT_PRODUCTION_APPROVED=false`; no fallback is allowed |
| Responsive management journey | `COMPLETE_VERIFIED` | Playwright at 375 px, no horizontal overflow, mobile navigation accessibility state verified |
| Cross-property denial | `COMPLETE_VERIFIED` | Backend tenant integration test and Playwright 403 journey |

## Executed Validation

### Backend

```powershell
Set-Location backend
./mvnw.cmd '-Dtest=PropertyPaymentConfigurationServiceTest,PropertyPaymentConfigurationIntegrationTest,TenantFilterArchitectureTest' test
```

Result: 9 tests, 0 failures, 0 errors, 0 skipped.

The suite covers masking/encryption, deposit validation, fail-closed Production, Sandbox without property-scoped credentials, tenant isolation, and dedicated permission enforcement.

### Frontend

```powershell
Set-Location frontend
npm test -- --watch=false --include='src/app/features/management/property-payment-configuration/property-payment-configuration.component.spec.ts'
npm run build
npx playwright test e2e/property-payment-configuration.spec.ts --project=chromium --grep "shows masked"
```

Verified behavior:

- Loads configuration from the selected `propertyId`.
- Never writes a masked account number back as a replacement secret.
- Disables mutations when the user lacks `PROPERTY_PAYMENT_CONFIG` update permission.
- Shows Production as blocked instead of live.
- Keeps the 375 px layout free of horizontal overflow.
- Redirects an out-of-scope property request to the 403 experience.

The production Angular build succeeded after the feature implementation. A later repeat build timed out while the long-running local `ng serve` process remained active; final template compilation, seven Angular tests, and the Playwright journey still passed after the last accessibility change. A non-blocking component-style budget warning remains for this information-dense screen; responsive and accessibility regression remains tracked by Feature 007 T165-T166.

## Readiness Rules

| Environment / method | Current rule |
|---|---|
| Simulator | Available only when `PAYMENT_DEMO_ENABLED=true`; never represents real money |
| Manual/QR bank transfer | Requires bank name/code, account holder, protected account number, and transfer template |
| VNPay/MoMo/ZaloPay Sandbox | Not ready until a property-scoped vault/secret-reference adapter is implemented and sandbox credentials are supplied |
| Cash/card terminal | Treated as local methods; still subject to the selected environment and global Production gate |
| Production | Requires explicit enablement, approval, complete provider identity/secrets, and a non-sandbox endpoint; currently disabled |

## Privacy and Audit Controls

- Full bank account numbers are never returned by the API.
- Account numbers are encrypted before persistence and only the final four characters are displayed.
- Merchant references are displayed masked; provider secrets are not accepted by this UI.
- Financial audit metadata contains environment and masked account state only.
- No production credential, merchant account, real-money transaction, or production-enable change was introduced.

## Remaining External Blockers

1. Provide approved Sandbox credentials and a property-scoped secret storage/reference design for VNPay, MoMo, and ZaloPay.
2. Provide dedicated E2E owner fixtures through `LUXESTAY_E2E_OWNER_USERNAME`, `LUXESTAY_E2E_OWNER_PASSWORD`, `LUXESTAY_E2E_PROPERTY_ID`, and `LUXESTAY_E2E_OTHER_PROPERTY_ID` to run the optional real-browser tenant journey in every environment.
3. Production activation requires a separate security/readiness approval and is intentionally outside this implementation.

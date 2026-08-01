# T104 Platform Billing Security and Merchant Separation

## Scope

- Added service-boundary security tests for cross-owner Platform Billing order access.
- Verified an owner cannot create an attempt for another owner's order; the service returns `RESOURCE_NOT_FOUND` and does not resolve a merchant or persist an attempt.
- Verified an unmanaged property is rejected before the backend reads the subscription catalog or persists a platform order.
- Verified a platform payment attempt binds the system-owned platform merchant configuration and exposes only its masked merchant reference.

## Automated Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=PlatformBillingSecurityIntegrationTest' -DforkCount=0 test
```

Result on 2026-08-01:

- `PlatformBillingSecurityIntegrationTest`: 3 passed, 0 failed, 0 errors, 0 skipped.
- No property-scoped merchant configuration, client amount, owner identity or target property can bypass the Platform Billing service boundary.
- Tests use synthetic entities and mocked repositories/services only; no provider network, production credential, production database or real-money operation is used.

## Safety and Recovery

- Cross-property denial intentionally uses a not-found financial error to avoid order/owner enumeration.
- Platform merchant readiness remains separate from property payment configuration; a future integration must preserve the two repositories and authorization boundaries.
- If a regression is detected, disable the affected Platform Billing mutation route, retain existing immutable order/attempt evidence and deploy a corrected authorization boundary before retrying.

# T194 Property Operational Access Evidence

Date: 2026-08-01
Base commit: `3f5b878` (T193)
Scope: pending/draft property access gate (PROP-SUB-003)
Production credentials, production payment and real-money operations: N/A

## Decision

The backend now exposes two explicit property scopes:

- `assignedHotelIds()` / `requireAssignedHotel()` keeps an active owner or staff mapping available for setup, property selection and Platform Billing actions.
- `accessibleHotelIds()` / `requireManagedHotel()` requires both `approvalStatus=APPROVED` and `operationStatus=ACTIVE`.

Draft, pending, rejected, suspended and inactive properties cannot reach operational APIs. The stable denial contract is HTTP `409` with code `PROPERTY_NOT_OPERATIONAL` and `currentState=approval=<status>;operation=<status>`. Cross-tenant properties remain hidden as `404` through the assigned-scope guard.

## Focused Commands

```powershell
Set-Location backend
.\mvnw.cmd "-Dtest=PropertyAccessServiceTest,PropertyOperationalErrorContractTest,ManagementPortalServiceTest,PlatformBillingSecurityIntegrationTest,SubscriptionOrderServiceTest,SubscriptionPolicyServiceTest,SubscriptionRenewalServiceTest,SubscriptionUpgradeServiceTest" test

Set-Location ..\frontend
npm test -- --watch=false --include="src/app/layout/management-layout/management-layout.spec.ts" --include="src/app/features/management/dashboard/management-dashboard.component.spec.ts"
```

Results: backend 30/30 passed; frontend 6/6 passed.

The full backend `mvnw test` regression was also attempted, but the five-minute runner limit stopped it. The generated reports show pre-existing harness/configuration blockers (`JWT_SECRET` is absent in the test context and several integration fixtures expose multiple `@SpringBootConfiguration` classes); this is not treated as a pass or as a T194 failure. A standalone frontend production build was attempted under the same constrained runner and timed out before completion; the Angular focused test build completed successfully.

## Acceptance Matrix

| State | Assigned list/setup | Platform Billing | Operational APIs | UI operational links |
|---|---|---|---|---|
| `DRAFT + INACTIVE` | Allowed | Allowed | Denied with `PROPERTY_NOT_OPERATIONAL` | Hidden; setup and billing remain visible |
| `PENDING_APPROVAL + INACTIVE` | Allowed | Allowed | Denied with stable state | Hidden; approval banner shown |
| `REJECTED + INACTIVE` | Allowed for review/history | Allowed where policy permits | Denied | Hidden; rejection state shown |
| `APPROVED + SUSPENDED` | Assigned | Allowed for subscription context | Denied with stable state | Hidden; suspension state shown |
| `APPROVED + ACTIVE` | Assigned | Allowed | Allowed | Visible when permission also passes |
| Cross-tenant | Hidden/not found | Hidden/not found | Hidden/not found | Not selectable |

## Layer Coverage

- Backend service, exception contract and Platform Billing unit coverage: complete.
- Angular management layout/dashboard state coverage: complete.
- Browser/Playwright live journey: N/A for this focused service/UI gate; no provider or production operation is required.
- Database migration/backfill: N/A; T194 changes authorization over existing status fields only.

## Recovery Note

The change is additive and reversible by restoring the previous access-scope implementation; no schema or financial records are mutated. Keep pending-owner activation and approval/rejection transition work in T195 and the related PROP-SUB-005/006/008 tasks.

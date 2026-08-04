# T333 - Management property profile

## Outcome

`/management/properties` is now a distinct assigned-property list and profile editor rather than an alias of the operations dashboard. Owners and managers can edit public profile fields while controlled approval, operation, demo, ownership and subscription state remain server-owned.

## Tenant and behavior evidence

- Property detail/update begins with privacy-safe assigned-property verification.
- The update request allowlists profile fields and validates that the selected ward belongs to the selected province.
- Browser and component tests prove the payload excludes `approvalStatus`, `operationStatus` and `isDemo`.
- Backend tests prove a foreign id fails before location lookup or persistence and an allowed update preserves controlled state.
- The management layout supplies the route title `Cơ sở lưu trú`.

## Verification

| Check | Result |
|---|---|
| Focused backend compilation and `ManagementPortalServiceTest` | PASS, 6/6 |
| `management-properties.component.spec.ts` | PASS, 1/1 |
| `management-property-profile.spec.ts` | PASS, 1/1 on Chromium |
| `npm run build` | PASS |

Visual evidence: `T333-management-property-profile.png`.

No production credentials, real payments, destructive migrations or shared aggregate files were used.

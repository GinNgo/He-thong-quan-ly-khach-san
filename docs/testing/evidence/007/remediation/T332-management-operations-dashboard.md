# T332 - Management operations dashboard

## Outcome

The management dashboard now presents operational counts for the explicitly selected property and labels the owner-account property count separately. The backend verifies assignment before entitlement or operational queries, identifies whether the Platform Billing entitlement is authoritative, and returns truthful generation, scope, watermark and room-count reconciliation metadata.

## Security and reconciliation evidence

- A selected assigned property loads its own room, staff and housekeeping counts and its own entitlement reference.
- A foreign property id fails with the existing privacy-safe not-found response before entitlement, room, staff or housekeeping repositories are queried.
- Room status counts reconcile as `classified + unclassified = total`; housekeeping dirtiness remains a separate overlapping operational dimension.
- The canonical portal selector, query parameter and dashboard share one property context; the UI hides operational data while a switch is loading or denied and ignores stale responses from earlier requests.

## Verification

| Check | Result |
|---|---|
| Focused backend compilation | PASS; all main sources compiled except the independently broken `PlatformBillingController` that references missing `SubscriptionPlanDTO` / `SubscriptionCatalogService` |
| `ManagementPortalServiceTest` | PASS, 4/4 |
| Dashboard and management-layout component specs | PASS, 7/7 |
| `management-operations-dashboard.spec.ts` | PASS, 1/1 on Chromium |
| `npm run build` | PASS |

## Visual evidence

- `T332-management-dashboard-property-switch.png`: selected LuxeStay Hue context with authoritative entitlement, selected-property limits and reconciled counts.
- `T332-management-dashboard-idor-denial.png`: privacy-safe denied property switch with prior property metrics removed.

No production credential, real payment, destructive migration or aggregate audit file was used.

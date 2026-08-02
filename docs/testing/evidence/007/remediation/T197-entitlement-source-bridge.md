# T197 - Entitlement source-of-truth bridge

Date: 2026-08-02
Task: `PROP-SUB-020`
Scope: platform subscription entitlement read model and safe legacy compatibility projection.

## Implemented

- `platform_subscription_entitlements` is checked first for every selected property.
- Terminal platform rows (`EXPIRED`, `REVOKED`, `REFUNDED`) never fall back to legacy user-wide subscriptions.
- Legacy subscriptions are projected only when exactly one active owner mapping exists and that owner has exactly one active owned property.
- Projection rows are unique by `target_hotel_id`, refreshed by a source fingerprint, and audited with a stable projection identity.
- Management billing now reads `/api/platform/subscriptions/{targetHotelId}/entitlement` and displays the selected-property source/limits.

## Validation

| Layer | Command | Result |
|---|---|---|
| Backend focused tests | `./mvnw.cmd -q "-Dtest=PropertySubscriptionEntitlementServiceTest,PlatformBillingControllerTest" test` | PASS (6/6) |
| Frontend focused test | `npm test -- --watch=false --include=src/app/features/management/subscription-billing/subscription-billing.component.spec.ts` | PASS (4/4) |
| Backend compile | `./mvnw.cmd -DskipTests compile` | PASS |

## Safety notes

- The migration is additive and creates no production provider configuration or payment effect.
- Ambiguous legacy owner/property scope fails closed with `LEGACY_SCOPE_AMBIGUOUS_OR_MISSING`.
- No real-money provider or production database was used.

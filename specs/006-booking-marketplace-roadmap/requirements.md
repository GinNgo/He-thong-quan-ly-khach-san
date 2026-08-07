# Enterprise Requirements

## Objective

Move LuxeStay from a functional booking MVP toward a trustworthy marketplace experience without claiming incomplete payment, promotion, support-channel or subscription behavior as production-ready.

## Actors

| Actor | Goal | Critical boundaries |
|---|---|---|
| Public guest | Discover destinations/landmarks and compare real available stays | No fake prices, ads or unavailable inventory |
| Authenticated customer | Book, pay, cancel, receive refund and contact support | Own reservations/payments/conversations only |
| Property owner/manager | Configure inventory, promotions, support channels and subscription | Tenant-scoped data and feature entitlements |
| Support agent | Handle assigned internal/social conversations | Only authorized tenant/queue, auditable actions |
| System administrator | Govern plans, campaigns, locations and provider health | Explicit permissions, no secret exposure |
| Payment/social provider | Deliver signed callbacks/webhooks | Signature, expiry, replay and amount/event validation |

## Scope

### In Scope

- Public header/search/date responsive correction.
- Landmark persistence, suggestion and nearby property search.
- Repeatable nationwide landmark generation, source attribution, administrative compatibility mapping, quality quarantine and safe refresh.
- Promotion, member deal and sponsored placement governance.
- VI/EN public flow and accessible Home slideshow/editorial motion.
- Reservation hold, concurrency proof, payment/refund lifecycle reporting and approved callback hardening.
- Internal chat browser completion, tenant routing and optional Facebook/Zalo adapters.
- Subscription plan DTO, feature catalog, usage display and entitlement reconciliation.
- Full unit/integration/security/browser release gates.

### Out of Scope Unless Separately Approved

- Additional settlement currencies or foreign exchange.
- Multi-room-type cart; current booking remains one `roomTypeId` plus quantity.
- Customer reviews, customer-selected add-ons and advanced dynamic revenue management.
- Production Facebook/Zalo launch without provider credentials, approval and privacy/legal sign-off.
- A real subscription online purchase path unless order/payment/activation/refund policy is approved.

## Current Capability Assessment

| Capability | Status | Required outcome |
|---|---|---|
| Header partner CTA | Exists | Remove header placement, preserve alternate owner access |
| Home search/date | Partial | Deterministic, responsive, accessible behavior with tests |
| Landmark | Search contract implemented; catalog partial | Reproducible pipeline and 77 quality-gated rows cover 46/63 provinces; complete remaining verified coordinates and browser evidence before claiming nationwide coverage |
| Promotion/VIP/ads | Missing | Server-governed pricing and transparent display |
| VI/EN | Partial assets | Real runtime locale and P1 translation coverage |
| Home motion | Partial | One accessible controlled slideshow |
| Booking concurrency | Lock exists, proof incomplete | Concurrent real-DB test and hold expiry |
| Payment/refund | Partial | Secure callback, lifecycle states and provider-truthful refund |
| Internal chat | Source/security strong, browser partial | Authenticated browser proof and tenant routing |
| Facebook/Zalo | Missing | Optional official adapters with secure configuration |
| Subscription | Partial/inconsistent | Canonical plan/features and complete gate inventory |

## Non-Functional Requirements

- **Security**: OWASP-aligned input handling; tenant isolation; no raw provider secrets in source, frontend, logs or API responses.
- **Reliability**: All financial/inventory/provider-event mutations are idempotent and transactionally safe.
- **Accessibility**: WCAG 2.2 AA target for public P1 flow; keyboard, focus, contrast, reduced motion and screen-reader names.
- **Performance**: Stable image dimensions, lazy non-critical media, bounded carousel assets and indexed landmark/radius queries.
- **Observability**: Correlation IDs and audit events for booking holds, payment/refund transitions, webhook delivery and channel configuration changes.
- **Localization**: No concatenated UI phrases that break grammar; dates/numbers follow locale while settlement remains VND.
- **Testability**: Real H2/SQL-compatible integration fixtures and real local browser services; mock objects may isolate unit logic but cannot prove E2E completion.

## Dependencies

- Feature-05 Home/Footer baseline.
- Feature-03 GAP-022/T058 callback decision and T062 authenticated chat browser fixture.
- Official Facebook/Zalo developer applications and sandbox credentials.
- Approved promotion/VIP/sponsored-placement product rules.
- Location source licensing/quality for landmark data.
- Versioned MIT travel dataset, Geofabrik/OpenStreetMap or GeoNames extracts, and a reviewed legacy-63/current-34 province alias strategy.

## Release Reporting

Each capability must be reported as `COMPLETE`, `PARTIAL`, `BLOCKED`, `MISSING` or `DEFERRED`. `COMPLETE` requires linked automated and browser evidence; external providers additionally require sandbox/provider evidence.

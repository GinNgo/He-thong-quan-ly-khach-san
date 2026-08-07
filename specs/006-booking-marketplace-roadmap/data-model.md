# Data Model

## Existing Models to Reuse

- `Location`: reuse for `LANDMARK`; retain parent province/ward, bilingual names, normalized name and coordinates.
- `Hotel`, `RoomType`, `Room`, `Reservation`, `ReservationDetail`: existing inventory and booking aggregate.
- `Payment`: preserve during migration; evolve toward typed charge/refund transactions without deleting history.
- `ChatMessage`, `UserProperty`: extend conversation context and tenant assignment.
- `SubscriptionPlan`, `PlanFeature`, `AccountSubscription`: canonical package/entitlement source.

## Proposed Additions

## Mandatory Tenant Scope

- Every property-related table below carries `hotel_id` or `property_id`, including tenant campaigns, redemptions, sponsored placements, reservation holds, payment/refund records, support conversations and support channels.
- Tenant-scoped entities use the project Hibernate `@Filter` convention so repositories cannot accidentally return another property's rows.
- Backend services resolve permitted property IDs from the authenticated principal through `PropertyAccessService`; request-body/query tenant IDs are only selectors validated against that scope.
- System-wide records use an explicit system scope while tenant-owned child rows remain filterable and auditable.

### Landmark Metadata

Initial implementation may extend `Location`. Locations are system-wide reference data and are not tenant-owned:

| Field | Type | Rule |
|---|---|---|
| `category` | string | Attraction category such as CULTURE, BEACH, NATURE, BUSINESS |
| `defaultRadiusKm` | decimal | Positive bounded radius; system default if null |
| `popularityScore` | integer | Non-negative discovery weight |
| `descriptionVi/En` | text | Optional public summary |
| `sourceProvider` | string | `CURATED_VN_TRAVEL`, `OSM`, `GEONAMES` or another approved provider |
| `sourceObjectType` | string | Provider object kind such as `DATASET_ROW`, `NODE`, `WAY`, `RELATION`, `GEONAME` |
| `sourceObjectId` | string | Stable provider identifier; unique with provider/type |
| `sourceUpdatedAt` | datetime | Provider/source version time when available |
| `lastSeenAt` | datetime | Most recent successful catalog run containing the source object |
| `dataQualityStatus` | enum | `VERIFIED`, `MATCHED`, `REVIEW_REQUIRED`, `REJECTED`, `MISSING_SOURCE` |
| `manualOverride` | boolean | Source refresh cannot overwrite curated fields when true |

Indexes: `(location_type,status,province/parent)`, normalized name, unique `(source_provider,source_object_type,source_object_id)`, and a coordinate/geospatial strategy suitable for SQL Server.

### CurrentProvinceAlias Catalog

Current and legacy provinces coexist in `Location` without changing historical foreign keys:

| Field | Type | Rule |
|---|---|---|
| `sourceCode` | string | Current rows use stable `VN34-XX`; legacy rows keep their original numeric source code |
| `officialCode` | string | Current two-digit code from the pinned 34-unit source; informational, never used as the database identity |
| `name` / `codename` | string | Current Vietnamese display name and normalized external codename |
| `legacyProvinceCodes` | string list | Non-empty list of legacy source codes; every legacy code occurs exactly once globally |

Current provinces are imported as parentless `PROVINCE` rows. The alias catalog is versioned with the application and resolved to database ids at runtime. Province filtering uses the canonical id plus all mapped legacy ids. Legacy wards remain children of legacy provinces until a separately verified ward/hotel migration exists.

### LocationImportRun / LandmarkImportIssue

`LocationImportRun` records source provider/version/checksum, started/completed timestamps, generated/imported/quarantined counts, coverage by province and final status. `LandmarkImportIssue` records the candidate source key, reason code, raw/normalized names, proposed province/coordinates, match score and review status. Raw provider payloads are retained only when license/privacy rules allow it.

### PromotionCampaign

| Field | Type | Rule |
|---|---|---|
| `id`, `code` | key/string | Code unique in configured scope |
| `ownerType`, `hotelId` | enum/FK | SYSTEM or TENANT; tenant campaign must have hotel and tenant filter |
| `nameVi`, `nameEn` | string | Required VI; EN required before bilingual publication |
| `discountType`, `discountValue`, `maxDiscount` | enum/money | PERCENT or FIXED; positive and bounded |
| `startAt`, `endAt`, `timezone` | datetime/string | End after start |
| `eligibilityJson` or normalized rules | rule set | Dates, location, room type, member tier, min spend |
| `budget`, `redemptionLimit`, `perCustomerLimit` | numeric | Optional but non-negative |
| `stackingPolicy`, `priority`, `status` | enum/int | Controlled lifecycle |

### PromotionRedemption

Links customer/reservation/campaign with quote key, amount, status and idempotency key. Unique constraint prevents repeated redemption for the same financial event.

### MembershipTier / CustomerMembership

Explicit tier code, bilingual name, rank, eligibility/benefits and effective customer assignment. Loyalty points may be an input but do not implicitly create a tier.

### SponsoredPlacement

Tenant/property, target surface/region/landmark, start/end, budget/impression/click caps, status and disclosure copy. `hotel_id` and the tenant filter are mandatory. Placement is evaluated only after property/search eligibility.

### ReservationHold

| Field | Type | Rule |
|---|---|---|
| `reservationId`, `roomTypeId`, `quantity` | FK/int | Positive quantity, same hotel |
| `holdKey` | string | Unique idempotency key |
| `status` | enum | ACTIVE, CONSUMED, RELEASED, EXPIRED |
| `expiresAt`, `releasedAt` | datetime | Explicit lifecycle |

The hold stores `hotel_id` and uses the tenant filter. Create/update occurs inside the same transaction/lock boundary as availability validation.

### PaymentTransaction

Typed `CHARGE` or `REFUND`, `hotel_id`, provider, provider reference, expected/actual amount, currency, method, status (`CREATED`, `PENDING`, `SUCCEEDED`, `FAILED`, `EXPIRED`), idempotency key, signature/session metadata and failure code. Unique provider/idempotency constraints protect replay; the tenant filter protects staff/owner queries.

### RefundRequest

Reservation/original charge, `hotel_id`, requested amount/reason, status (`REQUESTED`, `PENDING_PROVIDER`, `SUCCEEDED`, `FAILED`), provider refund reference and timestamps. Sum of successful refunds cannot exceed successful charge balance.

### SupportConversation

Customer, required tenant/property scope for property conversations, optional reservation, channel (`IN_APP`, `FACEBOOK`, `ZALO`), provider conversation id, assigned agent/team, status and last activity. Tenant conversations and messages use `hotel_id` plus the Hibernate filter. Existing messages link to conversation instead of using only receiver `0` for all routing.

### TenantSupportChannel

Tenant/property, provider, external account/page/OA id, encrypted secret reference, webhook verification metadata, status, scopes, consent and last health check. `hotel_id` and the Hibernate filter are mandatory. API DTO never returns secret values.

### LocalePreference

For guests, persist in browser storage; authenticated profiles may store locale code. Only `vi` and `en` are valid in this feature.

## Status Migration

- Reservation canonical states: `PENDING_PAYMENT`, `CONFIRMED`, `CANCELLED`, `EXPIRED`, `CHECKED_IN`, `CHECKED_OUT`, `COMPLETED`, `REJECTED`, `NO_SHOW`.
- Existing `PENDING` values require a data migration to `PENDING_PAYMENT` after compatibility checks.
- Payment/refund strings must be mapped to typed transitions while preserving historical rows.

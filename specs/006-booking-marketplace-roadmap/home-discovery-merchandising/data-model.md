# Data Model: Home Discovery and Merchandising

## Persisted Entity: SponsoredPlacement

This addendum refines the parent `SponsoredPlacement` entity rather than introducing a competing advertising model.

| Field | Type | Rules |
|---|---|---|
| `id` | Long | Primary key |
| `hotelId` | Long, nullable for platform editorial | Required for property-sponsored content; tenant-filtered |
| `placementSurface` | Enum | `HOME_PARTNER_SPOTLIGHT` for this feature |
| `placementKind` | Enum | `EDITORIAL` or `SPONSORED` |
| `status` | Enum | `DRAFT`, `SCHEDULED`, `ACTIVE`, `PAUSED`, `EXPIRED`, `REJECTED` |
| `titleVi`, `titleEn` | String | Required, bounded length |
| `descriptionVi`, `descriptionEn` | String | Optional, bounded length |
| `imageUrl` | String | Managed/authorized asset only |
| `imageAltVi`, `imageAltEn` | String | Required for meaningful imagery |
| `targetType` | Enum | `PROPERTY` or `SEARCH_COLLECTION` |
| `targetHotelId` | Long, nullable | Required for property target |
| `targetQueryJson` | JSON/text, nullable | Validated allowlist of search parameters |
| `startsAt`, `endsAt` | Instant | `endsAt > startsAt`; evaluated with application clock |
| `sortPriority` | Integer | Bounded; does not affect organic recommendation ranking |
| `impressionLimit` | Long, nullable | Optional quota; non-negative |
| `impressionCount` | Long | Server-managed, non-negative |
| audit fields | Standard | Created/updated actor and timestamps |

### State Transitions

```text
DRAFT -> SCHEDULED -> ACTIVE -> EXPIRED
  |          |           |
  `-> REJECTED          `-> PAUSED -> ACTIVE
```

- Public projection requires approved property/asset, eligible schedule, active status and remaining quota.
- `EDITORIAL` does not bypass property approval or asset validation.
- `SPONSORED` always returns a disclosure key.

## Optional Persisted Entity: PlacementEvent

Add only if impression/click evidence is included in the approved release.

| Field | Type | Rules |
|---|---|---|
| `id` | Long | Primary key |
| `eventId` | UUID/string | Unique idempotency key |
| `placementId` | Long | Foreign key |
| `eventType` | Enum | `IMPRESSION` or `CLICK` |
| `occurredAt` | Instant | Server timestamp |
| `anonymousSessionHash` | String, nullable | Rotating opaque hash; no email, account id or raw device id |

## Projection: HomeRecommendationDestination

Not persisted. Derived from current location/province data and active property supply.

| Field | Type | Meaning |
|---|---|---|
| `id` | Long | Current province/location id |
| `name` | String | Locale-aware name |
| `displayName` | String | Full display context |
| `propertyCount` | Integer | Active approved supply count |
| `selectedByDefault` | Boolean | Server/context hint only |

## Projection: HomeRecommendationItem

Not persisted. Derived from property search, availability, reviews and later the canonical quote.

| Field | Type | Meaning |
|---|---|---|
| `propertyId` | Long | Canonical property id |
| `name` | String | Property name |
| `propertyType` | String | Locale-ready type code/label |
| `provinceId`, `provinceName` | Long/String | Current province context |
| `wardName` | String | Optional local context |
| `imageUrl`, `imageAlt` | String | Managed media projection |
| `starRating` | Integer | Official property class if present |
| `reviewScore`, `reviewCount` | Decimal/Integer | Real review aggregate only |
| `availableRoomCount` | Integer | Date/guest scoped when dates are supplied |
| `pricing` | Quote summary | Current authoritative VND price; promotion fields optional until T028-T031 |
| `recommendationReason` | Enum | `SEARCH_CONTEXT`, `POPULAR_DESTINATION`, `TOP_RATED` |
| `sponsored` | Boolean | Always `false` in the organic endpoint |

## Validation and Ranking Rules

1. Only active, approved properties are eligible.
2. Current province ids expand through the existing legacy compatibility map.
3. When dates are provided, unavailable properties are excluded or explicitly marked unavailable according to one documented API mode; Home MVP should exclude them.
4. Ranking is deterministic and server-owned. Initial rule: review score descending, review count descending, property id descending as stable tie-breaker.
5. A later popularity signal may be added only with a documented time window and anti-gaming rule.
6. Sponsored placement priority never changes the organic ranking fields.

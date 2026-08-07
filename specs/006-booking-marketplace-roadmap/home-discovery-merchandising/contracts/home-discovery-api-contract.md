# Contract: Home Discovery API

Base path: `/api/public/home`

## GET `/recommendation-destinations`

Returns up to five real current destinations with active approved property supply.

### Query

- `limit`: integer, default `5`, allowed `1..8`
- `preferredProvinceId`: optional current province id from Home search state
- `locale`: optional `vi|en`; normal application locale headers may be used instead

### Response

```json
[
  {
    "id": 10146,
    "name": "An Giang",
    "displayName": "Tỉnh An Giang",
    "propertyCount": 18,
    "selectedByDefault": true
  }
]
```

### Rules

- No destination with zero eligible properties is returned.
- `selectedByDefault` appears on at most one item.
- Results use current province identities and aggregate mapped legacy property rows.

## GET `/recommendations`

Returns organic property recommendations for one selected destination.

### Query

- `provinceId`: required current province id
- `checkInDate`, `checkOutDate`: optional ISO local dates; must form a valid stay range when present
- `stayType`: optional existing stay type
- `adultCount`, `childCount`, `roomCount`: optional positive bounded counts using the existing search contract
- `limit`: integer, default `8`, allowed `1..12`

### Response

```json
{
  "destination": {
    "id": 10146,
    "displayName": "Tỉnh An Giang"
  },
  "items": [
    {
      "propertyId": 501,
      "name": "LuxeStay Riverside",
      "propertyType": "HOTEL",
      "provinceId": 10146,
      "provinceName": "An Giang",
      "wardName": "Phú Quốc",
      "imageUrl": "/assets/...",
      "imageAlt": "LuxeStay Riverside",
      "starRating": 4,
      "reviewScore": 8.7,
      "reviewCount": 126,
      "availableRoomCount": 3,
      "pricing": {
        "nightlyPrice": 500000,
        "currency": "VND"
      },
      "recommendationReason": "TOP_RATED",
      "sponsored": false
    }
  ],
  "totalAvailable": 18
}
```

### Rules

- `sponsored` is always `false` for this endpoint.
- Invalid current province ids return `400`; inaccessible/nonexistent context returns a non-disclosing `404` only where applicable.
- Price and availability are server-derived. Discount fields are absent until the canonical quote contract is complete.
- Ordering is stable for the same data and request.

## GET `/spotlights`

Returns eligible Home partner/editorial placements.

### Query

- `limit`: integer, default `6`, allowed `1..10`
- `locale`: optional `vi|en`

### Response

```json
[
  {
    "id": 7001,
    "kind": "SPONSORED",
    "title": "Khám phá kỳ nghỉ bên biển",
    "description": "Ưu đãi do đối tác cung cấp",
    "imageUrl": "/media/placements/7001.webp",
    "imageAlt": "Khu nghỉ dưỡng nhìn ra biển",
    "disclosure": "Được tài trợ",
    "target": {
      "type": "PROPERTY",
      "propertyId": 501,
      "route": "/hotel/501"
    },
    "startsAt": "2026-08-01T00:00:00Z",
    "endsAt": "2026-08-31T23:59:59Z"
  }
]
```

### Rules

- Only active, scheduled, approved, in-quota placements are returned.
- `SPONSORED` always includes a localized disclosure; `EDITORIAL` includes a distinct editorial label.
- Public target parameters are allowlisted and generated server-side; arbitrary external URLs are not returned in the first release.
- The endpoint returns an empty array when no placement is eligible. Angular must not replace it with fake content.

## Error and Cache Contract

- Public validation errors use the existing error envelope.
- Organic destinations may use a short cache because supply changes; recommendations vary by query and dates; spotlights must not be cached beyond the nearest schedule/quota boundary.
- A failure in one endpoint must not cancel or hide the other Home section.

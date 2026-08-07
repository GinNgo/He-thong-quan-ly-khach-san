# Marketplace API Contract Draft

All examples are conceptual; final DTO names may follow existing Java conventions. APIs return typed validation errors and never expose provider secrets.

## Search and Landmarks

### `GET /api/public/locations/provinces`

Returns exactly the 34 current province/city `Location` rows. Current rows have stable source codes such as `VN34-79`; legacy numeric province rows are not returned publicly but remain in storage for compatibility.

### Current province filter compatibility

`provinceId` identifies the current canonical row. The backend resolves it to a scope containing that id plus every mapped legacy province id before filtering hotels, wards, properties or landmarks. Responses display the current province id/name even when the matched hotel still stores a legacy province id.

### `GET /api/public/search/suggestions`

Existing parameters plus `provinceId?`. Response retains grouped arrays and populates:

```json
{
  "landmarks": [
    {
      "type": "LANDMARK",
      "id": 501,
      "name": "Cầu Rồng",
      "displayName": "Cầu Rồng, Đà Nẵng",
      "secondaryText": "Điểm tham quan",
      "provinceId": 900048,
      "latitude": 16.0611,
      "longitude": 108.2277,
      "defaultRadiusKm": 5
    }
  ]
}
```

### `GET /api/v1/properties/search`

Add `landmarkId?` and `radiusKm?`. Backend resolves landmark coordinates and rejects inactive/missing-coordinate landmarks. Client-supplied coordinates cannot override a supplied landmark id.

### Nationwide catalog administration

The generator is the primary reproducible interface:

```powershell
python backend/tools/landmarks/build_vietnam_landmarks.py --dry-run
python backend/tools/landmarks/build_vietnam_landmarks.py --write
```

It emits the generated landmark JSON, a coverage report and a quarantine report. If operational API endpoints are added later, they are admin-only and invoke the same service contract:

- `POST /api/admin/locations/imports/landmarks/dry-run`
- `POST /api/admin/locations/imports/landmarks`
- `GET /api/admin/locations/imports/{runId}`
- `GET /api/admin/locations/imports/{runId}/issues`

Import responses expose counts, source versions/checksums, per-province coverage and validation issues. They never accept arbitrary remote URLs from the request and never hard-delete referenced location rows.

## Pricing and Promotions

### `POST /api/public/quotes`

Request includes property/room type, dates, quantity, guests and optional coupon. Response includes base subtotal, taxes/fees, applied campaigns, member benefit, final total, currency, quote id and expiry. Search/detail may use a lighter quote summary but must call the same pricing policy.

### Admin/Tenant Campaign APIs

- `GET/POST /api/promotions`
- `GET/PUT /api/promotions/{id}`
- `POST /api/promotions/{id}/activate|pause`
- `GET/POST /api/sponsored-placements`

All tenant mutations require property scope and subscription entitlement if product policy gates them.

## Reservation Holds

### `POST /api/reservations`

Request keeps the current one-room-type-plus-quantity contract and adds a client idempotency key. Success returns reservation id, canonical status, hold expiry and quote snapshot. Sold-out returns conflict with current availability; replay returns the original reservation.

### `POST /api/reservations/{id}/cancel`

Returns reservation plus safe payment/refund lifecycle summaries. The reservation payload may include:

- `payment`: provider, expected/paid amount, canonical status (`CREATED`, `PENDING`, `SUCCEEDED`, `FAILED`, `EXPIRED`), expiry/completion time, reconciliation flag and safe failure code.
- `refunds`: ordered requests with opaque public id, amount, provider, canonical status (`REQUESTED`, `PENDING_PROVIDER`, `SUCCEEDED`, `FAILED`), request/completion time and safe failure code.

Transaction ids, callback payloads, access credentials and provider secrets are never returned by reservation history. Cancellation cannot claim refund success before the refund lifecycle succeeds.

## Payment

### Approved provider and authority contract (OQ-001)

- Initial provider scope is VNPay Sandbox, MoMo Test and ZaloPay Sandbox. Stripe is deferred.
- The server creates an expiring `PaymentSession` bound to the authenticated reservation owner, reservation, hotel, expected VND amount, provider, method and idempotency key.
- Browser return URLs are display/recovery signals only. Only a verified provider IPN/callback, or the signed internal non-production confirmation endpoint, may change financial state.
- The internal simulator accepts only a server-issued signed token. It never accepts caller-supplied reservation id, amount, method, transaction id or arbitrary status.
- Late provider success for a cancelled/expired reservation records the successful charge and enters reconciliation without reviving the reservation, consuming released inventory or awarding loyalty points.
- Provider callbacks and refund attempts are idempotent and retain provider request/reference ids for replay, query and audit.

### `POST /api/payments/sessions`

Requires an `Idempotency-Key` header and creates a server-side payment session bound to authenticated reservation ownership, amount, provider/method and the active reservation-hold expiry. Response returns the opaque session id, provider mode, canonical status, expiry and provider URL. Replaying the same owner/key/payload returns the original session; reusing it for another payload is rejected.

### Provider callbacks/webhooks

- Verify signature/token, timestamp, provider reference, expected amount/method and replay key.
- Return idempotent provider-compatible acknowledgement.
- Late success for cancelled/expired reservation creates a reconciliation record rather than direct confirmation.
- VNPay verifies `vnp_TmnCode`, `vnp_TxnRef`, `vnp_Amount`, `vnp_ResponseCode`, `vnp_TransactionStatus` and HMAC-SHA512.
- MoMo verifies `partnerCode`, `orderId`, `requestId`, `amount`, result code and HMAC-SHA256, then acknowledges promptly.
- ZaloPay verifies the raw callback `data` with HMAC-SHA256 using callback `key2`, matches `app_id`, `app_trans_id` and amount, and returns its `return_code` acknowledgement contract.

### `GET /api/payments/reservation/{id}/status`

Customer-owned/admin-authorized response includes charge status, refund state, timestamps and safe failure/recovery information.

## Support Channels

### Internal chat

Internal chat uses a tenant-scoped conversation aggregate. New messages always carry both `conversation_id` and `hotel_id`; rows created before the migration remain `legacy_unscoped` and are not guessed into tenant history.

Customer send payload:

```json
{
  "content": "I need help with my reservation",
  "hotelId": 101,
  "reservationId": 501
}
```

- `content` is required and limited to 2,000 characters.
- `reservationId`, when supplied, must belong to the authenticated customer; its hotel is authoritative.
- `hotelId`, when supplied without a reservation, must identify an approved and operational property.
- If neither selector is supplied, the backend may use the authenticated customer's latest reservation; it does not accept a caller-supplied customer identity.

Support APIs:

- `GET /api/chat/support/conversations` returns only non-closed conversations for the principal's active hotel assignments; system administrators may view all tenants.
- `GET /api/chat/support/conversations/{conversationId}` returns ordered scoped messages.
- `POST /api/chat/support/conversations/{conversationId}/assign` claims open tenant work for the authenticated support agent.
- `POST /api/chat/support/conversations/{conversationId}/escalate` returns assigned work to the tenant queue.

Support replies use `{ "conversationId": 123, "content": "..." }`. A reply to an unassigned or escalated conversation assigns it to the replying agent. Another agent cannot take over an assigned conversation unless the conversation is escalated or the actor is a system administrator. Closed conversations reject replies.

Cross-tenant list/history/reply access is hidden as not found and records a tenant-scoped `ACCESS_DENIED_*` audit event. Conversation rows use optimistic versioning for competing assignment updates.

STOMP delivery is private: customers subscribe to `/user/queue/messages`; authorized tenant agents subscribe to `/user/queue/support/messages`. The former shared `/topic/support/messages` broadcast is not part of the tenant-aware contract.

### Tenant channel configuration

- `GET /api/management/properties/{propertyId}/support-channels`
- `POST /api/management/properties/{propertyId}/support-channels/{provider}/connect`
- `POST /api/management/properties/{propertyId}/support-channels/{provider}/disconnect`
- Provider webhook endpoints under `/api/integrations/{provider}/webhook`

Configuration responses expose account label, status, scopes and health only; never access/refresh tokens.

## Subscription

### `GET /api/subscriptions/plans`

Canonical response:

```json
{
  "id": 1,
  "code": "PRO",
  "nameVi": "Chuyên nghiệp",
  "nameEn": "Professional",
  "billingType": "YEARLY",
  "price": 12000000,
  "currency": "VND",
  "isLifetime": false,
  "status": "ACTIVE",
  "features": [
    { "code": "MAX_PROPERTIES", "nameVi": "Số cơ sở", "nameEn": "Properties", "limit": 3 }
  ]
}
```

### `GET /api/subscriptions/me/usage`

Returns the effective subscription context and feature catalog used by management UI:

```json
{
  "planCode": "STANDARD",
  "subscriptionStatus": "ACTIVE",
  "startAt": "2026-07-01T00:00:00",
  "endAt": "2027-07-01T00:00:00",
  "lifetime": false,
  "limits": { "MAX_PROPERTIES": 3, "MAX_ROOMS": 100 },
  "usage": { "MAX_PROPERTIES": 1, "MAX_ROOMS": 12 },
  "features": [
    { "code": "MAX_PROPERTIES", "nameVi": "Số cơ sở", "nameEn": "Properties", "limit": 3, "usage": 1, "allowed": true }
  ]
}
```

Only active plans are returned from `/plans`. `/me` returns the authenticated account's
canonical subscription history, including expired records for authorized read-only views.
The UI uses this response rather than inferring features from plan code or price.

### Purchase

No purchase-success API is advertised until OQ-010 is approved. Contact workflow remains explicit and truthful.

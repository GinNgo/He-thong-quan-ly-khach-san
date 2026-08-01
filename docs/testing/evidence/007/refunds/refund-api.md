# T117 Refund API Boundaries

## Property Commerce

- `POST /api/property-payments/{transactionId}/refunds`
- `POST /api/property-refunds/{refundId}/approve`
- `POST /api/property-refunds/{refundId}/attempts`
- `GET /api/property-refunds/{refundId}`
- `POST /api/payment-providers/property/{provider}/refund-callback`

The request/status service authorizes the original transaction owner or property role. Approval/attempt creation requires the separate `PROPERTY_REFUND` approval permission.

## Platform Billing

- `POST /api/platform-payments/{transactionId}/refunds`
- `POST /api/platform-refunds/{refundId}/approve`
- `POST /api/platform-refunds/{refundId}/attempts`
- `GET /api/platform-refunds/{refundId}`
- `POST /api/payment-providers/platform/{provider}/refund-callback`

Platform mutations use `PLATFORM_REFUND` permissions and remain policy-blocked until an approved entitlement policy handler exists.

## Validation

`backend` compile result on 2026-08-02: BUILD SUCCESS with 401 main sources.

Provider callback routes are public only at the HTTP authentication layer; the shared SPI still verifies signature, merchant, amount, currency, reference and request ownership before any domain effect.

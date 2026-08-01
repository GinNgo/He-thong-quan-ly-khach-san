# T122 Refund UI

## Coverage

- Added the customer refund timeline/request surface at `/refunds`, with bilingual lifecycle labels, provider-safe status refresh and server-owned amount validation.
- Added the property-role approval workspace at `/admin/refunds`, protected by the `PROPERTY_REFUND` `APPROVE` route permission.
- Both surfaces show `REQUESTED`, approval, policy-blocked, provider-pending, succeeded, failed and cancelled states without promising immediate settlement.
- Responsive layouts collapse the request form and approval table for narrow screens.

## Validation

Command from `frontend/`:

```powershell
npm run build -- --configuration development
```

Result on 2026-08-02: Angular production compilation completed successfully. Existing unrelated NG8107 optional-chain warnings remain in subscription/client layout templates.

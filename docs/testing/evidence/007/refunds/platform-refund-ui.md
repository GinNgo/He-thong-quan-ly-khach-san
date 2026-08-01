# T123 Platform Refund UI

## Coverage

- Added `/admin/platform-refunds` as a system-scoped Platform Billing workspace.
- The UI separates platform transaction/order context from property refunds and exposes remaining balance, policy version and lifecycle state.
- `POLICY_BLOCKED` and unconfigured policy states disable approval/provider-attempt actions and explain the fail-closed behavior.
- Provider attempts are available only after an approved policy and `PENDING_PROVIDER` state; all mutating calls use the typed refund client.

## Validation

Command from `frontend/`:

```powershell
npm run build -- --configuration development
```

Result on 2026-08-02: Angular compilation completed successfully.

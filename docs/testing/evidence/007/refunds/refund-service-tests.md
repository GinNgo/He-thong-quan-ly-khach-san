# T118 Refund Service Unit Tests

## Coverage

- Property partial refund reserves the locked balance and returns the exact remaining VND amount.
- Excess property refund is rejected with `REFUND_EXCEEDS_BALANCE`.
- Successful property provider processing creates one immutable credit transaction and equivalent replay returns the existing effect.
- Invalid aggregate success before provider approval is rejected.
- Missing Platform Billing entitlement policy records `POLICY_BLOCKED` with no ledger/provider mutation.
- A configured policy version without a registered handler remains fail-closed with `POLICY_NOT_CONFIGURED`.

## Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=PropertyRefundServiceTest,PlatformRefundServiceTest' '-Dstyle.color=never' test
```

Result on 2026-08-02: 5 passed, 0 failed, 0 errors, 0 skipped.

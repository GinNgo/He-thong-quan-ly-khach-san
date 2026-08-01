# T114-T115 Locked Refund Services

## Property Commerce

- Locks the original immutable debit before reserving or applying a refund.
- Computes successful refund balance from credit ledger entries and reserves active requests to prevent provider over-submission.
- Uses per-property idempotency hashes for request replay and a stable ledger identity for exactly-once success.
- Supports customer/property-role authorization, separate approval, provider success/failure and append-only audit evidence.

## Platform Billing

- Locks the original platform debit and isolates all request/ledger records from Property Commerce.
- Resolves a versioned `PlatformRefundEntitlementPolicy` handler before provider approval or a successful financial effect.
- Records `POLICY_BLOCKED` when no approved policy handler is registered; no provider mutation, credit ledger or entitlement change occurs.
- If a future approved handler is registered, the platform credit and entitlement/history effect execute in the same transaction.

## Validation

Command from `backend/`:

```powershell
.\mvnw.cmd -DskipTests '-Dstyle.color=never' compile
```

Result on 2026-08-02: BUILD SUCCESS; 394 main sources compiled.

The default worktree registers no Platform Billing refund policy implementation, so subscription refunds remain fail-closed pending an explicit business decision.

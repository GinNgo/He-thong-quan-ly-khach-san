# T120 Refund Security Integration Tests

## Coverage

- A property actor without access to the source property receives `RESOURCE_NOT_FOUND`; no refund request is persisted, preventing cross-property enumeration or mutation.
- A valid Platform Billing transaction public ID is not accepted by the Property Commerce refund service; the context lookup fails closed and no property refund request is created.
- Property refund approval requires the dedicated `PROPERTY_REFUND` `APPROVE` permission. A `VIEW`-only actor receives `403 FORBIDDEN_PERMISSION`, while the approved action is admitted by the interceptor.

## Validation

Command from `backend/`:

```powershell
.\mvnw.cmd -q '-Dtest=FinancialRefundSecurityIntegrationTest' test
```

Result on 2026-08-02: 3 passed, 0 failed, 0 errors, 0 skipped.

# T116 Shared Refund Provider SPI

## Boundary

- `RefundProviderGateway` uses the existing payment-provider verification SPI for normalized callback, signature, merchant, amount, currency and reference checks.
- `RefundProviderClient` separates outbound refund dispatch from context-owned request/ledger effects.
- The simulator client is deterministic and returns a provider reference without external network access.
- Non-simulator refund providers fail closed until a provider-specific outbound refund client is registered.
- `RefundProviderOrchestrator` persists property/platform attempts separately, supports safe retries, and delegates successful/failure effects to the owning bounded context service.

## Validation

Command from `backend/`:

```powershell
.\mvnw.cmd -DskipTests '-Dstyle.color=never' compile
```

Result on 2026-08-02: BUILD SUCCESS; 398 main sources compiled.

Invalid signatures/references are rejected before attempt mutation. Production credentials and real provider calls remain outside this task.

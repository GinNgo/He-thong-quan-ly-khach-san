# T203 - Property service catalog tenant isolation

## Implemented

- Service list/create operations resolve an explicit property through `PropertyAccessService`; a missing property is inferred only when the current user has exactly one accessible operational property.
- Tenant service ownership is assigned by the server. Client attempts to change `hotelId` or create a system template are rejected.
- Reads, updates and deletes load the service without trusting the legacy single-property Hibernate filter, then enforce property access and return not-found semantics across tenants.
- System templates are shared read-only rows with no property owner. Tenant services must have an owner; entity callbacks and V39 enforce the same invariant.
- V39 replaces global service-code uniqueness with filtered uniqueness for `(hotel_id, code)` tenant rows and `code` system templates, while normalizing legacy null-owner rows into system templates.
- The admin catalog selects an authorized property before loading data, sends the property outside the mutable payload, and labels system templates as read-only.

## Verification

Executed on 2026-08-03:

```text
backend\mvnw.cmd -q "-Dtest=HotelServiceLogicImplTest" test
Result: PASS (6 tests, 0 failures, 0 errors)

frontend\npm test -- --watch=false \
  --include src/app/core/services/hotel-service.service.spec.ts \
  --include src/app/features/admin/service-management/service-management.spec.ts
Result: PASS (2 files, 3 tests)

Disposable SQL Server 2022 V39 scope validation
Result: PASS
```

The backend tests cover authorized list/create behavior, single-property inference, cross-property read/update/delete rejection and immutable system templates. The SQL Server validation proves legacy normalization, same-code reuse across two tenants, same-tenant duplicate rejection and invalid system ownership rejection.

## Residual scope

- PROP-OPS-026/T264 still owns `HOTEL_SERVICE` permission parity and HTTP allow/deny coverage.
- PROP-OPS-027/T265 still owns complete create/edit/deactivate UI workflows.
- PROP-OPS-028/T266 still owns full input validation, soft deactivation/versioning and historical catalog stability.

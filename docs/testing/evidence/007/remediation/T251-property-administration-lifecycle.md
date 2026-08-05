# T251 - Property administration lifecycle

Date: 2026-08-04
Branch: `codex/property-operations`

## Implemented behavior

- Admin create/update endpoints accept validated request DTOs instead of binding the
  `Hotel` entity. Client payloads cannot own `status`, `approvalStatus`,
  `operationStatus`, demo flags, data source, code or slug.
- Administrative create always produces a non-demo `DRAFT` / `DRAFT` / `INACTIVE`
  record with server-generated identifiers and a validated province/ward pair.
- Property profile mutations lock the property row and apply a state policy. Owner
  edits are allowed only for an active `OWNER` assignment and a draft/rejected or
  approved-active property. Pending-review and closed records are read-only.
- Submission is a service-owned transition from `DRAFT` or `REJECTED` to
  `PENDING_APPROVAL`; the controller no longer mutates a detached entity before a
  generic update.
- Legacy `DELETE /api/v1/hotels/{id}` and the explicit close endpoint now require a
  reason and transition the property to `CLOSED` without deleting the hotel row,
  owner/staff assignments, images or other historical aggregate data.
- Accepted create, admin update, owner update, submit and close operations append an
  operational audit event with actor context, reason and before/after lifecycle/profile
  snapshots in the same transaction.
- The management dashboard exposes a small owner profile editor for the currently
  selected property. Its client sends only editable fields plus a reason to
  `PUT /api/management/properties/{id}`. Admin create no longer sends server-owned
  lifecycle fields, and the admin client exposes reasoned closure.

## Authorization and tenant isolation

- Administrative create/update/close routes retain `SUPER_ADMIN` authorization and
  the service independently rejects non-system callers.
- Owner mutation resolves the authenticated user, requires an active `OWNER`
  `user_properties` row for the requested property and returns not-found for an
  unowned property before locking or mutating the hotel row.
- Focused service and HTTP cases prove positive owner edit, cross-property IDOR denial,
  pending-review rejection and absence of unauthorized repository writes.

## Transaction and rollback

- Property updates use `HotelRepository.findByIdForUpdate()` so lifecycle decisions
  and audit snapshots are based on one locked row.
- The H2 persistence test forces the audit append to fail after the `CLOSED` update is
  flushed. The service transaction rolls back and a fresh read still returns
  `status=ACTIVE` and `operationStatus=ACTIVE`.

## Verification

Backend focused command:

```text
backend\mvnw.cmd "-Dtest=HotelManagementServiceImplTest,PropertyAdministrationRollbackIntegrationTest,HotelControllerIntegrationTest,ManagementPropertyControllerHttpTest,PropertyAdministrationControllerHttpTest" test
```

Result: 14/14 passed with zero failures, errors or skipped tests, including proof that a
closed property remains retained but is no longer returned by the public detail route.

Frontend focused command:

```text
npm test -- --watch=false --include='src/app/core/services/management-api.service.spec.ts' --include='src/app/core/services/property.service.spec.ts' --include='src/app/features/admin/property-management/property-management.spec.ts' --include='src/app/features/management/dashboard/management-dashboard.component.spec.ts'
```

Result: 7/7 passed across four files. The Angular compiler emitted only the existing
unrelated optional-chain warning in `client-layout.html`.

## Migration and recovery

No migration is required. Closure reuses the existing lifecycle columns and the
append-only operational audit table; no data is deleted or backfilled. Application
rollback requires reverting the DTO/controller/service/UI changes together. Property
rows and audit evidence created while this behavior is active remain retained.

Temporary subscription source snapshots, repository compatibility methods, Maven test
includes and the Angular `core/i18n` junction used to work around parallel-base build
gaps were removed before staging.

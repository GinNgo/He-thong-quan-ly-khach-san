# T213 - General Operational/Admin Audit Log

Date: 2026-08-03

## Delivered

- Added `operational_audit_events` with explicit `TENANT`/`SYSTEM` scope, actor, reason, before/after snapshots and correlation ID.
- Added additive Flyway migration `V47__operational_audit_log.sql`, supporting indexes, scope check constraint and SQL Server `INSTEAD OF UPDATE, DELETE` append-only trigger.
- Added `GET /api/admin/audit-events` and `GET /api/admin/audit-events/export` with `AUDIT_LOG:VIEW/EXPORT`, assigned-property filtering, system-scope restriction and a 10,000-row bounded CSV export.
- Connected high-risk mutation paths for staff, role/permission, property, room, maintenance/housekeeping and reservation lifecycle changes.
- Added lazy Angular viewer routes at `/admin/audit-log` and `/management/audit-log` with filters, before/after expansion, loading/error/empty states and responsive CSV export action.

## Executable Results

| Suite | Result |
|---|---:|
| `OperationalAuditServiceTest` | 4/4 passed |
| `OperationalAuditControllerTest` | 1/1 passed |
| Affected backend regression (`UserServiceTest`, `ReservationServiceTest`, lifecycle/checkout IDOR and locking, `RolePermissionServiceTest`, `RoomServiceImplTest`, `ManagementPortalServiceTest`) | 51/51 passed |
| `audit-log.component.spec.ts` | 2/2 passed |
| `npm run build` | Passed; existing CSS/CommonJS warnings only |

## Security Notes

- Non-system actors can query only active assignments returned by `PropertyAccessService`; an explicit cross-property request returns `RESOURCE_NOT_FOUND` semantics.
- `SYSTEM` events are available only to `SUPER_ADMIN`; system audit data is never exposed through a tenant query.
- State snapshots redact credentials, tokens, signatures, account numbers and direct contact fields before persistence; CSV cells are formula-safe.
- The viewer exposes bounded page sizes and export volume. No delete/update API is provided, and SQL Server rejects direct row mutation through the append-only trigger.

## Residual Scope

Financial audit viewer (`CROSS-028`) and support conversation history viewer (`CROSS-029`) remain separate remediation tasks; this task supplies the shared operational/admin boundary without merging those bounded contexts.

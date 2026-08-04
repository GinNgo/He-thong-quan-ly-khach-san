# Property operations handoff

Branch: `codex/property-operations`  
Base: `origin/codex/ui-functional-audit-polish`  
Scope: T245-T268, excluding T262-T263 already completed by the coordinator.

## Completed task commits

| Task | Commit | Result |
|---|---|---|
| T245 | `08c3557` | Tenant-scoped staff reads |
| T246 | `8933e11` | Validated staff creation |
| T247 | `b93f59c` | Secure staff update/property move |
| T248 | `b7e23a9` | Governed custom role catalog |
| T249 | `024d804` | System-role integrity |
| T250 | `63e2080` | Role/staff audit and concurrency |
| T251 | `942f0fc` | Property administration lifecycle |
| T252 | `041dacb` | Canonical property profile |
| T253 | `0a33c50` | Tenant-safe property galleries |
| T254 | `940f5a3` | Property-owned media lifecycle |
| T255 | `69d16c0` | Amenity catalog and assignments |
| T256 | `3c0be18` | Operational policy snapshots |
| T257 | `d5da3c8` | Room-type lifecycle |
| T258 | `ce5291e` | Room-type gallery lifecycle |
| T259 | `233219a` | Physical-room CRUD/bulk operations |
| T260 | `2ae4fa0` | Physical-room gallery lifecycle |
| T261 | `e3cf6b2` | Maintenance work-order lifecycle |
| T264 | `fdd0300` | Service authorization parity |
| T265 | `d237947` | Service catalog CRUD UI |
| T266 | `5009139` | Service validation/history/soft lifecycle |
| T267 | `965a2c5` | Management action authorization matrix |
| T268 | `5929c4a` | Dashboard entitlement/usage reconciliation |

T262 and T263 were intentionally skipped because the coordinator's active-claims table records both as complete with housekeeping queue/completion evidence.

## Verification

- Every task has focused executable evidence under `docs/testing/evidence/007/remediation/`.
- Backend suites cover tenant IDOR, permission actions, validation, locking/concurrency, lifecycle transitions, snapshot stability and dashboard property isolation.
- Angular suites cover route/control visibility, mutation behavior, loading/error states, gallery/work-order/service flows and property-switch race isolation.
- SQL Server disposable-database validation covers migrations V80 through V88 introduced or extended by this branch; no production database was touched.
- Temporary subscription/public-i18n compiler compatibility sources were removed before staging.
- `git diff --check` passes and forbidden aggregate files were not modified.

## Coordinator updates required

The coordinator should mark T245-T261 and T264-T268 complete in:

- `specs/007-payment-billing-completion/tasks.md`
- `docs/audit/system/MASTER_FUNCTION_INVENTORY.md`
- `docs/audit/system/FULL_SYSTEM_TRACEABILITY_MATRIX.md`

The authoritative domain rows are already updated in `docs/audit/system/inventory/property-operations.md`; use the per-task evidence links there when merging aggregate documentation.

## Deployment notes

- Apply migrations in version order and review each evidence file's forward-recovery note.
- V88 intentionally stops on invalid legacy service catalog data rather than coercing financial values; correct those rows with a reviewed data-fix migration before retrying.
- No production credentials, production transactions, real-money calls or destructive production operations were used.

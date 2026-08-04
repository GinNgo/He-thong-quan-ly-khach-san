# T331 - Admin Work-Order Placeholder Removal

Branch: `codex/cross-cutting`

## Result

The timer-only maintenance table was removed from `/admin/dashboard`, together with the shared table component's console-only Excel/PDF controls on this route. The screen is now limited to its authoritative system reporting purpose.

Implementing maintenance work orders here would duplicate the property-operations domain and weaken tenant context. The canonical maintenance workflow remains owned by its dedicated property-scoped remediation; until that capability is available, the system dashboard no longer claims that records or exports exist.

## Scope and authorization

- Backend/API/model: N/A for this removal path; no fake work-order endpoint or data model is introduced.
- Tenant isolation: N/A on the removed surface. Future maintenance reads/mutations must derive property access from the authenticated principal.
- Export permission: N/A because the misleading controls are absent. Future exports must use a server-generated canonical export service and explicit permission.
- The system dashboard retains the T329 `REPORT:VIEW` plus system-administrator boundary.

## Validation

| Layer | Command / method | Result |
|---|---|---|
| Angular | `npm test -- --watch=false --include=src/app/features/admin/dashboard/dashboard.spec.ts` | PASS, 2/2; no maintenance heading or `app-data-table` remains and authoritative dashboard behavior is preserved |
| Browser | `PLAYWRIGHT_PORT=4296 npx playwright test e2e/admin-authoritative-dashboard.spec.ts --project=chromium --workers=1` | PASS, 4/4; the T331 case proves the table plus Excel/PDF buttons are absent |
| Production build | `npm run build` | PASS; dashboard lazy chunk decreases from 232.44 kB to 221.38 kB raw; only pre-existing unrelated warnings remain |

## Evidence artifact

- `docs/testing/evidence/007/remediation/T331-admin-dashboard-without-work-order-placeholder.png`

## Migration and recovery

- Migration/data mutation: N/A.
- Production credentials/real money: N/A.
- Rollback: revert the T331 commit. Forward recovery is preferred because rollback restores controls that neither call an API nor create a file.

## Coordinator updates

- Mark T331 complete in `specs/007-payment-billing-completion/tasks.md` after merging the task commit.
- Promote CROSS-020 in `docs/audit/system/MASTER_FUNCTION_INVENTORY.md`.
- Add T331 coverage to `docs/audit/system/FULL_SYSTEM_TRACEABILITY_MATRIX.md` for FR-044 / SC-013.

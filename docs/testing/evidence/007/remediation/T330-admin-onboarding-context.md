# T330 - Admin Onboarding Context

Branch: `codex/cross-cutting`

## Result

The hard-coded property setup percentage and implied approval workflow were removed from `/admin/dashboard`. T329 established this route as a system-wide, non-demo Platform Billing and operations dashboard restricted to system administrators, so property onboarding is not a meaningful aggregate on this screen.

Dedicated property registration, approval and management routes remain the correct owners of property-specific state and actions; T330 does not duplicate or bypass those workflows.

## Authorization and scope

- `/admin/dashboard` retains `REPORT:VIEW` route protection and backend `SUPER_ADMIN` / `ROLE_SUPER_ADMIN` enforcement.
- No property id, setup checklist, approval state or approval command is loaded by the system dashboard.
- No unauthorised or misleading approval CTA is rendered.
- Backend change: N/A; removing the inapplicable client-only placeholder avoids creating a second onboarding API or business policy.

## Validation

| Layer | Command / method | Result |
|---|---|---|
| Angular | `npm test -- --watch=false --include=src/app/features/admin/dashboard/dashboard.spec.ts` | PASS, 2/2; authoritative metrics still render and onboarding/approval text is absent |
| Browser | `PLAYWRIGHT_PORT=4295 npx playwright test e2e/admin-authoritative-dashboard.spec.ts --project=chromium --workers=1` | PASS, 3/3; the T330 case verifies the system scope and absence of setup/approval controls |
| Production build | `npm run build` | PASS; only pre-existing payment-configuration budget and STOMP/SockJS CommonJS warnings remain |

## Evidence artifact

- `docs/testing/evidence/007/remediation/T330-system-admin-dashboard-context.png`

## Migration, policy and recovery

- Migration: N/A.
- Tenant data mutation: N/A.
- Financial/onboarding policy: N/A; no policy is invented and no approval transition is changed.
- Rollback: revert the T330 commit. Forward recovery is preferred because rollback would restore a fabricated percentage with no authoritative state or permitted action.

## Coordinator updates

- Mark T330 complete in `specs/007-payment-billing-completion/tasks.md` after merging the task commit.
- Promote CROSS-019 in `docs/audit/system/MASTER_FUNCTION_INVENTORY.md`.
- Add T330 coverage to `docs/audit/system/FULL_SYSTEM_TRACEABILITY_MATRIX.md` for FR-044 / SC-013.

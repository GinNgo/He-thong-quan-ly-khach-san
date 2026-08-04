# Auth / Property Subscription Handoff

Branch: `codex/auth-property-subscription`

Base: `origin/codex/ui-functional-audit-polish`

## Task status

| Task | Status | Commit | Verification | Coordinator updates |
|---|---|---|---|---|
| T228 | COMPLETE_VERIFIED | `aeeba46` | Backend 5/5; frontend 6/6; `git diff --check` | Mark T228 complete in Feature 007 `tasks.md`; merge AUTH-020 status/evidence into the two shared aggregate inventories. |
| T229 | SKIPPED_CLAIMED | Owned by `/root/t229_auth_legal` | Active Parallel Claims checked before T228/T230 | No duplicate changes from this branch. |
| T230 | COMPLETE_VERIFIED | `a16a1bf` | Backend 7/7; frontend 4/4; `git diff --check` | Mark T230 complete in Feature 007 `tasks.md`; merge PROP-SUB-001 status/evidence into the two shared aggregate inventories. |
| T231 | COMPLETE_VERIFIED | `ac92af4` | Backend 14/14; frontend 7/7; `git diff --check` | Mark T231 complete in Feature 007 `tasks.md`; merge PROP-SUB-002 status/evidence into the two shared aggregate inventories. |
| T232 | COMPLETE_VERIFIED | `9c774a1` | Backend 33/33; frontend 6/6; `git diff --check` | Mark T232 complete in Feature 007 `tasks.md`; merge PROP-SUB-004 status/evidence into the two shared aggregate inventories. |
| T233 | COMPLETE_VERIFIED | `c188a92` | Backend compile 432 sources and 46/46 focused tests; frontend 9/9; `git diff --check` | Mark T233 complete in Feature 007 `tasks.md`; merge PROP-SUB-005 status/evidence into the two shared aggregate inventories. |
| T234 | COMPLETE_VERIFIED | `be7346b`; migration namespace fix `b93491d` | Backend compile 435 sources and 78/78 focused tests; frontend 10/10 plus development build; `git diff --check` | Mark T234 complete in Feature 007 `tasks.md`; merge PROP-SUB-006 status/evidence into the two shared aggregate inventories; preserve the branch-reserved V70 migration range. |
| T235 | COMPLETE_VERIFIED | `5945588` | Backend compile 440 sources and 98/98 regressions; frontend 15/15 plus development build; `git diff --check` | Mark T235 complete in Feature 007 `tasks.md`; merge PROP-SUB-007 status/evidence into the two shared aggregate inventories; preserve the branch-reserved V71 migration range. |
| T236 | COMPLETE_VERIFIED | `a05c10b` | Backend compile and 64/64 focused tests; frontend 38/38 plus development build; `git diff --check` | Mark T236 complete in Feature 007 `tasks.md`; merge PROP-SUB-009 status/evidence into the two shared aggregate inventories; preserve V72 and coordinate any later consolidation with the generic T324 email outbox. |
| T237 | COMPLETE_VERIFIED | `758b425`; teardown fix `c0eb1a4` | Backend compile, 27/27 focused and 11/11 clock/persistence tests; frontend 14/14 plus development build after teardown fix; `git diff --check` | Mark T237 complete in Feature 007 `tasks.md`; merge PROP-SUB-010 status/evidence into the two shared aggregate inventories; preserve V73 and record evidence upload as not approved/N/A. |
| T238 | COMPLETE_VERIFIED | `4198fb5` | Backend compile and 44/44 focused tests; frontend 8/8 plus development build; independent review findings fixed; `git diff --check` | Mark T238 complete in Feature 007 `tasks.md`; merge PROP-SUB-011 status/evidence into the two shared aggregate inventories; retain fail-closed ownership conflicts for T239/T241 and lock-order concurrency for T240. |

## Shared files intentionally not edited

- `specs/007-payment-billing-completion/tasks.md`
- `docs/audit/system/MASTER_FUNCTION_INVENTORY.md`
- `docs/audit/system/FULL_SYSTEM_TRACEABILITY_MATRIX.md`

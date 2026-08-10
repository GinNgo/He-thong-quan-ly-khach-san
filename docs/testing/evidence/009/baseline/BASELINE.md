# Feature 009 Baseline

**Captured**: 2026-08-09  
**Branch**: `codex/latest-clean-ui`

## Worktree policy

- The worktree contains extensive pre-existing tracked and untracked changes.
- Feature 009 implementation must preserve those changes and must not reset, clean or broadly rewrite overlapping files.
- Product changes are applied incrementally with targeted tests before task completion is recorded.

## Stack

- Backend: Java 21, Spring Boot 3.2.5, Maven, SQL Server/Flyway.
- Frontend: Angular 22, TypeScript 6, npm, Vitest/Angular TestBed and Playwright.
- Payment: existing Property Commerce, Platform Billing and shared VNPay provider adapters.
- Authorization: JWT request authentication, centralized bitmask permission interceptor and property access service.

## Initial observations

- Feature 007 financial bounded contexts and VNPay verification primitives already exist and should be extended.
- Existing action bits stop at `APPROVE = 32`; `TASK_EXECUTE` is not yet defined.
- Function catalog does not yet declare supported actions.
- Permission-related frontend/backend files already contain uncommitted work and require merge-safe edits.


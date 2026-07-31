# Feature 007 Evidence Index

Each evidence file records the command, UTC/local timestamp, branch, commit/worktree fingerprint, environment/profile, role/fixture, result, and artifact path. Fresh final-worktree evidence is required; historical output is never reused as final proof.

Required directories:

- `baseline/`: measurements before Feature 007 fixes.
- `foundation/`: migration, tenant, permission and provider-boundary evidence.
- `property-commerce/`: booking, checkout, invoice and property refund evidence.
- `platform-billing/`: subscription order, callback, entitlement and platform refund evidence.
- `reporting/`: report/export/reconciliation evidence.
- `final/`: clean-migration, complete test and release-gate evidence.

Sensitive values must be redacted. Never store provider secrets, full bank account identifiers, access tokens or real payment data.

# T165 Responsive Evidence

Date: 2026-08-03 (Asia/Ho_Chi_Minh)
Feature: `007-payment-billing-completion`
Result: `PARTIAL`

## Scope and method

The audit targets the new financial surfaces reachable from the current Angular router:

- Property Commerce: `/management/payment-configuration`, `/management/property-revenue`, `/admin/reservations` checkout workspace, `/admin/refunds`, `/my-invoices`, `/refunds`.
- Platform Billing: `/management/billing`, `/admin/platform-payment-configuration`, `/admin/platform-revenue`, `/admin/platform-refunds`.
- Payment state surfaces: `/payment-simulator` and `/payment-result`.

Required viewports were exercised as `320x720`, `375x812`, `768x1024`, `1024x768` and `1440x900`. The isolated frontend was served at `http://127.0.0.1:4217` from the existing Angular build; no production credentials, provider secrets or real-money operations were used.

Runtime checks used the repository Playwright config plus a temporary five-project config (`frontend/.codex-t165-playwright.config.ts`, removed after the run):

```text
frontend/node_modules/.bin/playwright.cmd test financial-reporting.spec.ts:123 --config=.codex-t165-playwright.config.ts --reporter=list --global-timeout=240000
frontend/node_modules/.bin/playwright.cmd test financial-reporting.spec.ts:169 --config=.codex-t165-playwright.config.ts --reporter=list --global-timeout=240000
frontend/node_modules/.bin/playwright.cmd test financial-accessibility.spec.ts --config=.codex-t165-playwright.config.ts --reporter=list --global-timeout=300000
frontend/node_modules/.bin/playwright.cmd test stay-checkout-invoice.spec.ts:241 --config=.codex-t165-playwright.config.ts --reporter=list --global-timeout=300000
frontend/node_modules/.bin/playwright.cmd test platform-subscription-purchase.spec.ts:46 --config=.codex-t165-playwright.config.ts --reporter=list --global-timeout=240000
```

The public payment states were also opened in the in-app Browser and measured with `document.documentElement.scrollWidth/clientWidth` plus visible-element bounds at every required width.

## Results by screen

| Surface | Role/fixture | 320 | 375 | 768 | 1024 | 1440 | Evidence and disposition |
|---|---|---:|---:|---:|---:|---:|---|
| `/management/payment-configuration` | Property owner; `financial-accessibility.spec.ts:227` | PASS | PASS | PASS | PASS | PASS | 5/5 passed; named controls, focus order, contrast and reduced-motion assertions. |
| `/management/property-revenue` | Property owner; `financial-reporting.spec.ts:123` | PASS | PASS | PASS | PASS | PASS | 5/5 passed; property-scoped report/filter/IDOR journey reached the report screen. |
| `/admin/platform-revenue` | System admin; `financial-reporting.spec.ts:169` | PASS | PASS | PASS | PASS | PASS | 5/5 passed; Platform Billing report/export remained property-isolated. |
| `/admin/platform-refunds` | System admin; `financial-accessibility.spec.ts:278` | PASS | PASS | PASS | PASS | PASS | 5/5 passed; keyboard-operable refund form and visible validation alert. |
| `/payment-simulator` | Public invalid-context state; in-app Browser | PASS | PASS | PASS | PASS | PASS | `scrollWidth === clientWidth` and no visible element outside viewport at all 5 widths. |
| `/payment-result` | Public missing-session state; in-app Browser | PASS | PASS | PASS | PASS | PASS | `scrollWidth === clientWidth` and no visible element outside viewport at all 5 widths. |
| `/admin/reservations` checkout workspace | Admin fixture; `stay-checkout-invoice.spec.ts:241` | BLOCKED | BLOCKED | BLOCKED | BLOCKED | BLOCKED | Fixture reached reservation row but check-in mutation stayed at `0`; `<app-reservation-checkout>` was never reached. No responsive PASS claimed. |
| `/admin/invoices` invoice dialog | Admin fixture; `financial-accessibility.spec.ts:315` | BLOCKED | BLOCKED | BLOCKED | BLOCKED | BLOCKED | Existing API mock did not produce the expected `RES-501` row; all 5 runs failed before dialog assertion. This is a fixture/harness blocker, not a verified responsive defect. |
| `/management/billing` | Owner; `platform-subscription-purchase.spec.ts:46` | BLOCKED_EXTERNAL | BLOCKED_EXTERNAL | BLOCKED_EXTERNAL | BLOCKED_EXTERNAL | BLOCKED_EXTERNAL | All 5 runs skipped because sandbox/backend purchase credentials are not configured. |
| `/admin/platform-payment-configuration` | System admin | PARTIAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | Route/component exists, but no dedicated five-width authenticated fixture was available in the current e2e set. |
| `/admin/refunds` | Property refund approver | PARTIAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | Route/component exists, but current responsive fixture covers customer `/refunds` and platform refunds only; no truthful five-width admin-property-refund journey was available. |
| `/my-invoices` | Customer | BLOCKED | BLOCKED | BLOCKED | BLOCKED | BLOCKED | Customer invoice detail is reached only after checkout in `stay-checkout-invoice.spec.ts`; that fixture stopped at the check-in mutation blocker. |
| `/refunds` | Customer | PARTIAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | Refund lifecycle fixture exists, but its current run is coupled to a hard-coded `localhost:4200` response assertion; isolated port evidence was not promoted to PASS. |

## Defects and fixes

No UI source defect was patched during T165. The verified responsive surfaces did not show document-level horizontal overflow at any required width. The blocked rows require fixture/API harness repair or external sandbox inputs before a responsive fix can be judged safely.

## Artifacts and reproducibility

Playwright failure screenshots/traces for the blocked checkout and invoice fixture runs are retained under `.codex-t165-runtime/test-results/` in the shared worktree. They show the pre-assertion states and must not be treated as PASS evidence. The exact commands above, the five-project viewport config, and the current worktree fingerprint should be rerun after the fixture blockers are repaired.

## Final gate

T165 is not complete: 8 surfaces have complete five-width runtime evidence, while checkout, invoice detail and platform configuration/property-refund/customer-invoice paths remain blocked or partial. No task checkbox was changed by this audit.

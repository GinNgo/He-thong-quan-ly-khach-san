# T229 - Auth legal and support destinations

Date: 2026-08-04

## Scope

- Replaced auth `href="#"` policy links and unsupported footer notices with public Angular routes.
- Added localized Vietnamese/English terms, privacy, cookie, contact, and support pages.
- Added route-focus, landmark, current-page, keyboard-link, and cookie-preference checks.
- Did not change credential login, social login, authorization, backend APIs, or production configuration.

## Implemented routes

| Route | Page data | Guard |
|---|---|---|
| `/terms` | `TERMS` | Public |
| `/privacy` | `PRIVACY` | Public |
| `/cookies` | `COOKIES` | Public |
| `/contact` | `CONTACT` | Public |
| `/support` | `SUPPORT` | Public |

## Accessibility and behavior

- Route activation focuses the page `h1` with `tabindex="-1"` for screen-reader context.
- The page exposes one `main`, one labelled policy/support `nav`, and `aria-current="page"`.
- All policy navigation and auth/profile destinations are real router links; contact actions use explicit `tel:` and `mailto:` links.
- Cookie choices use native checkboxes, preserve essential storage, save optional choices locally, and announce save status through `aria-live="polite"`.
- Interactive targets are at least 44px and retain the shared visible focus ring.
- Responsive layout stacks at narrow widths and uses the existing LuxeStay design tokens.

## Source verification

```text
rg -n 'href\s*=\s*["''][#]["'']|chưa được tích hợp|chưa tích hợp|not integrated' \
  frontend/src/app/features/auth frontend/src/app/features/client/profile \
  -g '*.html' -g '*.ts' -g '!*.spec.ts' -i

Result: no source placeholders found.
```

## Focused validation

Final command executed in the shared sequential Angular validation lane:

```text
npm test -- --watch=false \
  --include=src/app/features/auth/legal-support/public-information-page.component.spec.ts \
  --include=src/app/features/auth/login/login.component.spec.ts \
  --include=src/app/features/auth/register/register.component.spec.ts \
  --include=src/app/features/auth/admin-login/admin-login.component.spec.ts \
  --include=src/app/features/client/profile/profile-support-link.component.spec.ts
```

Result: exit code `0`; 5 files and 18 tests passed. The Angular test bundle completed successfully.

An earlier run correctly exposed that the legacy auth specs mocked `Router`, so Angular could not generate anchor `href` values. The focused link assertions now use the real test router. The profile support assertion was separated from the broader payment/refund profile suite so T229 does not alter or mask unrelated profile expectations.

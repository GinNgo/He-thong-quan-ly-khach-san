# Quality Checklist: Feature-02B

## Requirements

- [x] Menu authority and failure behavior explicit.
- [x] Existing/dead routes identified from `app.routes.ts`.
- [x] Property selector grounded in management context API.
- [x] Mobile, keyboard, focus, status and console criteria measurable.
- [x] Backend security and tenant contracts unchanged.
- [x] Dependency/backend changes excluded.

## Implementation

- [ ] Sidebar never displays invented fallback links.
- [ ] Loading, empty, error and retry states accessible.
- [ ] Admin shell responsive with breadcrumb and labelled popup controls.
- [ ] Management shell contains existing routes only.
- [ ] Property selector accepts server-validated context only.
- [ ] Mobile drawers close through link, backdrop and Escape.
- [ ] Subscriptions terminate on destroy; no new `any`.
- [ ] Focused tests pass.

## Delivery

- [ ] Full frontend tests and production build pass.
- [ ] Browser responsive/console smoke passes.
- [ ] Maven regression passes.
- [ ] Git whitespace and secret/artifact scan pass.
- [ ] Explicit commit created and pushed non-force.
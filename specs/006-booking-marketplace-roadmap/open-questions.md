# Open Questions and Decision Gates

| ID | Question | Recommended choice | Status | Blocks |
|---|---|---|---|---|
| OQ-001 | How should non-production payment confirmation be authorized? | Signed, expiring, server-issued demo transaction bound to reservation, owner, amount and method; official provider callbacks remain authoritative | **APPROVED 2026-07-29**: VNPay Sandbox, MoMo Test and ZaloPay Sandbox; Stripe deferred | Phase 5 callback implementation/browser mutation |
| OQ-002 | How long should an unpaid booking hold inventory? | Configurable 15 minutes, aligned with current VNPay expiry | Product approval recommended | Phase 5 hold scheduler and late callback rule |
| OQ-003 | Can automatic promotions stack? | Choose best one; optionally combine with one coupon | **APPROVED 2026-08-03**: use the single best eligible automatic campaign and optionally one eligible coupon; never stack multiple automatic campaigns | Phase 3 quote/rule engine |
| OQ-004 | What creates VIP/member tier? | Explicit tier policy managed by system; do not infer directly from points | **APPROVED 2026-08-03**: tier is assigned by an explicit managed membership policy; points and room codes are inputs/labels only, not implicit tier creation | Phase 3 member deals |
| OQ-005 | Where may sponsored properties appear? | Fixed Home/search slots with relevance threshold and visible label | **APPROVED 2026-08-03**: fixed Home/search slots only, relevance-gated, independently ranked and always disclosed as `Được tài trợ` / `Sponsored` | Phase 3 placement/ranking |
| OQ-006 | Who can configure tenant social channels? | Tenant owner plus specifically authorized manager; system admin can revoke | Security/product approval recommended | Phase 6 management API/UI |
| OQ-007 | Where are Facebook/Zalo tokens stored? | External secret manager or encrypted server-side secret reference | Infrastructure decision required | Phase 6 production readiness |
| OQ-008 | Which provider launches first? | Zalo OA or Facebook Messenger, one provider at a time after sandbox access | Credential/provider decision required | Phase 6 provider adapter order |
| OQ-009 | What is the social message retention/consent policy? | Define per legal/privacy requirements before production | Legal/privacy approval required | Phase 6 production activation |
| OQ-010 | Is subscription online purchase in scope now? | Keep contact-only until order/payment/refund policy is approved | Open product decision | Phase 7 purchase tasks |
| OQ-011 | Should English cover admin/management in the first release? | First release public P1 only; admin/management follows incrementally | Recommended, not blocking Phase 4 public scope | Localization scope |
| OQ-012 | Should the Home partner band remain after header CTA removal? | Keep it because it is lower-priority, contextual and preserves acquisition | Assumed approved unless user requests full removal | Phase 1 scope |

## Stop Rules

- T050-series payment security mutations are authorized only within the approved VNPay/MoMo/ZaloPay server-side session contract. Provider success cannot be simulated as official sandbox completion.
- Stop before provider credential storage or message transmission if OQ-006 to OQ-009 are unresolved.
- Stop before displaying a payable promotion if OQ-003/OQ-004 rules are unresolved.
- Stop before claiming subscription purchase support if OQ-010 is unresolved.

## Decision Log

- **2026-08-03**: The product owner approved OQ-003, OQ-004 and OQ-005 in the implementation thread. The approval covers stacking, membership-tier authority and sponsored-placement disclosure/surface rules only; OQ-002 and OQ-010 remain open and continue to block their respective work.

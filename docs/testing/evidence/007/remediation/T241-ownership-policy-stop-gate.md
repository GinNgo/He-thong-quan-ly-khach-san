# T241 Ownership Policy Stop Gate

## Status

- Task: T241 / PROP-SUB-015
- Status: `STOP_GATE_POLICY`
- Implementation: not started by design

The available specification, plan, task and domain inventory define the missing capability but do not approve the business rules needed to implement it. These decisions can change subscription billing responsibility and property authority, so the task meets the explicit financial-policy stop condition.

## Required Decisions

1. Define which actions are exclusive to the primary owner and which are allowed for co-owners.
2. Define invitation creation, expiry, revocation, acceptance identity checks and whether acceptance is required before authority or billing changes.
3. Define whether a property may have zero active owners and the exact last-owner removal/relinquishment protection.
4. Define primary ownership transfer: initiator, recipient acceptance, effective time, rollback/dispute behavior and required audit evidence.
5. Define co-owner removal and voluntary relinquishment, including active bookings, staff, payouts, disputes and suspended properties.
6. Define the subscription contract owner and payer before and after invite, acceptance, transfer, removal or relinquishment.
7. Define treatment of current billing periods, credits, refunds, proration, invoices, unpaid balances and renewal responsibility during ownership changes.
8. Define authorization and notification requirements for admins, the current primary owner, affected co-owners and the incoming owner.

## Safety Boundary

- No transfer, co-owner invitation, removal or relinquishment API/UI/schema was created.
- No subscription account, payer, invoice, entitlement or renewal responsibility was reassigned.
- No default financial policy was inferred from the existing `user_properties` model.
- T242-T244 were not started because the user-directed queue requires stopping when missing business policy can alter financial results.

## Resume Criteria

Resume T241 only after the decisions above are explicitly approved and recorded in the feature specification or another authoritative policy artifact. Then run `speckit-clarify`/`speckit-plan` as needed before `speckit-implement`.

## Coordinator Handoff

- Keep T241 unchecked in `specs/007-payment-billing-completion/tasks.md`.
- Record PROP-SUB-015 as policy-blocked in both shared aggregate inventories.
- Do not mark the T228-T244 queue complete while this stop gate remains unresolved.

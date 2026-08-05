# Registration and Payment Email Templates

Branch: `codex/payment-email-templates`  
Status: implemented and focused-test verified

## Implemented flows

- Initial registration and email-change verification use a branded bilingual VI/EN HTML
  template with a one-time expiry notice, escaped user-controlled values and a clear
  security warning.
- Successful Property Commerce payments send the customer a receipt for booking deposit,
  balance, service or surcharge payments. The receipt uses authoritative ledger values and
  includes reservation, property, amount, method/provider and transaction references.
- Successful Platform Billing payments send the tenant owner a subscription receipt with
  property, plan/order, operation, amount, method/provider and transaction references.
- Provider callback replay and manual-confirmation replay do not schedule another receipt.
  New receipts are scheduled only after the surrounding database transaction commits.
- Email delivery failure is recorded through operational metrics and does not reverse or
  mutate the completed financial transaction.

## Validation

The normal worktree Maven compile is affected by pre-existing UTF-8 BOM files and other
shared incomplete sources. Verification therefore used a clean temporary snapshot of this
branch, removed only the two unrelated BOM markers in that snapshot, and supplied the
shared subscription DTO/repository sources that are currently uncommitted in the root
coordination worktree.

```powershell
.\mvnw.cmd -q '-Dmaven.test.skip=true' package
.\mvnw.cmd -q '-Dtest=EmailVerificationMailerTest,PaymentReceiptEmailServiceTest,PropertyPaymentCallbackServiceTest,ManualTransferConfirmationServiceTest,PlatformPaymentCallbackServiceTest' test
```

Results: production package compile PASS; focused tests 20/20 PASS, 0 failures, 0 errors,
0 skipped.

## Safety and recovery

- No production mailbox credential, provider credential, production database or real-money
  transaction was used.
- Templates never include passwords, OTPs, merchant secrets, raw callback payloads or card
  details.
- Set `EMAIL_VERIFICATION_ENABLED=false` or `PAYMENT_RECEIPT_EMAIL_ENABLED=false` to disable
  the respective delivery path without affecting authentication or financial state.
- Code rollback removes the new receipt service and callback hooks. No schema rollback or
  destructive data action is required.

# Feature 009 VNPay Inventory

## Existing foundations

- Shared verification: `VnpayPaymentProviderAdapter` validates HMAC-SHA512, merchant, amount, currency and reference.
- Property Commerce: tenant-owned payment configuration, attempts, callback orchestration, ledger and concurrency tests.
- Platform Billing: system-owned configuration, subscription order snapshots, payment attempts, callback orchestration and entitlement application.
- Legacy booking flow: `PaymentController`, `PaymentSessionService` and `VnpayPaymentGateway`.

## Required convergence

- Move booking VNPay UI traffic to canonical Property Commerce payment attempts.
- Keep a temporary compatibility adapter so legacy callers do not create a second ledger effect.
- Add VNPay GET query ingress for provider IPN/return and delegate to context callback services.
- Keep browser return display-only and poll stored status.
- Add platform signed checkout URL and enable the currently disabled VNPay package option.
- Persist stable retry identity for platform order/provider/method.
- Add VNPay status-query recovery for lost/late platform IPN.
- Store mismatched or conflicting callbacks as reconciliation cases without fabricating success.


# Payment Capability Matrix - Initial Baseline

| Capability | Context | Existing evidence | Initial status | Feature 007 task |
|---|---|---|---|---|
| Server-owned payment session | Property/Provider boundary | `PaymentSessionService`, provider callbacks | `PARTIAL` | T052-T063 |
| Property bank/QR/deposit configuration | Property Commerce | No complete tenant-owned configuration | `MISSING` | T037-T048 |
| Immutable property ledger | Property Commerce | Legacy `Payment` entity | `PARTIAL` | T014, T052-T059 |
| Service/surcharge snapshots | Property Commerce | Partial `ReservationServiceItem` | `PARTIAL` | T070-T073 |
| Atomic checkout/invoice | Property Commerce | Existing checkout/invoice flow | `PARTIAL` | T074-T091 |
| Property refund lifecycle | Property Commerce | `RefundRequest`/provider attempts | `PARTIAL` | T112-T124 |
| Subscription order/callback/entitlement | Platform Billing | Existing subscription entities/service | `PARTIAL` | T092-T111 |
| Independent property/platform reports | Both | Mock/hard-coded analytics | `MISSING` | T125-T140 |
| Tenant filter activation | Property Commerce | Annotations exist; activation incomplete | `PARTIAL` | T026-T027 |
| Full-system traceability and manual guide | Whole system | Existing feature audits | `PARTIAL` | T141-T163 |

This is an initial evidence matrix, not a completion claim. Every row is reclassified after implementation and final-worktree execution.

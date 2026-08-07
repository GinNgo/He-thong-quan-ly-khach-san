# Contract: Audit Record

Mỗi dòng trong test matrix hoặc kết quả thực thi phải đáp ứng contract sau.

## Required Fields

| Field | Example | Required |
|-------|---------|----------|
| `id` | `TF-ADMIN-001` | Yes |
| `actor` | `ADMIN` | Yes |
| `surface` | `Admin Dashboard` | Yes |
| `route` | `/admin/dashboard` | Yes |
| `controlOrFlow` | `Gửi yêu cầu duyệt` | Yes |
| `preconditions` | `completedSteps >= 3` | Yes |
| `scenarioType` | `PRIMARY` | Yes |
| `expected` | `Request is submitted once and status updates` | Yes |
| `priority` | `P1` | Yes |
| `integrationLevel` | `REAL_INTEGRATION` | Yes |
| `result` | `NOT_RUN` | Yes |
| `capabilityStatus` | `UNVERIFIED` | Yes |
| `evidence` | `test-results/.../trace.zip` | On failure or verified gap |
| `gapId` | `UIF-002` | When a gap exists |
| `notes` | `Requires approvable property fixture` | Optional |

## Row Invariants

- `route` phải là canonical route; redirect được ghi ở `notes` hoặc entry point.
- Một row chỉ kiểm tra một outcome chính; không gộp nhiều mutation độc lập.
- `PASS` không tự động đồng nghĩa `COMPLETE`; scenario có thể pass ở unit/intercepted layer.
- `COMPLETE` yêu cầu tất cả scenario bắt buộc của flow đã pass trên real integration.
- `BLOCKED` phải nêu rõ account, data, service hoặc environment dependency bị thiếu.
- Mọi control hiển thị phải có row hoặc được ghi rõ là decorative/non-interactive.

## Minimum Scenario Set by Priority

| Priority | Required Scenario Types |
|----------|-------------------------|
| P1 | Primary, validation/error, recovery, permission khi có RBAC, duplicate-submit khi có mutation |
| P2 | Primary, error/recovery, responsive hoặc accessibility theo surface |
| P3 | Primary hoặc inventory verification; Coming Soon phải kiểm tra disabled/label |

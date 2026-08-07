# Contract: Evidence and Status

## Capability Status Decision

| Status | Decision Rule |
|--------|---------------|
| `COMPLETE` | Hành trình chính và scenario bắt buộc pass bằng UI trên real integration; state sau reload nhất quán |
| `PARTIAL` | Một phần flow hoạt động nhưng thiếu nhánh, persistence, data thật hoặc terminal outcome |
| `DISPLAYED_ONLY` | Control/menu/tab xuất hiện nhưng không có handler, không phát sinh kết quả hoặc chỉ là Coming Soon |
| `BROKEN` | Capability được kỳ vọng hoạt động nhưng click/submit/navigation lỗi hoặc cho kết quả sai |
| `BLOCKED` | Không thể xác minh do dependency cụ thể ngoài capability; dependency phải được ghi rõ |
| `MISSING` | Capability được yêu cầu/công bố nhưng không có surface hoặc flow sử dụng được |
| `DORMANT` | Source/component còn tồn tại nhưng không có route/menu/runtime entry point |

## Execution Result Decision

| Result | Decision Rule |
|--------|---------------|
| `PASS` | Actual khớp toàn bộ expected của scenario |
| `FAIL` | Ít nhất một expected không đạt và không phải blocker môi trường |
| `BLOCKED` | Scenario không thể bắt đầu/hoàn tất vì account, data, service hoặc environment dependency cụ thể |
| `NOT_RUN` | Chưa thực thi; không được dùng trong release conclusion |

## Severity

| Severity | Definition |
|----------|------------|
| `P0` | Lộ dữ liệu/tenant, mất tiền/dữ liệu, hệ thống không thể sử dụng hoặc security bypass |
| `P1` | Chặn hành trình cốt lõi, sai payment/reservation/permission hoặc không có recovery |
| `P2` | Chức năng phụ/CRUD/export/feedback không hoạt động hoặc gây nhầm lẫn đáng kể |
| `P3` | Coming Soon, nội dung phụ, polish hoặc vấn đề không chặn nghiệp vụ |

## Evidence Requirements

- Failure P0/P1: trace + screenshot + request/status liên quan + repro steps.
- Failure P2: screenshot hoặc trace, expected/actual và repro steps.
- P3: screenshot hoặc source/runtime note đủ để xác minh.
- Permission/tenant: ghi actor, property context và status code; redacted identifiers nếu cần.
- Payment: không ghi secret, full token, card/bank data hoặc callback signature.
- PASS: reporter output là đủ trừ khi scenario là release evidence được chỉ định.

## Evidence Naming

```text
test-results/<run-id>/<scenario-id>/
|-- screenshot.png
|-- trace.zip
|-- network-summary.txt
`-- notes.md
```

## Completion Rule

Một UI capability chỉ chuyển sang `COMPLETE` khi:

1. Primary scenario pass.
2. Error/recovery scenario bắt buộc pass.
3. Permission/tenant scenario pass nếu áp dụng.
4. Không có console error chưa giải thích hoặc failed request ngoài expected.
5. Terminal state vẫn đúng sau reload/navigation.
6. Evidence đến từ real integration.

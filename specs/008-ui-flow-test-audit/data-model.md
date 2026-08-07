# Data Model: UI Flow Test Audit

Feature này không tạo bảng database mới. Các entity dưới đây là cấu trúc logic cho Markdown/JSON/report và test implementation về sau.

## UI Surface

Đại diện cho một trang, menu hoặc vùng giao diện được công bố.

| Field | Type | Rules |
|-------|------|-------|
| `surfaceId` | string | Duy nhất, ổn định, ví dụ `ADMIN-DASHBOARD` |
| `actor` | enum | `PUBLIC`, `CUSTOMER`, `ADMIN`, `OWNER_MANAGER` |
| `route` | string | Route canonical sau redirect |
| `entryPoints` | string[] | Menu, CTA, direct URL, callback |
| `permission` | string/null | Function/action hoặc role requirement |
| `controls` | string[] | Các control chính được nhìn thấy |
| `dataDependencies` | string[] | API, WebSocket, property context, payment provider |
| `ownerArea` | string | Module chịu trách nhiệm |

## Interaction Flow

Chuỗi thao tác từ điểm bắt đầu đến kết quả nghiệp vụ.

| Field | Type | Rules |
|-------|------|-------|
| `flowId` | string | Duy nhất, ví dụ `CUSTOMER-BOOK-PAY` |
| `name` | string | Tên hành trình theo ngôn ngữ người dùng |
| `actor` | enum | Khớp UI Surface |
| `preconditions` | string[] | Account, data, property, subscription |
| `steps` | string[] | Mỗi bước là hành động quan sát được |
| `terminalOutcome` | string | Kết quả cuối có thể xác minh |
| `affectedSurfaces` | string[] | Liên kết tới `surfaceId` |
| `priority` | enum | `P1`, `P2`, `P3` |

## Test Scenario

Một biến thể kiểm thử của Interaction Flow.

| Field | Type | Rules |
|-------|------|-------|
| `scenarioId` | string | Duy nhất và traceable |
| `flowId` | string | Bắt buộc |
| `scenarioType` | enum | `PRIMARY`, `VALIDATION`, `ERROR`, `RECOVERY`, `PERMISSION`, `RESPONSIVE`, `ACCESSIBILITY` |
| `viewport` | string | Desktop hoặc kích thước mục tiêu |
| `fixtureProfile` | string | Không chứa secret |
| `expected` | string[] | Kết quả rõ ràng, đo được |
| `automationLevel` | enum | `AUTOMATED`, `MANUAL`, `HYBRID` |
| `integrationLevel` | enum | `REAL_INTEGRATION`, `INTERCEPTED`, `UNIT_ONLY` |

## Execution Run

Một lần thực thi scenario trên một build/môi trường.

| Field | Type | Rules |
|-------|------|-------|
| `runId` | string | Timestamp/build based |
| `scenarioId` | string | Bắt buộc |
| `buildRef` | string | Commit/build identifier nếu có |
| `environment` | string | Local, E2E, staging |
| `result` | enum | `PASS`, `FAIL`, `BLOCKED`, `NOT_RUN` |
| `startedAt` | datetime | ISO-8601 |
| `durationMs` | number | Không âm |
| `evidenceRefs` | string[] | Link/path tới evidence |
| `notes` | string | Không chứa secret/PII |

## Capability Gap

Ghi chức năng thiếu hoặc chưa hoàn thiện.

| Field | Type | Rules |
|-------|------|-------|
| `gapId` | string | `UIF-NNN` |
| `surfaceId` | string | Bắt buộc nếu runtime-visible |
| `control` | string | Control hoặc capability bị ảnh hưởng |
| `capabilityStatus` | enum | `PARTIAL`, `DISPLAYED_ONLY`, `BROKEN`, `BLOCKED`, `MISSING`, `DORMANT` |
| `severity` | enum | `P0`, `P1`, `P2`, `P3` |
| `gapType` | enum | `UI_HANDLER`, `NAVIGATION`, `API`, `DATA`, `PERMISSION`, `TENANT`, `VALIDATION`, `RESPONSIVE`, `ACCESSIBILITY`, `TEST_STALE` |
| `expected` | string | Bắt buộc |
| `actual` | string | Bắt buộc sau runtime verification |
| `reproduction` | string[] | Bắt buộc cho verified gap |
| `evidenceRefs` | string[] | Bắt buộc cho P0/P1/P2 verified gap |
| `disposition` | enum | `VERIFY`, `BACKLOG`, `ACCEPTED_COMING_SOON`, `ENVIRONMENT_BLOCKER`, `REMOVE_DORMANT` |

## Audit Evidence

| Field | Type | Rules |
|-------|------|-------|
| `evidenceId` | string | Duy nhất trong run |
| `kind` | enum | `SCREENSHOT`, `TRACE`, `VIDEO`, `CONSOLE`, `NETWORK`, `DATA_SNAPSHOT` |
| `path` | string | Relative path trong workspace/report |
| `redacted` | boolean | Phải `true` nếu evidence từng chứa thông tin nhạy cảm |
| `description` | string | Nêu rõ evidence chứng minh điều gì |

## State Transitions

### Capability Status

```text
UNVERIFIED -> COMPLETE
UNVERIFIED -> PARTIAL
UNVERIFIED -> DISPLAYED_ONLY
UNVERIFIED -> BROKEN
UNVERIFIED -> BLOCKED
UNVERIFIED -> MISSING
UNVERIFIED -> DORMANT

BLOCKED -> any verified status after dependency is available
PARTIAL/DISPLAYED_ONLY/BROKEN/MISSING -> COMPLETE only after fix + real-integration retest
DORMANT -> MISSING or REMOVE_DORMANT after product ownership review
```

### Execution Result

```text
NOT_RUN -> PASS | FAIL | BLOCKED
BLOCKED -> PASS | FAIL after environment/data is restored
FAIL -> PASS only after rerun on a changed build or corrected test
```

## Validation Rules

- `COMPLETE` không hợp lệ nếu `integrationLevel != REAL_INTEGRATION` cho hành trình end-to-end.
- Gap P0/P1/P2 không hợp lệ nếu thiếu expected, actual, reproduction hoặc evidence.
- `BLOCKED` phải ghi dependency cụ thể; không dùng thay cho scenario chưa được chạy do thiếu thời gian.
- `DORMANT` chỉ dùng khi source không còn route/menu/runtime entry point.
- Không lưu credential, JWT, payment secret hoặc dữ liệu cá nhân đầy đủ trong entity/evidence.

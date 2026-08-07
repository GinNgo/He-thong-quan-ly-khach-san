# Incomplete Function Register

Đây là register sau source inspection và runtime audit ngày 2026-08-01. `SOURCE_ONLY` chỉ còn dùng cho candidate chưa đủ runtime dependency; fixture/intercepted evidence không được dùng để gắn `COMPLETE`.

| Gap ID | Surface/Control | Source Evidence | Initial Status | Severity | Verification | Runtime Scenario | Disposition |
|--------|-----------------|-----------------|----------------|----------|--------------|------------------|-------------|
| UIF-001 | Admin Dashboard - CTA `Cập nhật ngay/Chỉnh sửa` | `frontend/src/app/features/admin/dashboard/dashboard.html:33` có button không click/routerLink/submit | DISPLAYED_ONLY | P1 | VERIFIED_BROWSER_FIXTURE | Click produced no URL change and no mutation; screenshot/JSON attachment captured | BACKLOG |
| UIF-002 | Admin Dashboard - CTA `Gửi yêu cầu duyệt` | `frontend/src/app/features/admin/dashboard/dashboard.html:78` chỉ có disabled/ngClass, không handler | DISPLAYED_ONLY | P1 | VERIFIED_BROWSER_FIXTURE | Button remained disabled under loaded non-zero dashboard state; screenshot captured | BACKLOG |
| UIF-003 | Admin Dashboard - 4 stat cards | `frontend/src/app/features/admin/dashboard/dashboard.html:93`, `:105`, `:117`, `:129` dùng `value="0"` | PARTIAL | P1 | VERIFIED_BROWSER_FIXTURE | Non-zero analytics response still rendered all four cards as zero | BACKLOG |
| UIF-004 | Admin Dashboard - Work order table | `frontend/src/app/features/admin/dashboard/dashboard.ts:163` mô phỏng API bằng `setTimeout` và luôn rỗng | DISPLAYED_ONLY | P2 | VERIFIED_BROWSER_FIXTURE | No work-order/maintenance request was issued | BACKLOG |
| UIF-005 | Shared data table - Export Excel/PDF | `frontend/src/app/shared/components/data-table/data-table.ts:88`, `:92` chỉ `console.log` | DISPLAYED_ONLY | P2 | VERIFIED_BROWSER_FIXTURE | Both actions timed out waiting for browser download | BACKLOG |
| UIF-006 | Customer login - Forgot password | `frontend/src/app/features/auth/login/login.component.html:60` ghi chưa hỗ trợ | MISSING | P2 | VERIFIED_BROWSER | Runtime renders a visible non-actionable `span`; screenshot in Playwright results | BACKLOG |
| UIF-007 | Admin login - Forgot password | `frontend/src/app/features/auth/admin-login/admin-login.component.html:97` ghi chưa hỗ trợ | MISSING | P2 | VERIFIED_BROWSER | Runtime renders a visible non-actionable `span`; screenshot in Playwright results | BACKLOG |
| UIF-008 | Home search - Flight tab | `frontend/src/app/features/client/home/components/search-service-tabs/search-service-tabs.component.ts:47` disabled + Coming Soon | DISPLAYED_ONLY | P3 | VERIFIED_BROWSER | Runtime shows the tab disabled with Coming Soon label | ACCEPTED_COMING_SOON candidate |
| UIF-009 | Home search - Transfer tab | `frontend/src/app/features/client/home/components/search-service-tabs/search-service-tabs.component.ts:48` disabled + Coming Soon | DISPLAYED_ONLY | P3 | VERIFIED_BROWSER | Runtime shows the tab disabled with Coming Soon label | ACCEPTED_COMING_SOON candidate |
| UIF-010 | Login legal/support links | `frontend/src/app/features/auth/login/login.component.html:118-120` dùng `href="#"` | BROKEN | P2 | VERIFIED_BROWSER | Runtime exposes 3 visible placeholder links; screenshot in Playwright results | BACKLOG |
| UIF-011 | Register terms/privacy/footer links | `frontend/src/app/features/auth/register/register.component.html:120-121`, `:156-159` dùng `href="#"` | BROKEN | P2 | VERIFIED_BROWSER | Runtime exposes 6 visible placeholder links; screenshot in Playwright results | BACKLOG |
| UIF-012 | Management `Cơ sở lưu trú` | `frontend/src/app/app.routes.ts:81-82` cho dashboard và properties cùng component | PARTIAL | P1 | VERIFIED_BROWSER_FIXTURE | Both routes rendered identical `app-management-dashboard` content | BACKLOG |
| UIF-013 | Customer flow automated test | `frontend/e2e/customer-flows.spec.ts:23`, `:35` dùng `/client/profile`, router hiện dùng `/profile` và `/booking-history` | STALE TEST | P1 test integrity | VERIFIED_SOURCE_AUDIT | Source audit failed on both removed routes | BACKLOG_TEST |
| UIF-014 | Admin dynamic menu compatibility | `frontend/src/app/layout/sidebar/sidebar.ts` chỉ loại `/ai`, `/admin/ai`; menu còn lại phụ thuộc backend | PARTIAL | P1 | VERIFIED_BROWSER_REAL_PARTIAL | Customer/admin/owner real login and core route groups passed; exhaustive `/auth/my-menu` entry traversal is still not implemented | VERIFY |
| UIF-015 | Dormant invoice mockup/legal link | `frontend/src/app/features/admin/invoice-management/invoice-management.component.html:114` chứa copy lỗi và `href="#"`, nhưng active route import file khác | DORMANT | P3 | VERIFIED_SOURCE_GRAPH | Router and repository-wide import search found no runtime entry outside its own source | REMOVE_DORMANT candidate |
| UIF-016 | Dormant mock screens | `features/customer/room-search`, `features/customer/checkout`, `features/admin/room-management/room-management.component.ts` chứa mock/no-op nhưng không phải active route imports | DORMANT | P3 | VERIFIED_SOURCE_GRAPH | Router and repository-wide import search found no runtime entry outside their own source | REMOVE_DORMANT candidate |

## Final Classification

| Capability Status | Count | Gap IDs |
|-------------------|-------|---------|
| COMPLETE | 0 | None |
| PARTIAL | 3 | UIF-003, UIF-012, UIF-014 |
| DISPLAYED_ONLY | 6 | UIF-001, UIF-002, UIF-004, UIF-005, UIF-008, UIF-009 |
| BROKEN | 2 | UIF-010, UIF-011 |
| BLOCKED | 0 | None |
| MISSING | 2 | UIF-006, UIF-007 |
| DORMANT | 2 | UIF-015, UIF-016 |
| STALE TEST | 1 | UIF-013 |

## Evidence Roots

- `frontend/test-results/source-inventory/`: UIF-006, UIF-007, UIF-010, UIF-011, UIF-013, UIF-015 and UIF-016 source evidence.
- `frontend/test-results/browser-capability/`: UIF-001..UIF-012 browser fixture/runtime evidence, including 11 retained failure traces.
- `frontend/test-results/p1-gap-evidence/`: deterministic screenshot + trace evidence for UIF-002 and UIF-012.
- `frontend/test-results/real-flow/`: initial UIF-014 missing-role credential blocker evidence.
- `frontend/test-results/real-flow-authenticated/`: authenticated follow-up evidence; core role journeys pass and UIF-014 moves to partial pending exhaustive dynamic-menu traversal.

## Runtime Update Template

Khi xác minh một mục, thêm:

```text
Actual:
Reproduction:
Environment/Actor/Property:
Network/Console:
Evidence:
Verified Status:
Verified Severity:
Disposition:
```

## Rules

- Không chuyển candidate sang verified chỉ vì đọc source.
- Không đánh dấu `BLOCKED` nếu đơn giản là chưa chạy.
- `DORMANT` phải chứng minh không có runtime entry point.
- Coming Soon được chấp nhận chỉ khi disabled rõ, không gây hiểu nhầm và không phát mutation.
- Test stale là gap test integrity, không tự động là lỗi sản phẩm.

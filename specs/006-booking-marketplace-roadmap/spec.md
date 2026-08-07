# Feature Specification: LuxeStay Booking Marketplace Readiness

**Feature Branch**: `[006-booking-marketplace-roadmap]`

**Created**: 2026-07-29

**Status**: Phase 1 and the Phase 2 landmark search contract are implemented. Phase 2C exposes Vietnam's current 34-province/city structure while retaining the 63-province dataset as a non-destructive compatibility layer for existing hotel and ward references. Automated generation, import, browser and measured API evidence proves 34/34 breadth and at least three active coordinate-valid landmarks per current unit. Phase 4 VI/EN and Home motion passes reduced-motion plus five-run desktop/mobile interaction and CLS evidence. Phase 7 is partial only because admin plan/feature lifecycle approval remains open: canonical reads/UI, mutation enforcement, active/expired/lifetime/unlimited/multi-plan fixtures and the real contact upgrade path pass integration/browser evidence. Day-use is partial/blocked by the backend contract; other later roadmap work remains planned or gated.

**Input**: Audit and phase the public marketplace experience: remove the header partner button, repair responsive search/date behavior, add landmarks, promotions, VIP/deal visibility, governed advertising, Vietnamese/English localization, accessible Home motion, payment/refund/concurrency proof, tenant support channels and subscription entitlement reconciliation.

## User Scenarios & Testing

### User Story 1 - Tìm chỗ nghỉ ổn định trên mọi màn hình (Priority: P1)

Khách truy cập Home có thể nhập địa điểm, chọn đúng khoảng ngày, số khách/phòng và gửi tìm kiếm trên mobile hoặc desktop mà không bị tràn ngang, che popup hoặc đổi ngày ngoài ý muốn. Header tập trung vào hành trình tìm kiếm và không còn nút “Đăng chỗ nghỉ của bạn” trong cụm hành động chính.

**Why this priority**: Đây là cửa vào của toàn bộ hành trình đặt phòng; lỗi responsive hoặc ngày làm hỏng chuyển đổi trước khi khách nhìn thấy kết quả.

**Independent Test**: Tại 375/768/1024/1440px, chọn điểm đến, ngày qua đêm hoặc trong ngày, khách/phòng và gửi tìm kiếm; URL và trạng thái hiển thị phải khớp, không có scroll ngang.

**Acceptance Scenarios**:

1. **Given** viewport 375px, **When** khách mở bộ tìm kiếm, **Then** các trường xếp dọc, popup nằm trong viewport và nút tìm kiếm luôn dùng được.
2. **Given** khách chọn ngày nhận rồi chọn ngày trả, **When** khoảng ngày hợp lệ, **Then** hệ thống giữ chính xác hai ngày đã chọn và không tự cộng ngày ngoài trường hợp sửa dữ liệu không hợp lệ.
3. **Given** chế độ ở trong ngày, **When** khách chọn một ngày, **Then** chỉ gửi ngày sử dụng và UI không yêu cầu ngày trả.
4. **Given** header public, **When** trang tải ở desktop hoặc mobile, **Then** nút đối tác không còn ở header/mobile nav; truy cập khu quản lý vẫn có trong menu tài khoản và khu vực đối tác ở Home.

---

### User Story 2 - Đặt phòng và thanh toán có trạng thái đáng tin cậy (Priority: P1)

Khách hiểu rõ booking đang giữ chỗ, chờ thanh toán, đã xác nhận, thất bại, hủy hay hoàn tiền; hai khách đặt đồng thời không thể vượt tồn phòng.

**Why this priority**: Sai tồn phòng hoặc xác nhận thanh toán giả là rủi ro tài chính và vận hành cao nhất.

**Independent Test**: Chạy hai yêu cầu đặt cùng loại phòng/ngày với tồn còn một; chỉ một yêu cầu thành công. Sau đó kiểm tra callback lặp, callback sai chủ sở hữu/sai chữ ký, hủy và refund bằng dữ liệu thật trong profile test/e2e.

**Acceptance Scenarios**:

1. **Given** chỉ còn một phòng, **When** hai khách tạo booking đồng thời, **Then** tối đa một booking giữ được tồn và yêu cầu còn lại nhận lỗi hết phòng có thể hiểu được.
2. **Given** callback thành công hợp lệ được gửi lặp, **When** hệ thống xử lý, **Then** chỉ có một giao dịch thu tiền và một lần cộng điểm.
3. **Given** booking đã thanh toán bị hủy theo chính sách, **When** refund được yêu cầu, **Then** trạng thái refund tiến triển rõ ràng và tồn phòng được giải phóng đúng một lần.
4. **Given** callback demo không có bằng chứng chủ sở hữu/chữ ký hợp lệ, **When** gọi endpoint, **Then** hệ thống từ chối và không đổi booking.

---

### User Story 3 - Tìm theo địa danh nổi tiếng (Priority: P2)

Khách có thể tìm một địa danh/điểm tham quan theo tỉnh, chọn kết quả và xem danh sách chỗ nghỉ gần đó với khoảng cách và ngữ cảnh khu vực.

**Why this priority**: Khách du lịch thường tìm theo điểm tham quan thay vì tên phường/xã; backend đã có loại `LANDMARK` nhưng chưa trả dữ liệu.

**Independent Test**: Tìm một địa danh đã nhập, chọn gợi ý, kiểm tra query gồm landmark/toạ độ/radius và kết quả được sắp theo khoảng cách trong bán kính cấu hình.

**Acceptance Scenarios**:

1. **Given** từ khóa khớp một địa danh, **When** autocomplete trả kết quả, **Then** địa danh nằm trong nhóm riêng với tỉnh và mô tả phụ.
2. **Given** khách chọn địa danh, **When** trang kết quả mở, **Then** chỉ các cơ sở hợp lệ trong bán kính được hiển thị và có khoảng cách.
3. **Given** địa danh không có cơ sở lân cận, **When** tìm kiếm hoàn tất, **Then** UI hiển thị empty state và đề xuất mở rộng bán kính/tỉnh.

---

### User Story 3A - Có danh mục địa danh phủ toàn Việt Nam (Priority: P2)

Khách có thể tìm các điểm du lịch tiêu biểu ở mọi tỉnh/thành mà hệ thống hỗ trợ; quản trị viên có thể xây dựng lại danh mục từ nguồn mở đã phê duyệt, xem báo cáo độ phủ và xử lý bản ghi chưa đủ chất lượng mà không tạo trùng hoặc làm mất dữ liệu đang được tham chiếu.

**Why this priority**: Luồng tìm theo địa danh đã hoạt động nhưng file hiện tại chỉ có bảy bản ghi mẫu. Dữ liệu thủ công nhỏ không đủ cho một marketplace toàn quốc và không thể cập nhật an toàn theo thời gian.

**Independent Test**: Chạy công cụ tạo danh mục hai lần với cùng phiên bản nguồn, xác nhận đầu ra không đổi; import vào database test và kiểm tra 34/34 tỉnh/thành hiện hành đều có địa danh active, mỗi địa danh được gắn vào tỉnh mới chính xác, tìm kiếm tỉnh mới mở rộng sang toàn bộ ID tỉnh cũ tương ứng và không có khóa nguồn trùng.

**Acceptance Scenarios**:

1. **Given** bộ nguồn đã được khóa phiên bản, **When** pipeline chạy, **Then** nó tạo file UTF-8 có nguồn gốc, tọa độ, danh mục, điểm phổ biến và mã tỉnh hợp lệ cho từng địa danh.
2. **Given** cùng một nguồn được chạy/import lại, **When** không có thay đổi đầu vào, **Then** kết quả và số bản ghi trong database không tăng thêm.
3. **Given** một ứng viên không xác định được tỉnh hoặc tọa độ đủ tin cậy, **When** chuẩn hóa, **Then** ứng viên được đưa vào báo cáo quarantine và không xuất hiện trong tìm kiếm public.
4. **Given** dữ liệu nguồn đổi tên hoặc biến mất tạm thời, **When** refresh, **Then** manual override được giữ và bản ghi đang được tham chiếu không bị xóa cứng.
5. **Given** một tỉnh hiện hành được hợp nhất từ nhiều tỉnh cũ, **When** khách chọn tỉnh hiện hành, **Then** kết quả bao gồm khách sạn đang lưu dưới mọi tỉnh cũ được ánh xạ mà không sửa sai khóa ngoại lịch sử.
6. **Given** một mã số hành chính hiện hành đã từng mang nghĩa khác trong baseline cũ, **When** import chạy, **Then** hệ thống tạo định danh `VN34-*` riêng và không đổi tên hàng tỉnh cũ tại chỗ.

---

### User Story 4 - Xem khuyến mãi, quyền lợi VIP và nội dung tài trợ minh bạch (Priority: P2)

Khách nhận biết giá gốc, giá sau ưu đãi, điều kiện áp dụng, quyền lợi thành viên và vị trí tài trợ mà không bị đánh lừa bởi badge hoặc quảng cáo giả.

**Why this priority**: Thành phần promotions hiện chỉ là component rời, chưa có dữ liệu/domain và chưa được gắn vào Home.

**Independent Test**: Tạo campaign có thời hạn/đối tượng thật, xác nhận giá search/detail/checkout đồng nhất; nội dung tài trợ có nhãn và không vượt quota.

**Acceptance Scenarios**:

1. **Given** campaign đang hiệu lực, **When** khách đủ điều kiện tìm phòng, **Then** giá gốc, mức giảm, giá cuối và điều kiện được hiển thị nhất quán.
2. **Given** khách không thuộc tier yêu cầu, **When** xem deal VIP, **Then** UI giải thích điều kiện thay vì áp dụng giảm giá.
3. **Given** một cơ sở mua vị trí tài trợ, **When** xuất hiện trên Home/search, **Then** card có nhãn “Tài trợ”, tuân theo relevance, quota và không giả làm xếp hạng tự nhiên.

---

### User Story 5 - Dùng Home bằng tiếng Việt hoặc tiếng Anh với chuyển động vừa đủ (Priority: P2)

Khách đổi ngôn ngữ trên public flow và xem Home sinh động bằng hero/editorial slideshow mà không mất khả năng đọc, điều khiển hoặc hiệu năng.

**Why this priority**: Tệp dịch đã tồn tại nhưng chưa được cấu hình toàn ứng dụng; header hiện chỉ là trạng thái VI/VND không tương tác.

**Independent Test**: Đổi VI/EN, reload và đi qua Home/search/detail/checkout/payment; nội dung, ngày, PrimeNG và thông báo chính đổi đồng bộ. Bật reduced motion để xác nhận slideshow ngừng tự chạy/chuyển động lớn.

**Acceptance Scenarios**:

1. **Given** khách chọn English, **When** reload hoặc đổi route, **Then** locale được giữ và không còn chuỗi public hard-code tiếng Việt trong phạm vi đã công bố.
2. **Given** slideshow tự chạy, **When** focus/hover hoặc người dùng bấm pause, **Then** slideshow dừng và không cướp focus.
3. **Given** `prefers-reduced-motion: reduce`, **When** Home tải, **Then** nội dung hiển thị tĩnh, không auto-play và không mất thông tin.

---

### User Story 6 - Nhận hỗ trợ đúng tenant và đúng kênh (Priority: P2)

Khách nhắn hỗ trợ trong ứng dụng; tenant có thể cấu hình kênh Facebook/Zalo được cấp phép để tiếp nhận/chuyển tiếp hội thoại mà không lộ token hoặc trộn dữ liệu giữa cơ sở.

**Why this priority**: Chat nội bộ đã có queue trung tâm và kiểm soát principal, nhưng chưa có tenant routing hoặc kênh xã hội.

**Independent Test**: Gửi chat từ khách gắn với một property/booking, xác nhận chỉ agent được cấp quyền tenant đó xử lý; webhook Facebook/Zalo giả lập bằng sandbox chính thức được xác minh chữ ký và chống gửi trùng.

**Acceptance Scenarios**:

1. **Given** hội thoại có tenant/property context, **When** agent mở dashboard, **Then** chỉ agent có quyền tenant phù hợp xem/trả lời.
2. **Given** tenant chưa cấu hình kênh xã hội, **When** khách mở hỗ trợ, **Then** chat nội bộ vẫn hoạt động và UI không hiển thị kênh giả.
3. **Given** webhook không hợp lệ hoặc trùng event, **When** backend nhận, **Then** từ chối/bỏ qua an toàn và ghi audit không chứa secret.

---

### User Story 7 - Hiểu và quản lý đúng gói dịch vụ (Priority: P2)

Chủ cơ sở và quản trị viên thấy đúng tên gói, giá, chu kỳ, quyền lợi, giới hạn sử dụng và trạng thái hết hạn; không có nút mua giả hoặc quyền lợi hard-code sai contract.

**Why this priority**: Backend có plan/feature/quota nhưng UI dùng model không đồng nhất, purchase chưa có API public và mới chỉ gate `MAX_PROPERTIES` ở một số luồng.

**Independent Test**: So sánh gói từ API với màn hình admin/management, kích hoạt/hết hạn gói trong fixture và xác nhận mọi mutation được gate đúng trong khi dữ liệu lịch sử vẫn đọc được.

**Acceptance Scenarios**:

1. **Given** plan có feature/limit, **When** hiển thị, **Then** UI đọc trực tiếp contract thay vì suy diễn từ giá/code.
2. **Given** subscription hết hạn, **When** chủ cơ sở thử mutation bị khóa, **Then** hệ thống giải thích giới hạn nhưng vẫn cho xem dữ liệu lịch sử.
3. **Given** purchase online chưa được triển khai, **When** người dùng xem gói, **Then** không có CTA giả; chỉ có liên hệ/nâng cấp theo kênh thật.

### Edge Cases

- Date picker mở gần mép màn hình, xoay thiết bị, bàn phím ảo xuất hiện hoặc chọn ngày qua ranh giới tháng/năm.
- Booking chờ thanh toán hết hạn, callback đến sau khi booking đã hủy/hết hạn hoặc refund lặp nhiều lần.
- Hai campaign chồng nhau, coupon vượt ngân sách, giá giảm về âm hoặc khác giữa search và checkout.
- Địa danh thiếu toạ độ, trùng tên giữa tỉnh, bị ngừng hoạt động hoặc không có property trong bán kính.
- Nguồn địa danh đổi tên, trùng mã nguồn, sai tỉnh do thay đổi địa giới, thiếu dấu tiếng Việt hoặc cùng một điểm được biểu diễn bằng node/way/relation khác nhau.
- Mã tỉnh số hiện hành trùng mã lịch sử nhưng tên/địa giới đã đổi; khách sạn vẫn trỏ tỉnh cũ trong khi địa danh mới trỏ tỉnh hiện hành.
- Chuỗi dịch thiếu key, nội dung dài hơn 30-50%, định dạng ngày/tiền khác nhưng tiền tệ vẫn là VND.
- Slideshow mất ảnh, mạng chậm, người dùng dùng bàn phím/screen reader hoặc reduced motion.
- Token Facebook/Zalo hết hạn, webhook đến sai tenant, provider rate-limit hoặc tenant thu hồi consent.
- Một người dùng có nhiều subscription hiệu lực, feature `-1` unlimited, `0` disabled hoặc plan bị inactive.

## Requirements

### Functional Requirements

- **FR-001**: Public header MUST remove the standalone partner CTA from desktop actions and mobile navigation while preserving authenticated owner access through the account menu and the Home partner section.
- **FR-002**: Home search MUST have explicit responsive layouts for 375px, 768px, 1024px and 1440px with zero page-level horizontal overflow.
- **FR-003**: Date selection MUST implement a deterministic overnight/day-use state model, local-date serialization and clear invalid-range feedback.
- **FR-004**: Mobile date UI MUST render a single-month viewport or equivalent bounded sheet; desktop MAY render two months.
- **FR-005**: Search controls MUST expose labels, keyboard navigation, visible focus and minimum 44px interactive targets.
- **FR-006**: Search state MUST preserve location/date/guest inputs across Home, sticky search and results without divergent state.
- **FR-007**: Location discovery MUST return populated `LANDMARK` suggestions from persisted, active, province-scoped records.
- **FR-008**: Selecting a landmark MUST pass a stable landmark identifier plus coordinates/radius or equivalent backend-resolved geography to property search.
- **FR-009**: Landmark results MUST support relevance/distance sorting, empty recovery and inactive/missing-coordinate handling.
- **FR-010**: Promotions MUST be persisted, time-bounded, status-controlled, scoped and evaluated server-side from a single pricing contract.
- **FR-011**: Search, detail, checkout, invoice and refund calculations MUST use the same promotion application result and money rounding policy.
- **FR-012**: VIP/member deals MUST derive eligibility from an explicit membership/tier rule and MUST explain unavailable benefits.
- **FR-013**: Sponsored placements MUST be labelled, tenant-scoped, time/budget bounded, relevance constrained and separated from organic ranking evidence.
- **FR-014**: Home promotion/advertising sections MUST have loading, empty, error and reduced-motion behavior and MUST NOT use hard-coded fake campaigns.
- **FR-015**: The application MUST provide a persisted VI/EN locale preference and dynamically update Angular/PrimeNG/public content.
- **FR-016**: Public Home, search, detail, booking, payment result, account navigation and support-critical text MUST reach complete VI/EN key coverage before release.
- **FR-017**: Missing translation keys MUST fall back safely and be detectable in automated checks.
- **FR-018**: Home motion/slideshow MUST provide pause/previous/next controls, avoid focus theft/layout shift and disable autoplay under reduced motion.
- **FR-019**: Reservation and payment status vocabularies MUST be centralized and transitions validated server-side.
- **FR-020**: Pending inventory holds MUST expire by an explicit policy and release inventory idempotently.
- **FR-021**: Reservation creation MUST prevent overbooking under concurrent requests and MUST have a real concurrent integration test.
- **FR-022**: Payment creation/callback MUST validate reservation ownership or a signed server-issued transaction, expected amount, method, expiry and replay/idempotency.
- **FR-023**: Refund MUST model requested/pending/succeeded/failed states and distinguish internal ledger recording from a real gateway refund.
- **FR-024**: Customer/admin UI MUST expose actionable pending, failure, cancellation and refund states without claiming gateway completion prematurely.
- **FR-025**: Internal support chat MUST preserve principal-derived identity, WebSocket destination restrictions and auditable delivery/retry states.
- **FR-026**: Tenant support routing MUST prevent cross-property conversation access and MUST define assignment/escalation behavior.
- **FR-027**: Facebook/Zalo integration MUST be optional per tenant, use official OAuth/webhooks/APIs, encrypt or externally reference secrets and verify webhook authenticity/idempotency.
- **FR-028**: Subscription plan DTOs MUST expose a canonical schema for names, pricing, billing, status and features/limits used by both admin and management UI.
- **FR-029**: Plan entitlements MUST gate every advertised mutation consistently while preserving authorized read-only historical access after expiry.
- **FR-030**: Unsupported purchase/upgrade paths MUST be disabled or replaced by a real contact workflow; no simulated success messaging is allowed.
- **FR-031**: Every phase MUST include unit/integration/browser evidence against real local services and MUST not use mocked UI responses to mark completion.
- **FR-032**: Security-, payment- or external-channel policy changes MUST stop at a documented decision gate before implementation when required authorization/credentials are absent.
- **FR-033**: Nationwide landmark data MUST be produced by a repeatable, versioned pipeline using approved open-data sources with recorded license and attribution.
- **FR-034**: The public catalog MUST cover all 34 current provinces/cities; unresolved legacy-to-current mappings MUST be quarantined rather than guessed silently.
- **FR-035**: Imported landmarks MUST use a stable natural key composed from source provider, source object type and source object id; repeated builds/imports MUST be idempotent.
- **FR-036**: Landmark records MUST retain source provenance, source update/last-seen timestamps, data-quality status and manual-override precedence.
- **FR-037**: The pipeline MUST deduplicate exact source keys and flag near-duplicate normalized-name/coordinate candidates for review.
- **FR-038**: Catalog refresh MUST preserve referenced province/ward/landmark rows and MUST use safe deactivation rather than destructive deletion after a single missing-source run.
- **FR-039**: Current provinces MUST use stable application identities prefixed `VN34-`; legacy numeric province source codes MUST NOT be repurposed or renamed in place.
- **FR-040**: Every one of the 63 legacy provinces MUST map to exactly one current province, and every current province MUST declare all of its legacy members in a versioned machine-readable catalog.
- **FR-041**: Public province lists and province autocomplete MUST expose only the 34 current provinces while legacy province and ward rows remain available for compatibility.
- **FR-042**: Selecting a current province MUST search hotels stored under the current province id and every mapped legacy province id; displayed province context MUST use the current province name.
- **FR-043**: Landmark generation/import MUST remap accepted records to current province identities, report coverage against 34 units and preserve legacy province/bounding-box data only for source validation.

### Key Entities

- **Landmark**: Active regional point of interest with bilingual name, province/ward, coordinates, category, popularity and search radius.
- **CurrentProvinceAlias**: Versioned mapping from one `VN34-*` current province identity to its official code/name and one or more legacy province source codes.
- **LocationImportRun / LandmarkCandidate**: Versioned source run, coverage metrics and quarantined candidate used to audit nationwide catalog generation.
- **PromotionCampaign**: Tenant/system campaign with validity, eligibility, budget/quota, discount rule, stacking rule and lifecycle status.
- **MembershipTier**: Customer tier and benefit/eligibility rules for member-only pricing.
- **SponsoredPlacement**: Labelled paid placement with surface, targeting, schedule, budget and ranking constraints.
- **ReservationHold**: Inventory reservation window with expiry, quantity, status and idempotency key.
- **PaymentTransaction / Refund**: Auditable charge/refund attempt with provider reference, amount, status, signature/idempotency metadata and timestamps.
- **TenantSupportChannel**: Per-tenant internal/Facebook/Zalo channel configuration with secret reference, provider state and consent/audit metadata.
- **SubscriptionPlan / PlanFeature / AccountSubscription**: Canonical package, entitlement and effective subscription records.
- **LocalePreference**: VI/EN choice persisted for guest or authenticated user.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Home search and date selection complete without page overflow or clipped interactive content at 375/768/1024/1440px in portrait and one mobile landscape sample.
- **SC-002**: 100% of targeted date state transitions pass unit tests, including overnight, day-use, same-day invalid, month boundary, restored recent search and local timezone serialization.
- **SC-003**: A concurrent integration test with one remaining room produces exactly one successful hold/booking and one deterministic sold-out response.
- **SC-004**: Repeated or concurrent callback/refund attempts create no duplicate financial effect; tampered, expired and cross-account attempts are rejected.
- **SC-005**: Search/detail/checkout show the same final payable amount for every promotion fixture and disclose sponsored content in 100% of sponsored placements.
- **SC-006**: All public P1 routes have zero missing VI/EN keys and preserve locale after reload; PrimeNG date labels follow the active locale.
- **SC-007**: Home slideshow meets keyboard/pause/reduced-motion requirements and adds no more than 100ms p75 interaction delay or 0.05 CLS across five cold desktop/mobile runs measured with the browser Performance API and layout-shift observer.
- **SC-008**: Landmark autocomplete and radius search pass populated, no-result, inactive and missing-coordinate cases using persisted location data.
- **SC-009**: Internal chat passes authenticated customer/support send/history/reconnect tests; cross-tenant access is denied. Social-channel completion is reported separately per provider sandbox/credential availability.
- **SC-010**: Every advertised plan feature maps to a backend entitlement check or is removed/marked unavailable; expired-plan browser tests preserve read-only access and block unauthorized mutations.
- **SC-011**: Full frontend tests/build, backend tests, security checks and the browser matrix pass before any phase is marked complete.
- **SC-012**: Public discovery exposes exactly 34 current provinces/cities and the generated catalog has at least one verified, coordinate-valid active landmark in 34/34 units; the three-per-province editorial depth target is reported separately and cannot be claimed until met.
- **SC-013**: Running the same catalog build and database import twice produces byte-stable generated JSON and zero additional database rows on the second run.
- **SC-014**: Generated data contains zero duplicate source keys, zero active landmarks without valid coordinates and zero silently unmapped provinces; all rejected candidates appear in a machine-readable report.
- **SC-015**: All 63 legacy province codes occur exactly once in the alias catalog, and representative merged-province search tests return hotels from every mapped legacy member without duplicate results.

## Assumptions

- Currency remains VND in this feature; bilingual UI does not imply multi-currency settlement.
- The top-header partner button is removed, but owner access remains available in authenticated account navigation and the existing Home partner area.
- Existing Angular 22, Spring Boot, SQL Server/H2 profiles, authentication and tenant permission infrastructure are reused.
- Real Facebook/Zalo delivery requires official app credentials, provider review/consent and sandbox access; implementation cannot be marked complete from UI-only simulation.
- The preferred non-production payment design is a signed, expiring, server-issued demo transaction, but T058/GAP-022 remains a decision gate until explicitly approved.
- Feature-05 Home/Footer changes are a baseline dependency and are not reverted by this roadmap.
- The 34-unit baseline follows the 2025 provincial consolidation and the pinned `provinces.open-api.vn/api/v2/p/` artifact; current ward migration is not allowed to destructively replace hotel-referenced legacy wards in this slice.

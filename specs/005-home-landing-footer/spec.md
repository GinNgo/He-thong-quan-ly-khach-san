# Feature Specification: LuxeStay Home Landing & Footer Polish

**Feature Branch**: `[005-home-landing-footer]`

**Created**: 2026-07-28

**Status**: Implemented and Validated

**Input**: Hoàn thiện trang Home đầu vào, giảm khoảng trắng giữa các section, làm rõ khu vực điểm đến/cơ sở nổi bật, hoàn thiện CTA đối tác và bổ sung footer đầy đủ, cân đối, responsive.

## User Scenarios & Testing *(mandatory)*

<!--
  IMPORTANT: User stories should be PRIORITIZED as user journeys ordered by importance.
  Each user story/journey must be INDEPENDENTLY TESTABLE - meaning if you implement just ONE of them,
  you should still have a viable MVP (Minimum Viable Product) that delivers value.

  Assign priorities (P1, P2, P3, etc.) to each story, where P1 is the most critical.
  Think of each story as a standalone slice of functionality that can be:
  - Developed independently
  - Tested independently
  - Deployed independently
  - Demonstrated to users independently
-->

### User Story 1 - Khám phá và bắt đầu tìm chỗ nghỉ (Priority: P1)

Khách truy cập mở trang Home và có thể hiểu ngay dịch vụ, nhập tiêu chí tìm kiếm hoặc chọn một điểm đến/cơ sở nổi bật để tiếp tục hành trình đặt phòng.

**Why this priority**: Đây là luồng giá trị cốt lõi của trang đầu vào; nội dung và CTA cần dẫn người dùng đến kết quả tìm kiếm mà không bị phân tán bởi khoảng trắng hoặc hierarchy yếu.

**Independent Test**: Tải Home ở desktop và mobile, nhập một tiêu chí tìm kiếm hợp lệ, bấm tìm kiếm; sau đó chọn một điểm đến hoặc thẻ cơ sở và xác nhận người dùng được chuyển đến luồng tìm kiếm/chi tiết tương ứng.

**Acceptance Scenarios**:

1. **Given** Home đã tải xong, **When** người dùng quan sát màn hình đầu tiên, **Then** tiêu đề, mô tả, form tìm kiếm và CTA chính nằm trong một hierarchy rõ ràng, không có khoảng trống bất thường giữa hero và nội dung.
2. **Given** người dùng nhập địa điểm, ngày, số khách/phòng, **When** bấm tìm kiếm, **Then** form giữ nguyên hành vi hiện có và điều hướng đến kết quả tìm kiếm với các tiêu chí đã chọn.
3. **Given** API điểm đến hoặc cơ sở đang tải, lỗi hoặc trả về rỗng, **When** trạng thái kết thúc, **Then** Home hiển thị skeleton/loading, thông báo lỗi hoặc empty state có ngữ nghĩa và không tạo vùng trắng không giải thích.
4. **Given** người dùng bấm thẻ điểm đến/cơ sở hoặc nút xem tất cả, **When** thao tác hoàn tất, **Then** điều hướng có thể nhận biết bằng bàn phím và không làm mất tiêu chí tìm kiếm đang chọn.

---

### User Story 2 - Đăng chỗ nghỉ cho đối tác (Priority: P2)

Chủ cơ sở nhìn thấy CTA đối tác ở vị trí hợp lý sau các nội dung khám phá, hiểu lợi ích và có thể bắt đầu đăng chỗ nghỉ hoặc đi tới khu vực quản lý phù hợp với trạng thái tài khoản.

**Why this priority**: CTA đối tác là nhánh chuyển đổi quan trọng nhưng không được lấn át nhiệm vụ tìm kiếm chính của khách lưu trú.

**Independent Test**: Truy cập Home ở cả hai trạng thái đăng nhập/đăng xuất, bấm CTA đối tác và xác nhận điều hướng đúng theo trạng thái tài khoản hiện tại.

**Acceptance Scenarios**:

1. **Given** khách chưa đăng nhập, **When** bấm “Đăng chỗ nghỉ”, **Then** được đưa tới đăng nhập với return URL tới luồng đăng ký đối tác.
2. **Given** chủ cơ sở đã có quyền quản lý hoặc hồ sơ đang chờ duyệt, **When** bấm CTA, **Then** được đưa tới dashboard hoặc trạng thái đăng ký tương ứng.
3. **Given** CTA hiển thị trên mobile, **When** người dùng đọc nội dung, **Then** headline, lợi ích và nút hành động có thứ tự rõ ràng, không bị ép ngang hoặc che bởi widget hỗ trợ.

---

### User Story 3 - Điều hướng và nhận hỗ trợ từ footer (Priority: P2)

Người dùng có thể dùng footer để truy cập nhanh các nhóm liên kết chính, thông tin liên hệ, chính sách và kênh hỗ trợ; footer kết thúc trang một cách đầy đủ, cân đối và không che nội dung.

**Why this priority**: Footer hiện chỉ có logo/tagline/copyright nên thiếu điểm đến điều hướng và thông tin tin cậy ở cuối hành trình.

**Independent Test**: Cuộn tới cuối Home ở 375px và desktop, dùng chuột lẫn bàn phím để mở các liên kết footer, kiểm tra liên hệ và xác nhận widget hỗ trợ không chồng lên nội dung quan trọng.

**Acceptance Scenarios**:

1. **Given** người dùng cuộn tới cuối trang, **When** footer xuất hiện, **Then** footer có logo/tagline, nhóm “Khám phá”, “Đối tác”, “Hỗ trợ”, thông tin liên hệ, chính sách và dòng bản quyền.
2. **Given** người dùng chọn một liên kết footer, **When** liên kết hợp lệ được bấm hoặc kích hoạt bằng Enter, **Then** router điều hướng đúng hoặc mở kênh liên hệ có chủ đích; không có nút giả không phản hồi.
3. **Given** viewport rộng 375px, **When** footer hiển thị, **Then** các cột xếp theo chiều dọc, nội dung không tràn ngang, vùng chạm tối thiểu 44px và focus ring nhìn thấy.
4. **Given** widget hỗ trợ nổi đang đóng hoặc mở, **When** người dùng đứng ở cuối trang, **Then** widget có khoảng đệm an toàn với footer và không che CTA/liên kết footer.

---

[Add more user stories as needed, each with an assigned priority]

### Edge Cases

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right edge cases.
-->

- API điểm đến/cơ sở trả về danh sách rỗng hoặc lỗi mạng: section vẫn giữ heading và hiển thị empty/error state gọn, có thể thử lại nếu luồng hiện có hỗ trợ.
- Tên cơ sở, địa danh hoặc tagline dài: văn bản được xuống dòng/truncate có chủ đích, không đẩy vỡ grid và không tạo scroll ngang.
- Người dùng bật reduced motion: animation reveal/skeleton giảm về trạng thái tĩnh nhưng hierarchy và feedback vẫn đầy đủ.
- Người dùng chưa đăng nhập bấm liên kết cần xác thực: giữ return URL và không rơi vào vòng lặp redirect.
- Chiều cao nội dung thay đổi khi dữ liệu tải xong: footer vẫn nằm sau nội dung, không xuất hiện khoảng trắng cố định bất thường.

## Requirements *(mandatory)*

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right functional requirements.
-->

### Functional Requirements

- **FR-001**: Home MUST present a coherent above-the-fold hierarchy containing the service promise, primary search action and supporting context.
- **FR-002**: Home MUST preserve the existing real search behavior and pass selected criteria to the result/detail navigation.
- **FR-003**: Home MUST present popular destinations and featured properties with consistent section headings, spacing rhythm, loading states and empty/error states.
- **FR-004**: Destination/property cards and “Xem tất cả” controls MUST be keyboard reachable, visibly focused and semantically labelled.
- **FR-005**: The partner CTA MUST reuse the existing account-aware routing behavior and remain visually subordinate to the primary guest search action.
- **FR-006**: The public footer MUST include grouped navigation, support/contact details, legal links and copyright information with valid destinations or explicit contact actions.
- **FR-007**: Footer and Home sections MUST render without horizontal overflow at 375px, 768px, 1024px and 1440px widths.
- **FR-008**: Floating support controls MUST maintain a safe visual offset from the footer and remain usable without obscuring footer content.
- **FR-009**: Interactive Home/Footer controls MUST meet a 44px minimum target and expose visible keyboard focus.
- **FR-010**: Home/Footer motion MUST respect `prefers-reduced-motion` and must not delay access to primary content.
- **FR-011**: The implementation MUST use real application routes/data and MUST NOT introduce mocked UI responses or fake navigation destinations.

### Key Entities *(include if feature involves data)*

- **Home discovery content**: Popular destinations and featured properties returned by the existing public data services, including display name, location, image, rating and availability/price context when present.
- **Footer navigation item**: A labelled destination or contact action grouped by user intent (explore, partner, support, legal), with a keyboard-accessible label and route/action semantics.

## Success Criteria *(mandatory)*

<!--
  ACTION REQUIRED: Define measurable success criteria.
  These must be technology-agnostic and measurable.
  CONSTITUTION V: Do NOT specify mock or fake interfaces. All outcomes must be verified with real integrations.
  CONSTITUTION VI & VII: Explicitly require full E2E testing as a success metric.
-->

### Measurable Outcomes

- **SC-001**: At 375px, the first viewport presents the service promise and search entry without horizontal scrolling or an unexplained blank band larger than 25% of viewport height.
- **SC-002**: At 375px, 768px, 1024px and 1440px, Home and footer content have zero horizontal overflow and all primary controls remain usable.
- **SC-003**: A user can reach search results from Home in one completed search action, and can reach a property detail from a featured card in one card activation.
- **SC-004**: All footer navigation/contact/legal actions are discoverable by keyboard and have a visible focus state; no dead-end placeholder links remain.
- **SC-005**: In a browser review, the support widget never covers footer navigation or the partner CTA at the bottom of the page.
- **SC-006**: Existing frontend unit tests, production build and the targeted Home/Footer browser smoke pass without introducing console errors.

## Assumptions

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right assumptions based on reasonable defaults
  chosen when the feature description did not specify certain details.
-->

- Home remains a public route and reuses the existing search, destination, property and authentication services.
- Footer links should point to routes already present in the application; where a dedicated page is not yet available, use a meaningful existing route or an explicit `mailto:`/`tel:` action rather than a dead placeholder.
- No backend schema or payment policy changes are in scope.
- Responsive review covers 375px, 768px, 1024px and 1440px, with mobile-first stacking for dense sections.
- The floating support widget remains available and is adjusted only for layout-safe spacing, not for chat authorization behavior.

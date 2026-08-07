# HƯỚNG DẪN TRẢ LỜI RUBRIC MÔN HỌC VÀ KHÓA LUẬN

## 1. Nguyên tắc

Rubric được trả lời như một ma trận kiểm chứng, không phải bài quảng cáo sản phẩm. Mọi câu trả lời phải nói đúng mức độ đã triển khai và chỉ tới bằng chứng mở được.

Nguồn chính thức đã được export vào `docs/thesis-assets/rubrics/` ngày 28/07/2026. Hai file không có mã tiêu chí riêng; khi cần trích dẫn trong ma trận, dùng mã truy vết nội bộ `D03-C1..C7` và `D04-C1..C7`, đồng thời giữ STT và trọng số nguyên bản.

## 2. Bảng rubric chuẩn

| Nguồn | Trace ID | Trọng số | Mức tự đánh giá | Câu trả lời ngắn | Mục báo cáo | Bằng chứng | Giới hạn | Việc còn làm |
| --- | --- | ---: | --- | --- | --- | --- | --- | --- |
| D03 | D03-C1..C7 | 10/10/10/15/15/10/30% | READY đến NEEDS_EVIDENCE | Claim + evidence + boundary | Chapter/section | Source/test/figure | Nếu có | Slide, signed forms, E2E |
| D04 | D04-C1..C7 | 10/40/10/5/10/5/20 | READY/PARTIAL/NEEDS_EVIDENCE | Claim + evidence + boundary | Chapter/section | Source/test/figure | Nếu có | DOCX/PDF, hỏi đáp, gap |

Mức tự đánh giá:

- READY: có câu trả lời, vị trí báo cáo và evidence hiện hành.
- NEEDS_EVIDENCE: nội dung có nhưng thiếu test/ảnh/source mapping.
- BLOCKED: không thể xác minh; có lý do và bước mở khóa.
- NOT_APPLICABLE: chỉ dùng khi rubric cho phép và có giải thích.

## 3. Cấu trúc trả lời 30-60 giây

### Bước 1 - Claim

Nói một câu trực tiếp: hệ thống giải quyết việc gì, cho actor nào, ở phạm vi nào.

### Bước 2 - Evidence

Chỉ tới ít nhất hai loại bằng chứng phù hợp:

- Route/màn hình.
- Endpoint/controller/service.
- Entity/migration/ERD.
- Unit/integration/E2E test hoặc log chạy.
- Screenshot có ngày và role.
- Section/figure/table trong báo cáo.

### Bước 3 - Lý do thiết kế

Giải thích quyết định kỹ thuật hoặc nghiệp vụ: bảo mật backend, tenant isolation, inventory động, idempotency, state transition, separation of concerns.

### Bước 4 - Boundary

Nói rõ phần chưa làm hoặc chưa kiểm tra. Dùng đúng status PARTIAL, MISSING, BLOCKED hoặc DEFERRED.

### Bước 5 - Value/next step

Nêu tác động thực tế và bước phát triển tiếp theo nếu cần.

Mẫu:

“Tiêu chí này được đáp ứng ở mức [READY/PARTIAL] bằng chức năng [tên] dành cho [actor]. Luồng đi từ [route] qua [API/service] tới [entity/data]. Bằng chứng nằm tại [section/test/figure]. Thiết kế chọn [lý do]. Phạm vi hiện tại chưa gồm [giới hạn], nên phần đó được ghi là [status] và có kế hoạch [next step].”

## 4. Hướng dẫn theo nhóm tiêu chí

### 4.1. Xác định bài toán và mục tiêu

Cần nói:

- Bài toán quản lý khách sạn và đặt phòng đang giải quyết.
- Các actor chính và pain point.
- Mục tiêu đo được và phạm vi phiên bản hiện tại.
- Vì sao các feature deferred không làm giảm tính trung thực của kết quả.

Bằng chứng:

- Chapter 1.
- BUSINESS_REQUIREMENTS.md.
- Capability matrix.
- Use Case tổng quát.

Không nên nói:

- “Hệ thống đầy đủ mọi chức năng khách sạn.”
- “Tất cả tính năng trong roadmap đã hoàn thành.”

### 4.2. Phân tích yêu cầu

Cần nói:

- Functional requirements theo actor.
- Quy tắc inventory [checkIn, checkOut), quantity và một RoomType/booking hiện tại.
- Trạng thái reservation và room tách biệt.
- Permission và multi-property scope.
- Các điều kiện lỗi/biên quan trọng.

Bằng chứng:

- BUSINESS_REQUIREMENTS, API_SPEC.
- Use Case/Activity Diagram.
- Controller/service validation và test.

### 4.3. Kiến trúc hệ thống

Câu trả lời mẫu:

“Hệ thống dùng Angular frontend, Spring Boot backend và SQL Server. Frontend chịu trách nhiệm trải nghiệm và guard cục bộ; backend là nơi xác thực JWT, kiểm tra action mask và tenant context. Dữ liệu nghiệp vụ được xử lý qua service/JPA. Sơ đồ kiến trúc và sequence auth ở Chương 3 chỉ ra ranh giới này.”

Điểm cần chứng minh:

- Không xem UI guard là lớp bảo mật duy nhất.
- Có ranh giới controller/service/repository.
- Multi-property không tin hotel/property id do client tự gửi.
- Tính năng subscription/feature chỉ được ghi đúng mức triển khai.

### 4.4. Thiết kế cơ sở dữ liệu

Cần nói:

- Entity chính và quan hệ.
- Property/tenant key.
- RoomType khác phòng vật lý như thế nào.
- Reservation, payment/refund, invoice và stay operations.
- Index, constraint, transaction/locking có thật.

Câu trả lời mẫu:

“Thiết kế tách RoomType để mô tả loại phòng và Room để quản lý phòng vật lý. Availability được tính theo số phòng vật lý trừ các reservation giao cắt khoảng [checkIn, checkOut). ERD được đối chiếu với JPA entity và migration; quan hệ nào mới ở mức mục tiêu được ghi riêng, không xem là schema hiện tại.”

### 4.5. Cài đặt chức năng

Trình bày theo luồng end-to-end:

1. Actor thao tác trên route/màn hình nào.
2. Request đi vào endpoint/controller nào.
3. Service áp dụng rule, transaction và authorization nào.
4. Entity/table nào thay đổi.
5. UI nhận trạng thái thành công/lỗi ra sao.
6. Test nào chứng minh.

Các capability nên chuẩn bị demo:

- Auth/RBAC.
- Search/location/availability.
- Property/RoomType/Room.
- Booking/payment/cancellation/refund/invoice.
- Assignment/check-in/service/checkout/housekeeping.
- Multi-property/subscription/import/claim.
- Support chat nếu notification/security task đã pass.

### 4.6. Kiểm thử

Phải nói đủ bốn ý:

- Loại test: unit, integration, E2E/manual.
- Lệnh và ngày chạy.
- Kết quả pass/fail/blocked.
- Kết luận có thể rút ra và không thể rút ra.

Câu trả lời mẫu:

“Các số 49/49, 60/60, 86/86, 122/122 và 66/66 được giữ là HISTORICAL. Lần chạy ngày 29/07/2026 đạt backend 123/123, frontend 73/73 trong 36 file và production build thành công. Playwright discovery có 71 test nhưng full run vẫn BLOCKED do timeout và artifact redirect/search; Admin core data-backed smoke còn 1 fail, 2 không chạy vì thiếu fixture/credential. Em không dùng build hoặc smoke shell để thay thế E2E chức năng.”

### 4.7. Bảo mật

Chuẩn bị:

- JWT stateless.
- PermissionInterceptor/action mask.
- Tenant/property access.
- Endpoint mutation luôn kiểm tra backend.
- Secret qua environment, privacy scrub báo cáo.
- Notification/WebSocket authentication sau khi task song song hoàn tất.

Câu trả lời mẫu:

“Frontend guard chỉ hỗ trợ UX. Quyết định cho phép thao tác nằm ở backend thông qua JWT, permission/action mask và property context. Với WebSocket notification/chat, em chỉ đánh dấu hoàn thành sau khi handshake/channel authorization và integration test được xác minh.”

### 4.8. Giao diện và UX

Cần nói:

- Màn hình phục vụ actor nào.
- Hành trình chính và các trạng thái loading, empty, validation, error.
- Responsive/accessibility nếu có evidence.
- Screenshot nào minh họa.

Không dùng số màn hình như bằng chứng duy nhất; phải gắn với capability hoạt động.

### 4.9. Đóng góp, giới hạn và hướng phát triển

Kết luận nên chia:

- COMPLETE: đóng góp đã chứng minh.
- PARTIAL/BLOCKED: phần còn rủi ro.
- DEFERRED: hướng phát triển có chủ đích.

Câu trả lời mẫu:

“Đóng góp chính là chuỗi search -> availability -> booking -> payment/refund và vòng đời vận hành lưu trú trong bối cảnh multi-property. Phiên bản hiện tại chưa hỗ trợ mixed RoomType trong một booking và customer review/favorites đầy đủ; các phần này được ghi DEFERRED thay vì trình bày như đã hoàn thành.”

## 5. Câu hỏi phản biện và cách trả lời

### “Chức năng nào thực sự hoàn thành?”

Mở capability matrix, chọn 2-3 dòng có source + test/screenshot hiện hành. Không đọc danh sách dài.

### “Vì sao có route nhưng vẫn PARTIAL?”

Giải thích route chỉ chứng minh khả năng điều hướng. Hoàn thành end-to-end cần API, authorization, state/data persistence và verification.

### “Làm sao chống overbooking?”

Chỉ ra thuật toán inventory giao cắt thời gian, transaction/locking/constraint thật trong source và test concurrent nếu có. Nếu test concurrent thiếu, nói rõ PARTIAL.

### “Một booking đặt nhiều loại phòng được không?”

Trả lời đúng contract hiện tại: một booking gắn một RoomType, quantity có thể lớn hơn một. Mixed RoomType là DEFERRED/MISSING nếu chưa triển khai.

### “Customer có chọn dịch vụ thêm hoặc review không?”

Không suy ra từ service operations của nhân viên. Chỉ trả lời có khi customer có route, API, data và test phù hợp.

### “Tại sao số test khác tài liệu cũ?”

Giải thích tài liệu cũ là evidence HISTORICAL theo ngày. Kết luận hiện tại chỉ dùng lần chạy mới; lỗi môi trường được ghi BLOCKED.

### “Hệ thống multi-tenant an toàn thế nào?”

Chỉ ra backend property context, permission và data filtering thực tế. Nếu Constitution yêu cầu Hibernate filter nhưng source chưa đủ, ghi gap thay vì tuyên bố tuân thủ hoàn toàn.

### “Import và claim quyền sở hữu đã hoàn thiện chưa?”

Trả lời tách hai phần. Import có batch/item staging, deduplication và permission ở backend. Claim đã có entity, service và API nhưng controller hiện còn dùng requester/reviewer ID cố định thay vì principal, nên phải trả lời `PARTIAL/BLOCKED` và nêu bước sửa identity mapping cùng integration test.

### “Subscription có mua, gia hạn và nâng cấp đầy đủ chưa?”

Nêu đúng API hiện hành: hệ thống đọc danh sách plan, subscription ACTIVE và feature map; feature gate đã có test CURRENT. Các endpoint register/activate/renew/upgrade/downgrade/revoke/history chưa đầy đủ, vì vậy không gọi lifecycle là hoàn thành.

### “Tại sao chọn kiến trúc này?”

Liên hệ separation of concerns, khả năng kiểm thử, bảo mật backend và khả năng mở rộng multi-property. Tránh chỉ nói “vì phổ biến”.

## 6. Bộ bằng chứng nên mở sẵn khi bảo vệ

- Báo cáo tại Chapter/section được hỏi.
- Capability traceability matrix.
- UML/ERD liên quan.
- Route và endpoint/controller.
- Service/entity/migration.
- Test file và log chạy gần nhất.
- Screenshot sạch dữ liệu nhạy cảm.

Sắp xếp bookmark theo capabilityId để chuyển từ câu hỏi sang bằng chứng nhanh.

## 7. Mẫu trả lời 30-60 giây theo evidence hiện hành

### 7.1. Auth và phân quyền

“Hệ thống xác thực bằng JWT và phân quyền bằng role kết hợp action mask. Route frontend chỉ hỗ trợ điều hướng; quyền cuối cùng được kiểm tra tại backend qua `PermissionInterceptor`, `@Permission` hoặc `@PreAuthorize`. Bằng chứng nằm ở Chương 3.3, UML-04/UML-08 và run Maven 123/123 ngày 29/07/2026. Phạm vi còn cần bổ sung là E2E data-backed cho 29 route quản trị; một số route còn cần rà soát guard tĩnh, nên em không dùng UI guard làm bằng chứng bảo mật duy nhất.”

### 7.2. Tìm kiếm và tồn phòng

“Khách có thể tìm Province, Ward và Property bằng tiếng Việt có dấu hoặc không dấu, sau đó lọc theo ngày, sức chứa và số phòng. Backend chuẩn hóa chuỗi và tính availability từ phòng active trừ bảo trì và reservation giao cắt. Bằng chứng là UML-09, ERD-02, integration test search/Unicode trong run backend CURRENT. Playwright Home Search hiện BLOCKED do timeout và artifact suggestion, nên em tách kết luận backend với trình duyệt.”

### 7.3. Booking và payment

“Phiên bản hiện tại cho phép đặt một RoomType với quantity lớn hơn một. Backend kiểm tra lại ngày, sức chứa, giá và tồn; thiếu tồn trả HTTP 409. Thanh toán dùng VNPay hoặc simulator, transaction ID có ràng buộc chống trùng. Bằng chứng nằm ở UML-10, ERD-03, migration V10 và test reservation/payment CURRENT. Mixed RoomType trong cùng booking là DEFERRED.”

### 7.4. Hủy và refund

“Khi khách hủy, service kiểm tra booking thuộc principal và trạng thái có cho phép hủy hay không. Refund được lưu thành payment âm với mã `REFUND-{paymentId}`; retry không tạo giao dịch thứ hai. UML-11/UML-17 và payment/reservation tests là evidence. Frontend E2E của luồng callback/hủy chưa chốt nên capability tổng thể vẫn PARTIAL.”

### 7.5. Multi-property và subscription

“`UserProperty` xác định tài khoản được thao tác cơ sở nào, còn `AccountSubscription` và `PlanFeature` xác định giới hạn gói. Hai kiểm tra độc lập để tránh vừa truy cập chéo property vừa vượt quota. Feature gate đã có integration test CURRENT. Tuy nhiên REST lifecycle subscription mới chỉ đọc plan, subscription ACTIVE và feature map; activate/renew/upgrade/revoke/history chưa đầy đủ.”

### 7.6. Import và claim

“Import đã có batch staging, item validation/deduplication và quyền VIEW/CREATE/EXECUTE. Claim đã có request, list, approve và reject, nhưng controller còn dùng ID requester/reviewer cố định. Vì vậy em chỉ đánh giá import là PARTIAL và claim là BLOCKED/PARTIAL; bước tiếp theo là lấy identity từ principal và thêm integration test.”

### 7.7. Chat và notification

“Chat và notification dùng hai endpoint SockJS/STOMP riêng. JWT được kiểm tra ở STOMP CONNECT; chat giới hạn `AI_CHAT:VIEW/CREATE`, notification giới hạn personal queue và admin topic `REPORT:VIEW`. Backend controller/service/channel tests nằm trong 123/123 CURRENT, frontend unit nằm trong 73/73 CURRENT. Authenticated E2E vẫn BLOCKED nên em chưa gọi hai capability là COMPLETE.”

### 7.8. Kết quả kiểm thử

“Ngày 29/07/2026 em chạy `mvnw test` đạt 123/123, frontend unit đạt 73/73 trong 36 file và production build thành công. Playwright phát hiện 71 test trong 12 file nhưng full run vẫn BLOCKED do timeout; Admin core data-backed smoke có 1 fail và 2 test không chạy vì login `admin/admin` bị giữ tại `/admin/login`. Do đó kết luận hiện tại là backend/unit/build CURRENT, Admin E2E BLOCKED. Các số test cũ chỉ giữ làm lịch sử.”

## 8. Checklist chốt rubric

- 100% tiêu chí có dòng mapping.
- Mã/trọng số khớp rubric export.
- Mỗi câu trả lời dưới 60 giây và có boundary.
- Không có claim dựa duy nhất vào roadmap/mockup.
- Không gọi HISTORICAL/BLOCKED là CURRENT/PASS.
- Không mở secret, token, PII hoặc đường dẫn cục bộ.
- Các câu hỏi mixed RoomType, add-on, reviews, favorites, subscription lifecycle và financial reporting có câu trả lời giới hạn rõ ràng.

## 9. Đã đối chiếu với file rubric chính thức

1. D03 có 7 tiêu chí với trọng số 10%, 10%, 10%, 15%, 15%, 10%, 30%.
2. D04 có 7 tiêu chí với thang điểm 10, 40, 10, 5, 10, 5, 20.
3. Nội dung nguyên văn, mapping và readiness nằm trong `docs/thesis-assets/RUBRIC_MATRIX.md`.
4. Các tiêu chí trình bày, thuyết trình và hỏi đáp vẫn `NEEDS_EVIDENCE` vì chưa có DOCX/PDF final, slide hoặc buổi bảo vệ.
5. Không nâng `PARTIAL`, `BLOCKED` hoặc `DEFERRED` thành `READY` nếu chưa có evidence mới.

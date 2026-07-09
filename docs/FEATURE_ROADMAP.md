# FEATURE ROADMAP

## Đánh giá nhanh so với Agoda/Booking

Dự án hiện tại đã có nền móng tốt cho một hệ thống quản lý khách sạn kết hợp đặt phòng online:

- Có đăng nhập, đăng ký, phân quyền, quản lý người dùng.
- Có quản lý phòng, loại phòng, dịch vụ, đặt phòng, thanh toán, hóa đơn.
- Có giao diện khách hàng để tìm khách sạn, xem chi tiết, đặt phòng.
- Có hồ sơ khách hàng và lịch sử chuyến đi.
- Có dashboard, AI assistant và một số màn hình quản trị.

Tuy nhiên, dự án **chưa đạt mức hoàn thiện như Agoda**. Hiện tại phù hợp hơn với mức **MVP nâng cao**: đặt phòng được, quản trị được, nhưng còn thiếu nhiều chức năng quan trọng của một app đặt phòng thực tế như tìm kiếm theo tồn phòng thật, giá động, đánh giá, bản đồ, voucher, thanh toán online, chính sách hủy, xác nhận email, thông báo, quản lý housekeeping, chống overbooking và trải nghiệm mobile chuyên nghiệp.

Mục tiêu roadmap này là đưa dự án từ MVP lên gần chuẩn một app đặt phòng khách sạn thực tế.

---

## Phase 0: Ổn định nền tảng

Mục tiêu: đảm bảo frontend/backend chạy ổn, API thống nhất, dữ liệu tiếng Việt đúng, không còn lỗi build cơ bản.

Chức năng/công việc:

- Chuẩn hóa `environment.apiUrl` cho toàn bộ frontend.
- Chuẩn hóa endpoint public cho khách sạn, phòng, loại phòng.
- Sửa lưu tiếng Việt bằng `NVARCHAR` cho các bảng có dữ liệu tiếng Việt.
- Sửa build lỗi do thiếu file môi trường, thiếu CSS component, import PrimeNG sai phiên bản.
- Thêm fallback sidebar khi API menu lỗi.
- Tách cấu hình dev/prod nếu triển khai thật.

Tiêu chí hoàn thành:

- `npm run build` frontend pass.
- `mvnw test` backend pass.
- Trang chủ, tìm kiếm, chi tiết khách sạn, checkout mở được.
- API public search trả dữ liệu tiếng Việt đúng.

Trạng thái: Đã làm phần lớn.

---

## Phase 1: Authentication và phân quyền thực tế

Mục tiêu: đăng nhập, đăng ký, phân quyền và bảo vệ route hoạt động chắc chắn cho từng vai trò.

Chức năng cần có:

- Đăng nhập khách hàng.
- Đăng nhập admin/staff.
- Đăng ký khách hàng.
- Quên mật khẩu, đặt lại mật khẩu.
- Xác thực email hoặc OTP.
- Đổi mật khẩu trong hồ sơ.
- Refresh token hoặc tự động đăng xuất khi token hết hạn.
- Guard route theo role và permission.
- Ẩn/hiện nút chức năng theo quyền.

Thiếu so với Agoda:

- Đăng nhập Google/Facebook/Apple.
- Xác minh email/phone.
- Quản lý thiết bị đăng nhập.
- Bảo mật captcha hoặc rate limit đăng nhập.

Tiêu chí hoàn thành:

- Khách hàng chỉ vào được profile/booking khi login.
- Staff chỉ thấy chức năng đúng quyền.
- Super Admin quản lý role/permission được.
- Không còn button chức năng hiển thị sai quyền.

---

## Phase 2: Tìm kiếm khách sạn và phòng như app đặt phòng

Mục tiêu: tìm kiếm không chỉ hiển thị khách sạn, mà phải dựa trên ngày ở, số khách, tồn phòng và giá thực tế.

Chức năng cần có:

- Tìm theo thành phố/khu vực/tên khách sạn.
- Chọn ngày nhận phòng, trả phòng, số khách, số phòng.
- Kiểm tra tồn phòng theo ngày để tránh overbooking.
- Lọc theo giá, sao, loại phòng, tiện ích, chính sách hủy, thanh toán tại chỗ.
- Sắp xếp theo đề xuất, giá thấp, sao cao, đánh giá cao.
- Hiển thị số lượng phòng còn lại.
- Hiển thị giá đã gồm/chưa gồm thuế phí rõ ràng.
- Empty state và loading state rõ ràng.

Thiếu so với Agoda:

- Gợi ý địa điểm khi nhập search.
- Bản đồ.
- Khoảng cách tới trung tâm/địa danh.
- Filter theo review score.
- Gợi ý deal, giá tốt hôm nay.
- Lưu tìm kiếm gần đây.

Tiêu chí hoàn thành:

- API search chỉ trả khách sạn/phòng còn trống trong khoảng ngày đã chọn.
- Không thể đặt quá số phòng tồn.
- Filter/sort tác động đúng dữ liệu backend.
- UI responsive tốt trên mobile.

---

## Phase 3: Chi tiết khách sạn và hạng phòng

Mục tiêu: trang chi tiết đủ thông tin để khách ra quyết định như trên Agoda.

Chức năng cần có:

- Gallery ảnh khách sạn và từng hạng phòng.
- Mô tả khách sạn, địa chỉ, sao, tiện ích.
- Danh sách loại phòng kèm ảnh, diện tích, số khách, số giường, chính sách.
- Giá mỗi đêm, tổng giá theo số đêm, thuế phí.
- Điều kiện hủy phòng.
- Chính sách nhận/trả phòng.
- CTA đặt phòng rõ ràng.

Thiếu so với Agoda:

- Đánh giá khách hàng.
- Điểm review theo hạng mục.
- Bản đồ vị trí.
- Câu hỏi thường gặp.
- So sánh nhiều lựa chọn phòng.
- Badge "bán chạy", "giá tốt", "còn ít phòng".

Tiêu chí hoàn thành:

- Chọn phòng từ chi tiết truyền đúng `roomTypeId`, ngày, số khách sang checkout.
- Giá tổng tính đúng theo số đêm.
- Có ảnh thực tế cho khách sạn và phòng.

---

## Phase 4: Checkout và đặt phòng an toàn

Mục tiêu: đặt phòng chắc chắn, có kiểm tra dữ liệu và tránh double booking.

Chức năng cần có:

- Form thông tin khách.
- Tự điền thông tin từ tài khoản.
- Chọn phương thức thanh toán.
- Ghi chú/yêu cầu đặc biệt.
- Kiểm tra số khách không vượt quá sức chứa phòng.
- Kiểm tra ngày trả phòng sau ngày nhận phòng.
- Backend lock/transaction để tránh overbooking.
- Tạo mã đặt phòng.
- Gửi email xác nhận.
- Trang đặt phòng thành công.

Thiếu so với Agoda:

- Thanh toán online bằng thẻ, ví điện tử, QR.
- Voucher/coupon.
- Chính sách hủy trước khi xác nhận.
- Giá cuối cùng gồm thuế/phí/dịch vụ.
- Đặt nhiều phòng trong một booking.

Tiêu chí hoàn thành:

- Không thể đặt phòng đã hết.
- Booking có trạng thái `PENDING` hoặc `CONFIRMED` rõ ràng.
- Khách xem lại được booking trong profile.
- Admin thấy booking mới ngay trong quản trị.

---

## Phase 5: Quản lý đặt phòng cho lễ tân/admin

Mục tiêu: vận hành booking đầy đủ từ xác nhận tới check-out.

Chức năng cần có:

- Danh sách đặt phòng có tìm kiếm/lọc theo ngày, trạng thái, khách hàng.
- Xác nhận đặt phòng.
- Hủy đặt phòng.
- Check-in.
- Check-out.
- Gán phòng cụ thể khi check-in.
- Đổi phòng.
- Gia hạn ngày ở.
- No-show.
- Lịch đặt phòng dạng timeline/calendar.

Thiếu so với app vận hành thực tế:

- Calendar room availability.
- Drag/drop đổi phòng.
- Ghi nhận đặt cọc.
- Tự động chuyển trạng thái quá hạn.
- In phiếu xác nhận/lưu trú.

Tiêu chí hoàn thành:

- Receptionist xử lý được vòng đời booking.
- Không thể check-out khi chưa thanh toán hoặc chưa có xác nhận phù hợp.
- Lịch phòng giúp nhìn nhanh phòng trống/bận.

---

## Phase 6: Thanh toán, hóa đơn và hoàn tiền

Mục tiêu: quản lý tài chính rõ ràng, có hóa đơn và lịch sử thanh toán.

Chức năng cần có:

- Thanh toán tiền mặt.
- Thanh toán chuyển khoản/QR.
- Thanh toán online.
- Ghi nhận nhiều lần thanh toán cho một booking.
- Xuất hóa đơn.
- Tải PDF hóa đơn.
- Gửi hóa đơn qua email.
- Hoàn tiền khi hủy.
- Báo cáo doanh thu theo ngày/tháng.

Thiếu so với Agoda:

- Payment gateway thật.
- Xác thực giao dịch.
- Lưu phương thức thanh toán an toàn.
- Chính sách hoàn tiền tự động.
- Đối soát thanh toán.

Tiêu chí hoàn thành:

- Booking có payment history.
- Hóa đơn tạo được sau thanh toán/check-out.
- Khách tải được hóa đơn từ profile.
- Admin lọc/xuất báo cáo tài chính.

---

## Phase 7: Hồ sơ khách hàng, loyalty và voucher

Mục tiêu: tăng trải nghiệm khách hàng và hỗ trợ giữ chân khách.

Chức năng cần có:

- Hồ sơ khách hàng.
- Lịch sử chuyến đi.
- Hủy booking theo chính sách.
- Tải hóa đơn.
- Danh sách voucher.
- Nhập mã giảm giá ở checkout.
- Tích điểm sau mỗi booking.
- Cấp hạng thành viên.

Thiếu so với Agoda:

- Wallet/voucher center hoàn chỉnh.
- Review sau chuyến đi.
- Wishlist/lưu khách sạn yêu thích.
- Gợi ý cá nhân hóa.
- Referral/share mã giới thiệu.

Tiêu chí hoàn thành:

- Khách tự quản lý booking cơ bản.
- Voucher ảnh hưởng đúng giá checkout.
- Điểm thưởng cập nhật sau booking hoàn tất.

---

## Phase 8: Review, rating và nội dung khách sạn

Mục tiêu: tăng độ tin cậy cho trang bán phòng.

Chức năng cần có:

- Khách đã checkout được đánh giá.
- Chấm điểm sao và viết nhận xét.
- Admin duyệt/ẩn review vi phạm.
- Tính điểm trung bình khách sạn.
- Hiển thị review trong trang chi tiết.
- Quản lý nội dung khách sạn: ảnh, tiện ích, mô tả, chính sách.

Thiếu so với Agoda:

- Review theo nhóm khách.
- Điểm theo hạng mục: sạch sẽ, vị trí, dịch vụ, giá trị.
- Ảnh review từ khách.
- Trả lời review từ khách sạn.

Tiêu chí hoàn thành:

- Chỉ khách có booking hoàn tất mới review được.
- Review ảnh hưởng đến sort/filter.
- Admin quản trị được nội dung public.

---

## Phase 9: Housekeeping, maintenance và vận hành phòng

Mục tiêu: không chỉ bán phòng mà còn vận hành phòng như khách sạn thật.

Chức năng cần có:

- Trạng thái phòng: trống, đã đặt, đang ở, cần dọn, bảo trì.
- Danh sách công việc dọn phòng.
- Giao việc cho housekeeping.
- Báo lỗi thiết bị/phòng.
- Tạo work order bảo trì.
- Đánh dấu hoàn tất dọn phòng/bảo trì.
- Chặn bán phòng đang bảo trì.

Thiếu so với hệ thống vận hành thực tế:

- App mobile cho housekeeping.
- Checklist dọn phòng.
- SLA bảo trì.
- Ảnh trước/sau xử lý.

Tiêu chí hoàn thành:

- Phòng bảo trì không xuất hiện trong search.
- Check-out tự tạo task dọn phòng.
- Phòng chỉ bán lại khi trạng thái sẵn sàng.

---

## Phase 10: Thông báo và giao tiếp

Mục tiêu: người dùng và nhân viên nhận thông tin đúng lúc.

Chức năng cần có:

- Email xác nhận đặt phòng.
- Email hủy/đổi lịch.
- Thông báo admin khi có booking mới.
- Thông báo khách trước ngày check-in.
- Notification center trong app.
- Template email song ngữ.

Thiếu so với Agoda:

- Push notification.
- SMS/Zalo/WhatsApp.
- Inbox/chat với khách sạn.
- Nhắc thanh toán.

Tiêu chí hoàn thành:

- Booking mới gửi thông báo cho khách và admin.
- Hủy/đổi trạng thái có log và notification.
- Người dùng xem được notification history.

---

## Phase 11: AI Assistant và cá nhân hóa

Mục tiêu: AI hỗ trợ tìm phòng, trả lời câu hỏi và phân tích vận hành.

Chức năng cần có:

- Chatbot hỏi đáp thông tin khách sạn/phòng/chính sách.
- Gợi ý phòng theo số khách, ngân sách, ngày ở.
- Gợi ý upsell dịch vụ.
- AI dashboard insight cho admin.
- AI dự báo công suất phòng.

Thiếu so với sản phẩm hiện đại:

- AI dùng dữ liệu thật từ search/booking.
- Memory theo khách hàng.
- RAG trên chính sách khách sạn.
- AI support ticket summary.

Tiêu chí hoàn thành:

- AI không trả lời bịa thông tin tồn phòng/giá.
- AI có thể đưa link tới phòng phù hợp.
- Admin xem insight dựa trên dữ liệu thật.

---

## Phase 12: Báo cáo, phân tích và xuất dữ liệu

Mục tiêu: admin có đủ số liệu để vận hành.

Chức năng cần có:

- Dashboard doanh thu.
- Công suất phòng.
- Booking theo trạng thái.
- Doanh thu theo loại phòng.
- Top khách hàng.
- Top dịch vụ.
- Xuất Excel/PDF.
- Lọc theo ngày, khách sạn, phòng.

Thiếu so với hệ thống quản lý chuyên nghiệp:

- Forecast doanh thu.
- So sánh kỳ trước.
- Heatmap occupancy.
- Báo cáo kênh bán.

Tiêu chí hoàn thành:

- Số liệu lấy từ booking/payment thật.
- Export báo cáo đúng filter.
- Dashboard không hiển thị số liệu mock.

---

## Phase 13: Đa ngôn ngữ, responsive và accessibility

Mục tiêu: trải nghiệm tốt trên desktop/mobile và hỗ trợ song ngữ.

Chức năng cần có:

- Tiếng Việt và tiếng Anh cho toàn bộ màn hình.
- Không còn text hard-code rải rác.
- Mobile layout cho search, detail, checkout, profile.
- Loading skeleton.
- Empty state chuẩn.
- Accessibility cơ bản: label, focus, contrast.

Thiếu so với Agoda:

- Currency switcher.
- Locale date/number.
- Multi-language hotel content.
- App-like mobile UX.

Tiêu chí hoàn thành:

- Chuyển ngôn ngữ không reload lỗi.
- Mobile 320px không vỡ layout.
- Tất cả form có validation message rõ.

---

## Phase 14: Subscription và multi-hotel SaaS

Mục tiêu: nếu dự án hướng tới SaaS cho nhiều khách sạn, cần tách dữ liệu và gói dịch vụ.

Chức năng cần có:

- Super Admin quản lý nhiều khách sạn.
- Hotel Admin chỉ thấy dữ liệu khách sạn của mình.
- Gói subscription: Free, Standard, Premium.
- Giới hạn tính năng theo gói.
- Billing subscription.
- Duyệt khách sạn trước khi public.

Thiếu so với marketplace thực tế:

- Onboarding khách sạn.
- Kiểm duyệt nội dung.
- Hợp đồng/hoa hồng.
- Quản lý payout cho khách sạn.

Tiêu chí hoàn thành:

- Dữ liệu giữa các khách sạn không lẫn nhau.
- Gói dịch vụ khóa/mở chức năng chính xác.
- Super Admin có màn hình duyệt khách sạn.

---

## Phase 15: Testing, bảo mật và triển khai

Mục tiêu: đưa dự án tới mức có thể demo/thực nghiệm ổn định.

Chức năng/công việc:

- Unit test service quan trọng.
- Integration test booking, payment, invoice.
- E2E test luồng khách hàng: search -> detail -> checkout -> profile.
- E2E test luồng lễ tân: confirm -> check-in -> payment -> check-out -> invoice.
- Validate input backend.
- Rate limit auth API.
- Log lỗi tập trung.
- Docker compose cho frontend/backend/database.
- CI build/test.
- Backup database.

Tiêu chí hoàn thành:

- CI pass trước khi merge.
- Không có endpoint public nhạy cảm.
- Deploy được bằng Docker.
- Có hướng dẫn chạy local và production.

---

## Thứ tự ưu tiên đề xuất

Nếu cần làm nhanh để giống app đặt phòng hơn, ưu tiên theo thứ tự:

1. Search availability thật, chống overbooking.
2. Chi tiết khách sạn/phòng có ảnh, tiện ích, giá tổng.
3. Checkout có thuế phí, voucher, chính sách hủy.
4. Lịch sử booking, hủy booking, tải hóa đơn.
5. Admin booking lifecycle đầy đủ.
6. Payment/invoice PDF.
7. Review/rating.
8. Housekeeping/maintenance.
9. Notifications/email.
10. Mobile polish và song ngữ đầy đủ.

---

## Kết luận

Dự án hiện tại **chưa ngang Agoda**, nhưng đã có nền tảng để phát triển theo hướng đó. Để đạt cảm giác giống app Agoda/Booking, cần tập trung trước vào 4 điểm quan trọng nhất:

- Tìm kiếm theo tồn phòng thật.
- Trang chi tiết khách sạn/phòng giàu thông tin.
- Checkout có giá cuối, chính sách, thanh toán rõ ràng.
- Hệ sinh thái sau đặt phòng: lịch sử, hủy, hóa đơn, review, thông báo.

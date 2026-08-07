# Ma trận rubric chính thức

Ngày cập nhật: 2026-07-28

## Quy ước đọc

- D03 và D04 không cung cấp mã tiêu chí riêng; bảng dùng `D03-Cn`/`D04-Cn` làm **mã truy vết nội bộ** theo số thứ tự trong file, không gọi đó là mã chính thức.
- Cột “Yêu cầu nguyên văn” giữ nội dung trích từ DOCX export, chỉ chuẩn hóa khoảng trắng do ô bảng bị lặp khi đọc OOXML.
- `READY` nghĩa là đã có source và evidence hiện hành để trả lời; điểm số cuối cùng vẫn do giảng viên/hội đồng chấm.
- `NEEDS_EVIDENCE` nghĩa là nội dung có thể trình bày nhưng còn thiếu artifact bảo vệ, screenshot, E2E hoặc xác nhận của giảng viên.

## D03 - Mau-1-Rubric-Project Mon Hoc

| Trace ID | STT | Trọng số | Yêu cầu nguyên văn | Câu trả lời 30-60 giây | Vị trí báo cáo | Evidence trực tiếp | Boundary | Readiness |
| --- | ---: | ---: | --- | --- | --- | --- | --- | --- |
| D03-C1 | 1 | 10% | Tiêu chí 1: Độ khó của đề tài | Đề tài bao phủ nhiều actor và chuỗi search, availability, booking, payment, vận hành lưu trú, multi-property và hỗ trợ. Độ khó nằm ở việc giữ nhất quán trạng thái, tenant context, quyền backend và idempotency thay vì chỉ dựng màn hình. | THESIS §1.1-1.4; §3.1-3.9 | UML-01, UML-04, UML-09-19; THESIS_CODE_EVIDENCE | Rubric không chấm tự động độ khó; không tự quy đổi thành điểm. | READY |
| D03-C2 | 2 | 10% | Tiêu chí 2: Tính thực tiễn của đề tài | Hệ thống giải quyết nghiệp vụ tìm phòng, đặt phòng, thanh toán, hủy/hoàn tiền và vận hành khách sạn. Evidence được gắn với API, entity, test và giới hạn hiện hành, không dùng roadmap làm bằng chứng. | THESIS §1.1-1.3; §4.3-4.6 | FEATURE_SUMMARY; API_SPEC; backend 122/122 | Mixed RoomType, review, favorites và full subscription lifecycle chưa phải phạm vi hoàn thành. | READY |
| D03-C3 | 3 | 10% | Tiêu chí 3: Tính đúng đắn của phương pháp nghiên cứu | Phương pháp gồm khảo sát yêu cầu, đối chiếu source/migration/test, thiết kế UML/ERD, cài đặt theo lớp và kiểm chứng bằng unit/integration/build. Các kết luận phân biệt CURRENT, HISTORICAL và BLOCKED. | THESIS §1.4; §3; §4.8-4.10 | THESIS_CODE_EVIDENCE; THESIS_TEST_EVIDENCE; 24/24 diagram render | Playwright full run còn BLOCKED nên chưa kết luận đầy đủ hành trình trình duyệt. | READY/PARTIAL |
| D03-C4 | 4 | 15% | Tiêu chí 4: Chất lượng của Giải pháp thi công, cài đặt, mô phỏng | Giải pháp có frontend Angular, backend Spring Boot, dữ liệu quan hệ, JWT/RBAC và các service nghiệp vụ. Backend test 122/122, frontend unit 66/66 và production build pass chứng minh phần lõi; E2E và claim identity còn gap. | THESIS §2.1-2.7; §3.2-3.10; §4.1-4.7 | Security/integration/service tests; API_SPEC; UML/ERD | Playwright timeout; PropertyClaimController còn requester/reviewer ID cố định; subscription mutation chưa đủ. | PARTIAL |
| D03-C5 | 5 | 15% | Tiêu chí 5: Chất lượng về hình thức (cấu trúc, định dạng, chính tả) | Báo cáo đã có năm chương, caption và 13-slot manifest; format rules bám D07: A4, Times New Roman, lề 3/2/2/2 cm, line 1.5, đánh số giữa chân trang. Bản DOCX/PDF cuối chưa render nên chưa tự nhận đạt hoàn toàn. | THESIS §1.5; WHOLE_DOCUMENT_MANIFEST; THESIS_FORMAT_RULES | D07 export; UML/ERD QA; checklist 13 slot | DOCX/PDF và mục lục phân trang chưa chốt; render source template bị BLOCKED do thiếu LibreOffice. | NEEDS_EVIDENCE |
| D03-C6 | 6 | 10% | Tiêu chí 6: Chất lượng của bài thuyết trình | Nội dung bảo vệ có thể trình bày theo Claim-Evidence-Reason-Boundary-Value, tập trung 15 phút vào mục tiêu, phương pháp, giải pháp, demo và kết quả. Chưa có file slide và chưa có lần bảo vệ để đánh giá chất lượng trình bày. | THESIS §4.10; RUBRIC_RESPONSE_GUIDE §3-§7 | D05 hướng dẫn; rubric response guide | Không suy ra kỹ năng nói, thời lượng thực tế hoặc điểm thuyết trình từ mã nguồn. | NEEDS_EVIDENCE |
| D03-C7 | 7 | 30% | Tiêu chí 7: Chất lượng trả lời câu hỏi của hội đồng | Câu trả lời chuẩn luôn chỉ ra contract hiện tại, evidence mở được và boundary: ví dụ booking chỉ một RoomType với quantity, payment VNPay/simulator, claim còn fixed ID và E2E BLOCKED. | RUBRIC_RESPONSE_GUIDE §7; THESIS §5.2 | Bộ 8 câu trả lời 30-60 giây; evidence registry | Chưa có phiên hỏi đáp thật; không tự ghi điểm hoặc 100% câu hỏi. | NEEDS_EVIDENCE |

## D04 - Mau-2-Rubric-KLTN-Edit

| Trace ID | STT | Trọng số | Yêu cầu nguyên văn | Câu trả lời 30-60 giây | Vị trí báo cáo | Evidence trực tiếp | Boundary | Readiness |
| --- | ---: | ---: | --- | --- | --- | --- | --- | --- |
| D04-C1 | 1 | 10 | Tính thực tiễn của đề tài, sự hiểu biết về vấn đề nghiên cứu | Hệ thống mô hình hóa các vấn đề thực tế của khách sạn: tra cứu tồn, đặt phòng, thanh toán, vận hành lưu trú và quản lý nhiều cơ sở. Phạm vi và trạng thái từng capability được ghi rõ trong Chương 1 và ma trận truy vết. | THESIS §1.1-1.3; §4.3-4.6 | FEATURE_SUMMARY; capability matrix; backend integration tests | Các capability deferred không được trình bày như sản phẩm đã hoàn thiện. | READY |
| D04-C2 | 2 | 40 | Tính đúng đắn và hợp lí của phương pháp nghiên cứu, của thiết kế, của giải pháp được nêu ra trong luận văn. Mức độ hoàn thiện của sản phẩm, mức độ hoàn thành công việc của sinh viên. | Thiết kế tách frontend, backend, service và dữ liệu; availability dùng khoảng [checkIn, checkOut), reservation/payment có state và idempotency, backend kiểm tra JWT/permission/tenant. Các diagram được đối chiếu source và test. | THESIS §2; §3.2-3.9; §4.1-4.6 | 19 UML + 5 ERD; 122/122 backend; 66/66 frontend | E2E BLOCKED, claim principal chưa hoàn chỉnh, subscription lifecycle REST còn thiếu; vì vậy mức hoàn thiện tổng thể là PARTIAL. | PARTIAL |
| D04-C3 | 3 | 10 | Chất lượng của bài thuyết trình | Có kịch bản demo theo capability và bộ trả lời rubric, nhưng chưa có slide/buổi bảo vệ để xác minh khả năng trình bày. | THESIS §4.10; RUBRIC_RESPONSE_GUIDE | D05; release/checklist assets | Không dùng screenshot hoặc source để tự kết luận điểm thuyết trình. | NEEDS_EVIDENCE |
| D04-C4 | 4 | 5 | Khả năng đọc sách ngoại ngữ tham khảo | Báo cáo sử dụng các tài liệu kỹ thuật tiếng Anh về Angular, Spring Boot, JWT, REST và idempotency; các nguồn được liệt kê ở phần tài liệu tham khảo và gắn với quyết định thiết kế. | THESIS §2.1-2.7; Tài liệu tham khảo | THESIS references [1], [8] và các nguồn kỹ thuật | Khả năng đọc/diễn giải của sinh viên cần được kiểm tra qua hỏi đáp; rubric không có test tự động. | NEEDS_EVIDENCE |
| D04-C5 | 5 | 10 | Khả năng tổng hợp kiến thức, viết luận văn | Báo cáo tổng hợp lý thuyết, yêu cầu, UML/ERD, cài đặt và kiểm thử thành năm chương; mỗi capability liên kết source và trạng thái. | THESIS §1-§5; FEATURE_TRACEABILITY_MATRIX | THESIS, UML.md, ERD.md, API_SPEC.md | Cần hoàn thiện DOCX/PDF và rà văn phong/chính tả trước khi tự nhận READY tuyệt đối. | READY/PARTIAL |
| D04-C6 | 6 | 5 | Chất lượng về hình thức của luận văn (Cấu trúc, định dạng, chính tả,…) | Cấu trúc và quy cách đã đối chiếu D07, gồm A4, Times New Roman, lề 3/2/2/2 cm, line 1.5, heading/caption và 13 slot điện tử. Bản final chưa qua render. | THESIS_FORMAT_RULES; WHOLE_DOCUMENT_MANIFEST | D07 export; diagram QA; format checklist | Render DOCX/PDF, mục lục và screenshot hiện hành còn pending. | NEEDS_EVIDENCE |
| D04-C7 | 7 | 20 | Chất lượng trả lời các câu hỏi của hội đồng | Bộ trả lời được viết theo Claim-Evidence-Reason-Boundary-Value và nói thẳng các giới hạn: E2E BLOCKED, claim fixed ID, subscription partial, mixed RoomType deferred. | RUBRIC_RESPONSE_GUIDE; THESIS §5.2 | 8 mẫu trả lời 30-60 giây; evidence registry | Chưa có hỏi đáp thật; không tự quy đổi thành 100% hoặc điểm số. | NEEDS_EVIDENCE |

## Coverage check

- D03: 7/7 tiêu chí có trọng số, nguyên văn, câu trả lời, vị trí báo cáo, evidence, boundary và readiness.
- D04: 7/7 tiêu chí có trọng số, nguyên văn, câu trả lời, vị trí báo cáo, evidence, boundary và readiness.
- Coverage mapping hiện đạt 14/14 dòng nguồn; trạng thái `NEEDS_EVIDENCE`/`PARTIAL` được giữ nguyên, không nâng thành `READY` để làm đẹp kết quả.

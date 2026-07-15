# Báo cáo Home Search và Location Autocomplete - 2026-07-15
## Phạm vi

Phase này chỉ sửa Home/Public Search của LuxeStay trên source hiện tại. Không thay đổi Admin, không dùng Elasticsearch, không lấy mã nguồn, ảnh, nội dung hoặc API từ Agoda.

## API và dữ liệu

1. API tương thích cũ: `GET /api/public/locations/search?keyword=...` vẫn hoạt động và dùng chung service tìm kiếm mới.
2. API nhóm mới: `GET /api/public/search/suggestions?keyword=...&limit=...&latitude=...&longitude=...`.
3. API điểm đến: `GET /api/public/popular-destinations?limit=8`, số cơ sở được đếm từ dữ liệu `APPROVED` và `ACTIVE`.
4. `SearchSuggestionGroupsDTO` gồm `provinces`, `wards`, `properties`, `landmarks`. `LocationSuggestionDTO` bổ sung `slug`, `propertyType`, ảnh, review và khoảng cách.
5. Province query tìm `nameVi`, `normalizedName`, `fullPath`; Ward query tìm cùng trường và trả Province cha trực tiếp, không tạo District.
6. Property query tìm tên chuẩn hóa, địa chỉ chuẩn hóa, code, slug, Province và Ward; chỉ trả cơ sở public hợp lệ.
7. Giới hạn popup: 5 Province, 8 Ward, 10 Property và 5 Landmark khi nguồn Landmark được bổ sung.
8. Flyway `V6__public_discovery_index.sql` đã chạy trên SQL Server và tạo `IX_hotels_public_discovery`; không tạo index key trên `NVARCHAR(MAX)`.

## Kết quả API thực tế

| Kiểm tra | Kết quả |
| --- | --- |
| `my tho` | 1 Ward, 1 Property |
| `Mỹ Tho` | Kết quả tương đương tìm không dấu |
| `Ocean Pearl` | Có nhóm Property, tối đa 10 kết quả |
| `21 duong vuon xanh` | 1 Property theo địa chỉ |
| Popular Destinations | 8 Province, số lượng lấy từ database |
| Location import | 34 Province, 6.283 Ward; 0 lỗi import |

## Frontend

- Autocomplete dùng Reactive Forms, `debounceTime(350)`, `distinctUntilChanged`, `switchMap`, retry và hủy request cũ.
- Keyword rỗng hiển thị Recent Search và Popular Destinations hai cột; keyword từ 2 ký tự chỉ hiển thị nhóm kết quả.
- Hỗ trợ Arrow Up/Down, Enter, Escape, Tab, click ngoài, loading skeleton, lỗi, retry và empty state.
- Highlight được render bằng text node/`mark`, không dùng `innerHTML`.
- Search State dùng chung giữ Province, Ward, Property, ngày, khách và số phòng; chọn Property đi thẳng `/hotel/{id}`.
- Recent Search giới hạn 8 bản ghi, chống trùng, cho xóa từng mục/toàn bộ và tự sửa ngày đã qua.
- Popular API dùng `shareReplay` trong service để Home và popup không tạo hai truy vấn SQL giống nhau.
- Callback RxJS gọi `markForCheck()` để cập nhật đúng trong chế độ Angular zoneless.
- Home dùng ảnh local demo, dữ liệu Popular/Featured từ backend và không còn danh sách/số lượng khuyến mãi hard-code.
- Tab chưa có backend bị disable và gắn “Sắp ra mắt”.

## Kiểm thử

- Backend: 34 test, 0 failure, 0 error; Maven build thành công.
- Frontend production build: thành công, bundle 2,44 MB; còn cảnh báo budget 441,59 kB và hai dependency CommonJS cũ.
- Playwright Home Search: 10/10 pass trong 1,3 phút với một worker.
- E2E đã kiểm tra focus rỗng, có/không dấu, tên/địa chỉ Property, chọn Province/Ward/Property, giữ booking params, bàn phím, lỗi/retry, empty state và mobile overflow.
- Ảnh sau cải tiến: `docs/screenshots/home-search-after-desktop.png` và `docs/screenshots/home-search-after-mobile.png`.
- Ảnh trước là ảnh tham khảo do người dùng cung cấp, không sao chép vào source để tránh đưa tài sản Agoda vào dự án.

## Phần chưa hoàn thành

- Nhóm Landmark vẫn trả mảng rỗng vì schema hiện tại chưa có nguồn Landmark hợp lệ.
- “Cơ sở đã xem gần đây”, geolocation và “Tiếp tục đặt phòng” chưa thêm vì phase này chưa có API public ổn định tương ứng.
- Chưa tối ưu cảnh báo bundle Angular và CommonJS; chúng không làm build thất bại.

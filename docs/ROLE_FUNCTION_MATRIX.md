# Ma trận role–function

Nguồn: dữ liệu thực tế trong SQL Server `HotelDB`, bảng `app_role`, `app_function`, `app_role_permission`.

## Action mask

| Bit | Hành động |
|---:|---|
| 1 | VIEW |
| 2 | CREATE |
| 4 | UPDATE |
| 8 | DELETE |
| 16 | EXPORT |
| 32 | APPROVE |

Mask dùng phép OR theo bit: `3 = VIEW + CREATE`, `7 = VIEW + CREATE + UPDATE`, `15 = VIEW + CREATE + UPDATE + DELETE`, `31 = VIEW + CREATE + UPDATE + DELETE + EXPORT`, `47 = VIEW + CREATE + UPDATE + DELETE + APPROVE`, `63 = toàn bộ`.

## Ma trận quyền vận hành

| Role | Function | Mask | Quyền |
|---|---|---:|---|
| ACCOUNTANT | INVOICE | 31 | VIEW, CREATE, UPDATE, DELETE, EXPORT |
| ACCOUNTANT | REPORT | 1 | VIEW |
| HOTEL_ADMIN | CHAT | 15 | VIEW, CREATE, UPDATE, DELETE |
| HOTEL_ADMIN | CUSTOMER | 15 | VIEW, CREATE, UPDATE, DELETE |
| HOTEL_ADMIN | HOTEL_SERVICE | 15 | VIEW, CREATE, UPDATE, DELETE |
| HOTEL_ADMIN | INVOICE | 31 | VIEW, CREATE, UPDATE, DELETE, EXPORT |
| HOTEL_ADMIN | REPORT | 1 | VIEW |
| HOTEL_ADMIN | RESERVATION | 47 | VIEW, CREATE, UPDATE, DELETE, APPROVE |
| HOTEL_ADMIN | ROOM | 15 | VIEW, CREATE, UPDATE, DELETE |
| HOTEL_ADMIN | ROOM_TYPE | 15 | VIEW, CREATE, UPDATE, DELETE |
| HOTEL_MANAGER | CHAT | 15 | VIEW, CREATE, UPDATE, DELETE |
| HOTEL_MANAGER | CUSTOMER | 15 | VIEW, CREATE, UPDATE, DELETE |
| HOTEL_MANAGER | HOTEL_SERVICE | 15 | VIEW, CREATE, UPDATE, DELETE |
| HOTEL_MANAGER | INVOICE | 31 | VIEW, CREATE, UPDATE, DELETE, EXPORT |
| HOTEL_MANAGER | REPORT | 1 | VIEW |
| HOTEL_MANAGER | RESERVATION | 47 | VIEW, CREATE, UPDATE, DELETE, APPROVE |
| HOTEL_MANAGER | ROOM | 15 | VIEW, CREATE, UPDATE, DELETE |
| HOTEL_MANAGER | ROOM_TYPE | 15 | VIEW, CREATE, UPDATE, DELETE |
| RECEPTIONIST | CHAT | 7 | VIEW, CREATE, UPDATE |
| RECEPTIONIST | CUSTOMER | 7 | VIEW, CREATE, UPDATE |
| RECEPTIONIST | INVOICE | 3 | VIEW, CREATE |
| RECEPTIONIST | REPORT | 1 | VIEW |
| RECEPTIONIST | RESERVATION | 7 | VIEW, CREATE, UPDATE |
| RECEPTIONIST | ROOM | 5 | VIEW, UPDATE |
| RECEPTIONIST | ROOM_TYPE | 1 | VIEW |

## Quyền quản trị

`SUPER_ADMIN` và `ADMIN` có mask `63` trên mọi function sau:

- `ADMIN_PROPERTIES`
- `AI_CHAT`
- `CHAT`
- `CUSTOMER`
- `HOTEL_SERVICE`
- `INVOICE`
- `PROPERTY_APPROVALS`
- `PROPERTY_OWNERS`
- `PROPERTY_REGISTRATIONS`
- `PROPERTY_ROOM_TYPES`
- `PROPERTY_ROOMS`
- `PROPERTY_STAFF`
- `REPORT`
- `RESERVATION`
- `ROLE`
- `ROLE_PERMISSION`
- `ROOM`
- `ROOM_TYPE`
- `SOFTWARE_CONTRACTS`
- `SUBSCRIPTION_ORDERS`
- `SUBSCRIPTION_PAYMENTS`
- `SUBSCRIPTION_PLANS_ADMIN`
- `SYSTEM`
- `UNSUBSCRIBED_OWNERS`
- `USER`

## Role không có permission tĩnh

| Role | Trạng thái | Ghi chú |
|---|---|---|
| CUSTOMER | ACTIVE | Không có bản ghi trong `app_role_permission`; truy cập qua API khách hàng/public |
| PROPERTY_OWNER | ACTIVE | Không có bản ghi trong `app_role_permission`; phạm vi cơ sở lưu tại `user_properties` và `users.hotel_id` |

## Dữ liệu bất thường cần xử lý

- Role `E2E_115913` trạng thái `INACTIVE` còn permission `ROOM` mask `5`; dữ liệu test tồn dư.
- `receptionist1` có `users.hotel_id = 1` nhưng không có bản ghi `user_properties`. Code dùng scope mới dựa trên `user_properties` có thể trả `403` dù permission mask hợp lệ.
- `manager1` cùng tình trạng chỉ có legacy scope `users.hotel_id = 1`.
- `PROPERTY_OWNER` có property scope nhưng không có permission tĩnh; endpoint dùng `@Permission` sẽ từ chối role này nếu không có xử lý riêng.
- DB hiện không có function `RESERVATION_PAYMENT`, dù seed permission có tham chiếu enum này; `ensurePermission` bỏ qua khi function chưa tồn tại.
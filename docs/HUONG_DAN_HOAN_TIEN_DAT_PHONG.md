# HUONG DAN HOAN TIEN DAT PHONG

Tai lieu nay ap dung cho **tien dat phong cua khach san**, khong ap dung cho hoan tien goi SaaS/phi nen tang.

## 1. Khi nao booking duoc hoan tien?

Booking co the tao yeu cau hoan tien khi dap ung tat ca dieu kien sau:

1. Booking da co giao dich thanh toan goc hop le.
2. Giao dich goc la khoan thu tien (`DEBIT`), khong phai giao dich hoan tien hay dieu chinh thu cong.
3. Giao dich khong nam trong trang thai can doi soat du lieu cu.
4. So tien yeu cau lon hon `0 VND`.
5. Tong tien da hoan va dang cho xu ly khong vuot qua so tien cua giao dich goc.
6. Nguoi yeu cau la khach da dat booking, hoac tai khoan co quyen tai dung co so luu tru.

Khi khach huy mot booking da thanh toan, he thong tu dong tao yeu cau cho phan tien con co the hoan. Huy booking **khong co nghia tien da ve tai khoan ngay**. Yeu cau con phai duoc duyet va gui sang cong thanh toan.

Booking da `CHECKED_IN`, `CHECKED_OUT`, `COMPLETED`, `REJECTED`, `EXPIRED` hoac `NO_SHOW` khong the huy theo luong huy booking thong thuong, vi vay khong tu dong sinh hoan tien theo luong nay.

## 2. Ai thuc hien tung buoc?

| Vai tro | Thao tac |
|---|---|
| Khach hang | Huy booking hoac gui yeu cau bang ma giao dich goc; theo doi trang thai |
| Le tan | Ho tro kiem tra booking/huy booking neu duoc cap quyen; mac dinh khong duyet hoan tien |
| Chu co so / Quan ly khach san | Xem, duyet va gui yeu cau sang cong thanh toan |
| Ke toan | Xem, duyet va gui yeu cau sang cong thanh toan |
| Cong thanh toan | Xu ly va tra ket qua thanh cong/that bai |

Tai khoan xu ly can co quyen `PROPERTY_REFUND` voi thao tac `VIEW` va `APPROVE`.

## 3. Cach 1 - Hoan tien tu dong khi khach huy booking

### Buoc cua khach hang

1. Dang nhap tai [LuxStay](https://luxustay.duckdns.org).
2. Mo trang lich su/danh sach dat phong.
3. Chon booking can huy.
4. Bam **Huy dat phong**.
5. Chon ly do huy hop le va xac nhan.
6. Kiem tra booking da chuyen sang `CANCELLED`.
7. Mo trang **Hoan tien** tai `/profile/refunds` de theo doi yeu cau.

Sau khi huy thanh cong, he thong tim cac giao dich thu tien hop le cua booking va tu dong tao yeu cau hoan cho so du con lai.

### Buoc cua quan ly hoac ke toan

1. Dang nhap tai khoan quan ly/ke toan.
2. Mo **Quan ly > Hoan tien**, duong dan `/management/refunds`.
3. Chon dung **Co so**.
4. Bam **Tai lai**.
5. Doi chieu ma yeu cau, ma giao dich goc, booking va so tien.
6. Tai trang thai **Cho duyet**, bam **Duyet**.
7. Khi trang thai thanh **Dang cho cong thanh toan**, bam **Gui sang cong**.
8. Cho cong thanh toan callback va tai lai danh sach.
9. Chi thong bao da hoan tien cho khach khi trang thai la **Thanh cong (`SUCCEEDED`)**.

## 4. Cach 2 - Khach tu gui yeu cau bang ma giao dich

Dung cach nay khi booking khong huy theo luong tu dong, hoac can gui lai mot yeu cau hop le cho giao dich con so du.

1. Khach dang nhap.
2. Mo `/profile/refunds`.
3. Nhap **Ma giao dich goc** cua lan thanh toan dat phong.
4. Nhap **So tien VND** can hoan.
5. Nhap ly do ro rang.
6. Bam **Gui yeu cau**.
7. Theo doi trang thai va bam **Cap nhat** khi can.
8. Quan ly/ke toan tiep tuc duyet va gui sang cong theo muc 3.

Khong dung ma booking thay cho ma giao dich. So tien khong duoc vuot qua so du co the hoan tren may chu.

## 5. Y nghia trang thai

| Trang thai | Y nghia | Can lam gi |
|---|---|---|
| `REQUESTED` | Da tao, cho duyet | Quan ly/ke toan kiem tra va duyet |
| `PENDING_APPROVAL` | Dang cho phe duyet | Nguoi co quyen thuc hien duyet |
| `POLICY_BLOCKED` | Bi chan boi chinh sach | Kiem tra dieu kien/chinh sach, khong gui cong |
| `PENDING_PROVIDER` | Da duyet, cho cong thanh toan | Bam **Gui sang cong** hoac cho xu ly dang dien ra |
| `SUCCEEDED` | Cong thanh toan xac nhan thanh cong | Hoan tat, thong bao khach |
| `FAILED` | Cong thanh toan bao that bai | Kiem tra cau hinh/cong va xu ly lai theo quy trinh |
| `CANCELLED` | Yeu cau da huy truoc khi gui cong | Tao yeu cau moi neu van du dieu kien |

## 6. Cach tinh so tien con co the hoan

```text
So du co the hoan = So tien giao dich goc - Tong cac khoan da hoan thanh cong

So tien co the tao yeu cau moi
= So du co the hoan
- Tong cac yeu cau REQUESTED/PENDING_APPROVAL/PENDING_PROVIDER
```

Vi du: giao dich goc `1.000.000 VND`, da hoan thanh cong `300.000 VND`, dang cho xu ly `200.000 VND`. Yeu cau moi toi da la `500.000 VND`.

## 7. Checklist quay video demo an toan

Nen dung moi truong `SIMULATOR` de tranh phat sinh tien that.

1. Tao booking test va thanh toan bang simulator.
2. Ghi lai ma booking va ma giao dich goc.
3. Quay man hinh khach huy booking.
4. Mo `/profile/refunds`, cho thay yeu cau va trang thai cho duyet.
5. Chuyen sang tai khoan quan ly/ke toan.
6. Mo `/management/refunds`, chon co so va tai danh sach.
7. Quay thao tac **Duyet**.
8. Quay thao tac **Gui sang cong**.
9. Neu la simulator, xac nhan ket qua simulator theo giao dien he thong.
10. Tai lai va quay trang thai `SUCCEEDED`.
11. Quay lai tai khoan khach, bam **Cap nhat** de thay ket qua thanh cong.

Khong quay video voi moi truong `PRODUCTION` neu chua duoc phep thuc hien giao dich tien that.

## 8. Xu ly loi thuong gap

- **Khong thay yeu cau sau khi huy:** booking co the chua co giao dich thu tien hop le, giao dich da hoan het, hoac can bam **Tai lai** va chon dung co so.
- **Bao vuot so du:** giam so tien; kiem tra cac yeu cau dang cho va cac khoan da hoan.
- **Khong thay nut Duyet:** tai khoan thieu quyen `PROPERTY_REFUND/APPROVE`, hoac yeu cau khong o trang thai cho duyet.
- **Khong thay nut Gui sang cong:** yeu cau chua duoc duyet hoac khong o `PENDING_PROVIDER`.
- **Cong thanh toan that bai:** kiem tra provider, moi truong, merchant/credential va callback; khong danh dau thanh cong bang tay.
- **Gui trung yeu cau:** he thong dung khoa idempotency de ngan ghi nhan trung; khong thay doi noi dung khi dung lai cung mot khoa.

## 9. Dieu kien de xac nhan voi khach

Chi noi **da hoan tien thanh cong** khi he thong hien `SUCCEEDED`. Trang thai `REQUESTED`, `PENDING_APPROVAL` hay `PENDING_PROVIDER` chi co nghia la dang xu ly, chua xac nhan tien da duoc cong thanh toan hoan ve.

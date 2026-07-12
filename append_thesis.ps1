$content = @'

## Chuong X. T? Ð?ng B? Sung D? Li?u Khách S?n & Thu?t Toán Deduplication

### 1. V?n d? Ð?t Ra & Phuong pháp Gi?i Quy?t
Trong quá trình kh?i t?o m?t n?n t?ng OTA, v?n d? "Cold Start" (Thi?u d? li?u ban d?u) là m?t thách th?c r?t l?n. Vi?c yêu c?u hàng ngàn ch? co s? t? dang ký t? d?u t?n r?t nhi?u ngu?n l?c.
Tuy nhiên, h? th?ng không du?c phép thu th?p d? li?u trái phép (Scraping) t? các bên th? ba (OTA khác) vì v?n d? b?n quy?n và d?o d?c.

**Gi?i pháp:**
Tích h?p API cung c?p D? Li?u M? (Open Data) ho?c d?ch v? cung c?p thông tin POI h?p pháp (nhu Nominatim, Google Places) d? t?o d?ng danh m?c co s? luu trú n?n t?ng. H? th?ng s? d?ng m?t Provider Abstraction Layer (Interface AccommodationDataProvider), cho phép hoán d?i provider tùy theo c?u hình.

### 2. Thu?t Toán Ch?ng Trùng L?p 5 C?p (Deduplication)
Khi l?y d? li?u t? Provider, m?t r?i ro l?n là nh?p nhi?u l?n m?t co s?, ho?c co s? dã t?n t?i trong DB. Quá trình ki?m tra trùng du?c th?c hi?n 5 m?c:
- **M?c 1 (External ID)**: Trùng mã d?nh danh cung c?p b?i Provider (EXACT_DUPLICATE).
- **M?c 2 (Tên + T?nh + Phu?ng)**: So kh?p d?a trên Tên chu?n hóa (Normalized Name) trong cùng m?t khu v?c hành chính (POSSIBLE_DUPLICATE).
- **M?c 3 (Ði?n tho?i)**: N?u Provider có cung c?p SÐT, ki?m tra trùng SÐT.
- **M?c 4 (Website)**: Trùng domain trang web chính th?c.
- **M?c 5 (Không Gian - Geo Spatial)**: S? d?ng Haversine tính toán kho?ng cách 2 co s?. N?u kho?ng cách < 50m và tên có d? tuong d?ng (String Distance) cao thì du?c li?t vào POSSIBLE_DUPLICATE.

T?t c? các b?n ghi tìm du?c d?u luu vào b?ng t?m (property_import_items). Qu?n tr? viên ph?i vào h? th?ng xác nh?n (Link/Ignore) tru?c khi import chính th?c.

### 3. Nh?n Quy?n S? H?u (Claim Ownership)
D? li?u t? d?ng nh?p ch? t?o ra m?t *H? so Co S? (Property Profile)* ? tr?ng thái IMPORTED_PENDING_REVIEW mà chua có phòng, không có giá và không ai có quy?n qu?n lý.
Ch? co s? có th? truy c?p h? th?ng, tìm khách s?n c?a mình và g?i yêu c?u PropertyClaimRequest. Sau khi xác minh pháp lý (Gi?y phép kinh doanh, v.v.), Qu?n tr? viên duy?t và h? th?ng gán role OWNER cho tài kho?n yêu c?u. T? dó, ch? co s? có th? bán phòng.
'@
Add-Content -Path "docs\THESIS.md" -Value $content

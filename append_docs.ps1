$changelog = @'

## [Unreleased]
### Added
- Tính nang T? d?ng Nh?p D? Li?u (Automated Property Import) t? ngu?n tr?c tuy?n (Nominatim/OSM).
- Các th?c th? m?i: PropertyImportBatch, PropertyImportItem, PropertyClaimRequest, PropertyExternalPhoto.
- Co ch? Deduplication 5 c?p ki?m tra trùng l?p co s? (Mã ngoài, Tên, SÐT, Website, T?a d?).
- Lu?ng duy?t quy?n s? h?u co s? (Claim Ownership) cho các co s? du?c import t? d?ng (tr?ng thái IMPORTED_PENDING_REVIEW).
- UI/UX Qu?n lý Import và Claim cho Admin.
- Component yêu c?u Claim cho phía Khách Hàng (User/Client) trên trang chi ti?t khách s?n.
'@
Add-Content -Path "docs\CHANGELOG.md" -Value $changelog

$logbook = @'

## [2026-07-11] - Tri?n khai T? Ð?ng B? Sung D? Li?u
- **B?i c?nh**: H? th?ng c?n b? sung d? li?u co s? luu trú ban d?u mà không vi ph?m chính sách crawling.
- **Gi?i pháp**: Xây d?ng ki?n trúc AccommodationDataProvider h? tr? l?y d? li?u t? OpenStreetMap (Nominatim).
- **Th?c hi?n**:
  - Thi?t k? các b?ng staging và logs (batch, item) d? admin ki?m duy?t tru?c khi dua vào b?ng hotels chính.
  - Implement thu?t toán deduplicate don gi?n ? m?c Service.
  - Xây d?ng lu?ng Claim Ownership d? ch? khách s?n có th? nh?n quy?n qu?n lý (c?p quy?n OWNER).
  - C?p nh?t ERD, UML, API_SPEC và THESIS.
'@
Add-Content -Path "docs\LOGBOOK.md" -Value $logbook

$readme = @'

### C?u hình Import D? Li?u (Property Import)
H? th?ng h? tr? l?y d? li?u m? t? các provider bên th? ba. B?n có th? c?u hình thông qua pplication.yml:
`yaml
app:
  accommodation-import:
    enabled: true
    provider: NOMINATIM
    max-results-per-batch: 100
    request-delay-ms: 1000
    auto-publish: false
`
Luu ý: Tuân th? API Rate Limit c?a nhà cung c?p, ví d? Nominatim gi?i h?n 1 request/giây.
'@
Add-Content -Path "README.md" -Value $readme

$content = @'

## 6. PHÂN H? IMPORT D? LI?U T? Ð?NG & CLAIM CO S?

### 6.1. Bi?u d? Use Case (Import & Claim)
`plantuml
@startuml
left to right direction
actor "Khách hàng" as Guest
actor "Qu?n tr? viên (Super Admin)" as Admin

rectangle "Phân h? Import & Claim" {
  usecase "Tìm ki?m & L?c D? li?u t? API" as UC1
  usecase "Xem Preview và Sàng l?c Trùng L?p" as UC2
  usecase "Import d? li?u vào h? th?ng" as UC3
  usecase "G?i yêu c?u nh?n quy?n (Claim)" as UC4
  usecase "Duy?t yêu c?u Claim" as UC5
}

Admin --> UC1
Admin --> UC2
Admin --> UC3
Admin --> UC5
Guest --> UC4
@enduml
`

### 6.2. Bi?u d? Tu?n t? (Sequence Diagram) - Lu?ng Deduplicate và Import
`plantuml
@startuml
actor Admin
participant "PropertyImportController" as Controller
participant "PropertyImportService" as ImportService
participant "AccommodationDataProvider" as Provider
database "Database (Staging & Property)" as DB

Admin -> Controller: POST /api/admin/property-imports/search
activate Controller
Controller -> ImportService: searchAndStageProperties()
activate ImportService

ImportService -> Provider: search(request)
activate Provider
Provider --> ImportService: List<ProviderSearchResult>
deactivate Provider

ImportService -> DB: Create PropertyImportBatch
loop Cho m?i k?t qu?
    ImportService -> DB: Check Duplicate (M?c 1-5: ID, Tên, T?a d?, SDT)
    alt Exact Duplicate
        ImportService -> DB: Luu Staging (Status=EXACT_DUPLICATE)
    else Possible Duplicate
        ImportService -> DB: Luu Staging (Status=POSSIBLE_DUPLICATE)
    else New
        ImportService -> DB: Luu Staging (Status=NEW)
    end
end
ImportService --> Controller: BatchPreviewResult
deactivate ImportService
Controller --> Admin: JSON Preview
deactivate Controller

Admin -> Controller: POST /api/admin/property-imports/{batchId}/import
activate Controller
Controller -> ImportService: importValidItems(batchId)
activate ImportService
ImportService -> DB: Select valid NEW items
loop
    ImportService -> DB: Insert to hotels (approval_status=IMPORTED_PENDING_REVIEW)
end
ImportService --> Controller: ImportResult
deactivate ImportService
Controller --> Admin: Success Response
deactivate Controller
@enduml
`

### 6.3. Bi?u d? Ho?t d?ng (Activity Diagram) - Nh?n quy?n co s?
`plantuml
@startuml
start
:User xem trang chi ti?t khách s?n;
if (Khách s?n ? tr?ng thái IMPORTED_PENDING_REVIEW?) then (Có)
  :Hi?n th? nút "Xác nh?n quy?n qu?n lý";
  :User click và di?n form xác minh (gi?y phép kinh doanh...);
  :Luu PropertyClaimRequest (PENDING);
  :Admin nh?n thông báo;
  :Admin ki?m tra gi?y t?;
  if (H?p l??) then (Ð?ng ý)
    :Chuy?n tr?ng thái Khách s?n -> ACTIVE;
    :T?o record user_properties v?i Role=OWNER;
    :G?i email thông báo c?p quy?n thành công;
  else (T? ch?i)
    :C?p nh?t PropertyClaimRequest (REJECTED);
    :G?i email yêu c?u b? sung thông tin;
  end if
else (Không)
  :Ch? xem thông tin bình thu?ng;
end if
stop
@enduml
`
'@
Add-Content -Path "docs\UML.md" -Value $content

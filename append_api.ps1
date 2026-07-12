$content = @'

## 7. PROPERTY IMPORT & CLAIM API

### 7.1. Property Import Management

#### 7.1.1. Search and Stage Properties (Admin)
- **Endpoint**: /api/admin/property-imports/search
- **Method**: POST
- **Role**: SUPER_ADMIN
- **Permission**: PROPERTY_IMPORT:CREATE
- **Request Body**:
`json
{
  "provider": "NOMINATIM",
  "provinceId": 1,
  "wardId": null,
  "propertyTypes": ["HOTEL", "HOMESTAY", "RESORT"],
  "radiusKm": 20,
  "maxResults": 100
}
`
- **Response** (200 OK):
`json
{
  "batchId": 10,
  "status": "PREVIEW_READY",
  "totalFound": 90,
  "totalNew": 70,
  "totalDuplicate": 20
}
`

#### 7.1.2. Get Batch Items
- **Endpoint**: /api/admin/property-imports/{batchId}/items
- **Method**: GET
- **Role**: SUPER_ADMIN

#### 7.1.3. Import Valid Items
- **Endpoint**: /api/admin/property-imports/{batchId}/import
- **Method**: POST
- **Role**: SUPER_ADMIN
- **Response** (200 OK):
`json
{
  "message": "Imported 70 properties successfully."
}
`

### 7.2. Property Claim

#### 7.2.1. Request Claim (User)
- **Endpoint**: /api/properties/{propertyId}/claim
- **Method**: POST
- **Role**: USER
- **Request Body**:
`json
{
  "verificationMethod": "BUSINESS_LICENSE",
  "verificationData": "URL to document or text note"
}
`

#### 7.2.2. Get Claim Requests (Admin)
- **Endpoint**: /api/admin/property-claims
- **Method**: GET
- **Role**: SUPER_ADMIN

#### 7.2.3. Approve Claim
- **Endpoint**: /api/admin/property-claims/{id}/approve
- **Method**: POST
- **Role**: SUPER_ADMIN
- **Response** (200 OK): Grants the user OWNER role for the property.
'@
Add-Content -Path "docs\API_SPEC.md" -Value $content

SET XACT_ABORT ON;

ALTER TABLE notifications ALTER COLUMN title NVARCHAR(255) NOT NULL;
ALTER TABLE notifications ALTER COLUMN message NVARCHAR(MAX) NOT NULL;

ALTER TABLE property_claim_requests ALTER COLUMN verification_data NVARCHAR(MAX) NULL;
ALTER TABLE property_claim_requests ALTER COLUMN note NVARCHAR(MAX) NULL;
ALTER TABLE property_claim_requests ALTER COLUMN rejection_reason NVARCHAR(MAX) NULL;

ALTER TABLE property_import_items ALTER COLUMN raw_name NVARCHAR(255) NOT NULL;
ALTER TABLE property_import_items ALTER COLUMN normalized_name NVARCHAR(255) NULL;
ALTER TABLE property_import_items ALTER COLUMN raw_address NVARCHAR(1000) NULL;
ALTER TABLE property_import_items ALTER COLUMN raw_payload_json NVARCHAR(MAX) NULL;
ALTER TABLE property_import_items ALTER COLUMN error_message NVARCHAR(MAX) NULL;

ALTER TABLE subscription_plans ALTER COLUMN name_vi NVARCHAR(255) NOT NULL;
ALTER TABLE subscription_plans ALTER COLUMN name_en NVARCHAR(255) NULL;

UPDATE subscription_plans SET name_vi=N'Gói Miễn phí' WHERE code='FREE';
UPDATE subscription_plans SET name_vi=N'Gói Tiêu chuẩn' WHERE code='STANDARD';
UPDATE subscription_plans SET name_vi=N'Gói Cao cấp' WHERE code='PREMIUM';

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

IF COL_LENGTH('dbo.services', 'version') IS NULL
    ALTER TABLE dbo.services ADD version BIGINT NOT NULL CONSTRAINT DF_services_version DEFAULT 0;

IF EXISTS (
    SELECT 1 FROM dbo.services
    WHERE NULLIF(LTRIM(RTRIM(code)), '') IS NULL
       OR NULLIF(LTRIM(RTRIM(name_vi)), '') IS NULL
       OR NULLIF(LTRIM(RTRIM(name_en)), '') IS NULL
       OR price <= 0 OR price <> FLOOR(price)
       OR status NOT IN ('ACTIVE','INACTIVE')
)
    THROW 51088, 'Service catalog contains invalid rows; correct source data before applying lifecycle constraints.', 1;

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_services_positive_integer_vnd')
    ALTER TABLE dbo.services ADD CONSTRAINT CK_services_positive_integer_vnd CHECK (price > 0 AND price = FLOOR(price));

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_services_status_domain')
    ALTER TABLE dbo.services ADD CONSTRAINT CK_services_status_domain CHECK (status IN ('ACTIVE','INACTIVE'));

IF OBJECT_ID('dbo.service_catalog_history', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.service_catalog_history (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        service_id BIGINT NOT NULL,
        hotel_id BIGINT NULL,
        action VARCHAR(20) NOT NULL,
        reason NVARCHAR(1000) NOT NULL,
        code VARCHAR(80) NOT NULL,
        name_vi NVARCHAR(255) NOT NULL,
        name_en NVARCHAR(255) NOT NULL,
        price DECIMAL(19,0) NOT NULL,
        status VARCHAR(20) NOT NULL,
        service_version BIGINT NOT NULL,
        created_at DATETIME2 NULL,
        updated_at DATETIME2 NULL,
        created_by VARCHAR(255) NULL,
        updated_by VARCHAR(255) NULL,
        CONSTRAINT FK_service_catalog_history_service FOREIGN KEY (service_id) REFERENCES dbo.services(id),
        CONSTRAINT CK_service_catalog_history_action CHECK (action IN ('CREATE','UPDATE','DEACTIVATE','REACTIVATE')),
        CONSTRAINT CK_service_catalog_history_status CHECK (status IN ('ACTIVE','INACTIVE')),
        CONSTRAINT CK_service_catalog_history_price CHECK (price > 0 AND price = FLOOR(price))
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_service_catalog_history_service')
    CREATE INDEX IX_service_catalog_history_service ON dbo.service_catalog_history(service_id, id);

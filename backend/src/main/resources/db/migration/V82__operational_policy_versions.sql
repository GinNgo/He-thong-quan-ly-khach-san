IF OBJECT_ID('dbo.property_policy_versions', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.property_policy_versions (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_property_policy_versions PRIMARY KEY,
        hotel_id BIGINT NOT NULL,
        version_number BIGINT NOT NULL,
        status VARCHAR(20) NOT NULL CONSTRAINT DF_property_policy_status DEFAULT 'DRAFT',
        effective_from DATETIME2 NOT NULL,
        effective_until DATETIME2 NULL,
        check_in_vi NVARCHAR(2000) NOT NULL,
        check_in_en NVARCHAR(2000) NULL,
        check_out_vi NVARCHAR(2000) NOT NULL,
        check_out_en NVARCHAR(2000) NULL,
        cancellation_vi NVARCHAR(3000) NOT NULL,
        cancellation_en NVARCHAR(3000) NULL,
        child_policy_vi NVARCHAR(2000) NOT NULL,
        child_policy_en NVARCHAR(2000) NULL,
        pet_policy_vi NVARCHAR(2000) NOT NULL,
        pet_policy_en NVARCHAR(2000) NULL,
        smoking_policy_vi NVARCHAR(2000) NOT NULL,
        smoking_policy_en NVARCHAR(2000) NULL,
        house_rules_vi NVARCHAR(4000) NOT NULL,
        house_rules_en NVARCHAR(4000) NULL,
        row_version BIGINT NOT NULL CONSTRAINT DF_property_policy_row_version DEFAULT 0,
        created_at DATETIME2 NULL,
        updated_at DATETIME2 NULL,
        created_by NVARCHAR(255) NULL,
        updated_by NVARCHAR(255) NULL,
        CONSTRAINT FK_property_policy_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT CK_property_policy_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
        CONSTRAINT CK_property_policy_effective_range CHECK (effective_until IS NULL OR effective_until > effective_from),
        CONSTRAINT UK_property_policy_version UNIQUE (hotel_id, version_number)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_property_policy_effective' AND object_id = OBJECT_ID('dbo.property_policy_versions'))
    CREATE INDEX IX_property_policy_effective
        ON dbo.property_policy_versions(hotel_id, status, effective_from DESC)
        INCLUDE (effective_until, version_number);

IF COL_LENGTH('dbo.reservations', 'operational_policy_id') IS NULL
    ALTER TABLE dbo.reservations ADD operational_policy_id BIGINT NULL;

IF COL_LENGTH('dbo.reservations', 'operational_policy_version') IS NULL
    ALTER TABLE dbo.reservations ADD operational_policy_version BIGINT NULL;

IF COL_LENGTH('dbo.reservations', 'operational_policy_effective_from') IS NULL
    ALTER TABLE dbo.reservations ADD operational_policy_effective_from DATETIME2 NULL;

IF COL_LENGTH('dbo.reservations', 'operational_policy_snapshot') IS NULL
    ALTER TABLE dbo.reservations ADD operational_policy_snapshot NVARCHAR(MAX) NULL;

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_reservation_operational_policy')
    ALTER TABLE dbo.reservations ADD CONSTRAINT FK_reservation_operational_policy
        FOREIGN KEY (operational_policy_id) REFERENCES dbo.property_policy_versions(id);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_reservation_operational_policy' AND object_id = OBJECT_ID('dbo.reservations'))
    CREATE INDEX IX_reservation_operational_policy
        ON dbo.reservations(hotel_id, operational_policy_version)
        INCLUDE (operational_policy_id, operational_policy_effective_from);

IF OBJECT_ID('dbo.customer_favorites', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.customer_favorites (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_customer_favorites PRIMARY KEY,
        customer_id BIGINT NOT NULL,
        hotel_id BIGINT NOT NULL,
        created_at DATETIME2(7) NOT NULL CONSTRAINT DF_customer_favorites_created_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT UQ_customer_favorites_customer_hotel UNIQUE (customer_id, hotel_id),
        CONSTRAINT FK_customer_favorites_customer FOREIGN KEY (customer_id) REFERENCES dbo.users(id),
        CONSTRAINT FK_customer_favorites_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_customer_favorites_customer_created')
    CREATE INDEX IX_customer_favorites_customer_created
        ON dbo.customer_favorites(customer_id, created_at DESC);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_customer_favorites_hotel')
    CREATE INDEX IX_customer_favorites_hotel
        ON dbo.customer_favorites(hotel_id);

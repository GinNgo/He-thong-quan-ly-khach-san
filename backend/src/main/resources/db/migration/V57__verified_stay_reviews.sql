CREATE TABLE dbo.stay_reviews (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    hotel_id BIGINT NOT NULL,
    reservation_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    rating INT NOT NULL,
    title NVARCHAR(150) NULL,
    comment NVARCHAR(2000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    moderation_reason NVARCHAR(500) NULL,
    moderated_by BIGINT NULL,
    moderated_at DATETIME2 NULL,
    property_response NVARCHAR(1000) NULL,
    responded_by BIGINT NULL,
    responded_at DATETIME2 NULL,
    created_at DATETIME2 NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_stay_reviews_reservation UNIQUE(reservation_id),
    CONSTRAINT fk_stay_reviews_hotel FOREIGN KEY(hotel_id) REFERENCES dbo.hotels(id),
    CONSTRAINT fk_stay_reviews_reservation FOREIGN KEY(reservation_id) REFERENCES dbo.reservations(id),
    CONSTRAINT fk_stay_reviews_customer FOREIGN KEY(customer_id) REFERENCES dbo.users(id),
    CONSTRAINT fk_stay_reviews_moderator FOREIGN KEY(moderated_by) REFERENCES dbo.users(id),
    CONSTRAINT fk_stay_reviews_responder FOREIGN KEY(responded_by) REFERENCES dbo.users(id),
    CONSTRAINT ck_stay_reviews_rating CHECK(rating BETWEEN 1 AND 10),
    CONSTRAINT ck_stay_reviews_status CHECK(status IN ('PUBLISHED','HIDDEN'))
);
CREATE INDEX ix_stay_reviews_hotel_status_created ON dbo.stay_reviews(hotel_id,status,created_at DESC);
CREATE INDEX ix_stay_reviews_customer_created ON dbo.stay_reviews(customer_id,created_at DESC);

DECLARE @moduleId BIGINT = (SELECT TOP 1 id FROM dbo.app_module WHERE code = 'HOTEL' ORDER BY id);
IF @moduleId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.app_function WHERE code = 'REVIEW')
    INSERT INTO dbo.app_function(code,name,url,icon,sort_order,module_id)
    VALUES('REVIEW',N'Đánh giá sau lưu trú',NULL,'pi pi-star',49,@moduleId);

;WITH source AS (
    SELECT role.id role_id, function_row.id function_id,
           CASE WHEN role.code IN ('SUPER_ADMIN','ADMIN','PROPERTY_OWNER','HOTEL_ADMIN') THEN 63 ELSE 5 END action_mask
    FROM dbo.app_role role CROSS JOIN dbo.app_function function_row
    WHERE role.code IN ('SUPER_ADMIN','ADMIN','PROPERTY_OWNER','HOTEL_ADMIN','HOTEL_MANAGER')
      AND function_row.code = 'REVIEW'
)
MERGE dbo.app_role_permission target USING source
ON target.role_id=source.role_id AND target.function_id=source.function_id
WHEN MATCHED THEN UPDATE SET action_mask=target.action_mask | source.action_mask
WHEN NOT MATCHED THEN INSERT(role_id,function_id,action_mask) VALUES(source.role_id,source.function_id,source.action_mask);

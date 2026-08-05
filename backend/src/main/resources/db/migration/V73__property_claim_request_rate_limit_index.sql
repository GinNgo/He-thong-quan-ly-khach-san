IF OBJECT_ID('dbo.property_claim_requests', 'U') IS NULL
    THROW 51000, 'Required property_claim_requests table is missing.', 1;

IF COL_LENGTH('dbo.property_claim_requests', 'requester_user_id') IS NULL
   OR COL_LENGTH('dbo.property_claim_requests', 'created_at') IS NULL
   OR COL_LENGTH('dbo.property_claim_requests', 'id') IS NULL
    THROW 51000, 'Required property claim rate-limit columns are missing.', 1;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.property_claim_requests')
      AND name = 'IX_property_claim_requests_requester_created'
)
    CREATE INDEX IX_property_claim_requests_requester_created
        ON dbo.property_claim_requests(requester_user_id, created_at, id);

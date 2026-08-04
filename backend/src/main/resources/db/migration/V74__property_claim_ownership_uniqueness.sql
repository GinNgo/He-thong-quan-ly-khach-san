SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;

IF OBJECT_ID('dbo.property_claim_requests', 'U') IS NULL
   OR OBJECT_ID('dbo.user_properties', 'U') IS NULL
    THROW 51074, 'Required property claim ownership tables are missing.', 1;

IF COL_LENGTH('dbo.property_claim_requests', 'property_id') IS NULL
   OR COL_LENGTH('dbo.property_claim_requests', 'requester_user_id') IS NULL
   OR COL_LENGTH('dbo.property_claim_requests', 'status') IS NULL
   OR COL_LENGTH('dbo.user_properties', 'user_id') IS NULL
   OR COL_LENGTH('dbo.user_properties', 'hotel_id') IS NULL
   OR COL_LENGTH('dbo.user_properties', 'relationship_type') IS NULL
   OR COL_LENGTH('dbo.user_properties', 'status') IS NULL
   OR COL_LENGTH('dbo.user_properties', 'is_primary_owner') IS NULL
    THROW 51074, 'Required property claim ownership columns are missing.', 1;

IF EXISTS (
    SELECT property_id, requester_user_id
    FROM dbo.property_claim_requests
    WHERE [status] = 'PENDING'
    GROUP BY property_id, requester_user_id
    HAVING COUNT_BIG(*) > 1
)
    THROW 51074, 'Duplicate pending property claims must be resolved before applying V74.', 1;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.property_claim_requests') AND name = 'UX_property_claim_pending_requester')
    CREATE UNIQUE INDEX UX_property_claim_pending_requester
        ON dbo.property_claim_requests(property_id, requester_user_id)
        WHERE [status] = 'PENDING';

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes i
    JOIN sys.index_columns c1 ON c1.object_id = i.object_id AND c1.index_id = i.index_id AND c1.key_ordinal = 1
    JOIN sys.columns k1 ON k1.object_id = c1.object_id AND k1.column_id = c1.column_id AND k1.name = 'property_id'
    JOIN sys.index_columns c2 ON c2.object_id = i.object_id AND c2.index_id = i.index_id AND c2.key_ordinal = 2
    JOIN sys.columns k2 ON k2.object_id = c2.object_id AND k2.column_id = c2.column_id AND k2.name = 'requester_user_id'
    WHERE i.object_id = OBJECT_ID('dbo.property_claim_requests')
      AND i.name = 'UX_property_claim_pending_requester' AND i.is_unique = 1
      AND REPLACE(REPLACE(REPLACE(REPLACE(UPPER(i.filter_definition), '[', ''), ']', ''), ' ', ''), '(', '')
          LIKE '%STATUS=''PENDING''%'
      AND (SELECT COUNT(*) FROM sys.index_columns ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal > 0) = 2
      AND (SELECT COUNT(*) FROM sys.index_columns ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id) = 2
)
    THROW 51074, 'UX_property_claim_pending_requester has an invalid definition.', 1;

IF EXISTS (
    SELECT user_id, hotel_id
    FROM dbo.user_properties
    WHERE relationship_type = 'OWNER'
    GROUP BY user_id, hotel_id
    HAVING COUNT_BIG(*) > 1
)
    THROW 51074, 'Duplicate owner mappings must be resolved before applying V74.', 1;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.user_properties') AND name = 'UX_user_property_owner')
    CREATE UNIQUE INDEX UX_user_property_owner
        ON dbo.user_properties(user_id, hotel_id)
        WHERE relationship_type = 'OWNER';

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes i
    JOIN sys.index_columns c1 ON c1.object_id = i.object_id AND c1.index_id = i.index_id AND c1.key_ordinal = 1
    JOIN sys.columns k1 ON k1.object_id = c1.object_id AND k1.column_id = c1.column_id AND k1.name = 'user_id'
    JOIN sys.index_columns c2 ON c2.object_id = i.object_id AND c2.index_id = i.index_id AND c2.key_ordinal = 2
    JOIN sys.columns k2 ON k2.object_id = c2.object_id AND k2.column_id = c2.column_id AND k2.name = 'hotel_id'
    WHERE i.object_id = OBJECT_ID('dbo.user_properties')
      AND i.name = 'UX_user_property_owner' AND i.is_unique = 1
      AND REPLACE(REPLACE(REPLACE(REPLACE(UPPER(i.filter_definition), '[', ''), ']', ''), ' ', ''), '(', '')
          LIKE '%RELATIONSHIP_TYPE=''OWNER''%'
      AND (SELECT COUNT(*) FROM sys.index_columns ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal > 0) = 2
      AND (SELECT COUNT(*) FROM sys.index_columns ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id) = 2
)
    THROW 51074, 'UX_user_property_owner has an invalid definition.', 1;

IF EXISTS (
    SELECT hotel_id
    FROM dbo.user_properties
    WHERE relationship_type = 'OWNER' AND [status] = 'ACTIVE' AND is_primary_owner = 1
    GROUP BY hotel_id
    HAVING COUNT_BIG(*) > 1
)
    THROW 51074, 'Duplicate primary active owners must be resolved before applying V74.', 1;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.user_properties') AND name = 'UX_user_property_primary_active_owner')
    CREATE UNIQUE INDEX UX_user_property_primary_active_owner
        ON dbo.user_properties(hotel_id)
        WHERE relationship_type = 'OWNER' AND [status] = 'ACTIVE' AND is_primary_owner = 1;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes i
    JOIN sys.index_columns c1 ON c1.object_id = i.object_id AND c1.index_id = i.index_id AND c1.key_ordinal = 1
    JOIN sys.columns k1 ON k1.object_id = c1.object_id AND k1.column_id = c1.column_id AND k1.name = 'hotel_id'
    WHERE i.object_id = OBJECT_ID('dbo.user_properties')
      AND i.name = 'UX_user_property_primary_active_owner' AND i.is_unique = 1
      AND REPLACE(REPLACE(REPLACE(REPLACE(UPPER(i.filter_definition), '[', ''), ']', ''), ' ', ''), '(', '')
          LIKE '%RELATIONSHIP_TYPE=''OWNER''%STATUS=''ACTIVE''%IS_PRIMARY_OWNER=1%'
      AND (SELECT COUNT(*) FROM sys.index_columns ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.key_ordinal > 0) = 1
      AND (SELECT COUNT(*) FROM sys.index_columns ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id) = 1
)
    THROW 51074, 'UX_user_property_primary_active_owner has an invalid definition.', 1;

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;

IF OBJECT_ID('dbo.user_properties', 'U') IS NULL OR OBJECT_ID('dbo.users', 'U') IS NULL OR OBJECT_ID('dbo.hotels', 'U') IS NULL
    THROW 51087, 'Required ownership lifecycle tables are missing.', 1;

IF COL_LENGTH('dbo.user_properties', 'accepted_at') IS NULL ALTER TABLE dbo.user_properties ADD accepted_at DATETIME2 NULL;
IF COL_LENGTH('dbo.user_properties', 'left_at') IS NULL ALTER TABLE dbo.user_properties ADD left_at DATETIME2 NULL;
IF COL_LENGTH('dbo.user_properties', 'removed_at') IS NULL ALTER TABLE dbo.user_properties ADD removed_at DATETIME2 NULL;
IF COL_LENGTH('dbo.user_properties', 'removed_by_user_id') IS NULL ALTER TABLE dbo.user_properties ADD removed_by_user_id BIGINT NULL;
IF COL_LENGTH('dbo.user_properties', 'owner_exit_reason') IS NULL ALTER TABLE dbo.user_properties ADD owner_exit_reason NVARCHAR(500) NULL;
IF COL_LENGTH('dbo.user_properties', 'billing_admin') IS NULL ALTER TABLE dbo.user_properties ADD billing_admin BIT NOT NULL CONSTRAINT DF_user_properties_billing_admin DEFAULT 0;

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_user_properties_removed_by')
    ALTER TABLE dbo.user_properties ADD CONSTRAINT FK_user_properties_removed_by FOREIGN KEY(removed_by_user_id) REFERENCES dbo.users(id);

IF OBJECT_ID('dbo.owner_invitations', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.owner_invitations (
        id BIGINT IDENTITY PRIMARY KEY, hotel_id BIGINT NOT NULL, invited_by_user_id BIGINT NOT NULL,
        invited_email NVARCHAR(320) NOT NULL, token_hash CHAR(64) NOT NULL, [status] VARCHAR(20) NOT NULL,
        expires_at DATETIME2 NOT NULL, accepted_at DATETIME2 NULL, accepted_by_user_id BIGINT NULL,
        owner_terms_accepted_at DATETIME2 NULL, cancelled_at DATETIME2 NULL,
        created_at DATETIME2 NULL, created_by VARCHAR(255) NULL, updated_at DATETIME2 NULL, updated_by VARCHAR(255) NULL,
        CONSTRAINT FK_owner_invitation_hotel FOREIGN KEY(hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_owner_invitation_inviter FOREIGN KEY(invited_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT FK_owner_invitation_acceptor FOREIGN KEY(accepted_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT CK_owner_invitation_status CHECK ([status] IN ('PENDING','ACCEPTED','DECLINED','CANCELLED','EXPIRED'))
    );
END;

IF OBJECT_ID('dbo.ownership_transfers', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.ownership_transfers (
        id BIGINT IDENTITY PRIMARY KEY, hotel_id BIGINT NOT NULL, from_user_id BIGINT NOT NULL, to_user_id BIGINT NOT NULL,
        [status] VARCHAR(20) NOT NULL, expires_at DATETIME2 NOT NULL, accepted_at DATETIME2 NULL,
        responsibility_accepted_at DATETIME2 NULL,
        created_at DATETIME2 NULL, created_by VARCHAR(255) NULL, updated_at DATETIME2 NULL, updated_by VARCHAR(255) NULL,
        CONSTRAINT FK_ownership_transfer_hotel FOREIGN KEY(hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_ownership_transfer_from FOREIGN KEY(from_user_id) REFERENCES dbo.users(id),
        CONSTRAINT FK_ownership_transfer_to FOREIGN KEY(to_user_id) REFERENCES dbo.users(id),
        CONSTRAINT CK_ownership_transfer_status CHECK ([status] IN ('PENDING','ACCEPTED','CANCELLED','EXPIRED'))
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.owner_invitations') AND name='UX_owner_invitation_pending_email')
    CREATE UNIQUE INDEX UX_owner_invitation_pending_email ON dbo.owner_invitations(hotel_id, invited_email) WHERE [status]='PENDING';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.owner_invitations') AND name='UX_owner_invitation_token_hash')
    CREATE UNIQUE INDEX UX_owner_invitation_token_hash ON dbo.owner_invitations(token_hash);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.ownership_transfers') AND name='UX_ownership_transfer_pending_hotel')
    CREATE UNIQUE INDEX UX_ownership_transfer_pending_hotel ON dbo.ownership_transfers(hotel_id) WHERE [status]='PENDING';

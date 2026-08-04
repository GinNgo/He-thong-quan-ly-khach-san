SET NOCOUNT ON;

IF (SELECT COUNT(*) FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.support_conversations')
      AND name IN ('first_response_at','last_customer_message_at','last_support_reply_at',
                   'closed_reason','reopened_at','reopen_reason')) <> 6
    THROW 51000, 'T328 lifecycle columns are incomplete.', 1;

IF OBJECT_ID('dbo.support_conversation_attachments', 'U') IS NULL
    THROW 51000, 'T328 attachment table is missing.', 1;

IF (SELECT COUNT(*) FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.support_conversation_attachments')
      AND name IN ('IX_support_attachments_conversation_time','IX_support_attachments_checksum')) <> 2
    THROW 51000, 'T328 attachment indexes are incomplete.', 1;

IF (SELECT COUNT(*) FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.support_conversation_attachments')) <> 2
    THROW 51000, 'T328 attachment checks are incomplete.', 1;

IF (SELECT COUNT(*) FROM sys.foreign_keys
    WHERE parent_object_id = OBJECT_ID('dbo.support_conversation_attachments')) <> 3
    THROW 51000, 'T328 attachment foreign keys are incomplete.', 1;

SELECT 6 AS lifecycle_columns, 1 AS attachment_table, 2 AS attachment_indexes,
       2 AS attachment_checks, 3 AS attachment_foreign_keys;

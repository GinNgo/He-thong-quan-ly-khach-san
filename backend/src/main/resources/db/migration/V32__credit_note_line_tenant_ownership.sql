SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;

IF OBJECT_ID('dbo.property_credit_note_lines', 'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.property_credit_note_lines', 'hotel_id') IS NULL
        ALTER TABLE dbo.property_credit_note_lines ADD hotel_id BIGINT NULL;

    UPDATE line
    SET hotel_id = note.hotel_id
    FROM dbo.property_credit_note_lines line
    INNER JOIN dbo.property_credit_notes note ON note.id = line.credit_note_id
    WHERE line.hotel_id IS NULL;

    IF EXISTS (SELECT 1 FROM dbo.property_credit_note_lines WHERE hotel_id IS NULL)
        THROW 51032, 'Credit-note line ownership could not be backfilled before V32.', 1;

    IF EXISTS (
        SELECT 1
        FROM dbo.property_credit_note_lines line
        INNER JOIN dbo.property_credit_notes note ON note.id = line.credit_note_id
        WHERE line.hotel_id <> note.hotel_id
    )
        THROW 51033, 'Credit-note line ownership conflicts with its credit note before V32.', 1;

    IF EXISTS (
        SELECT 1
        FROM dbo.property_credit_note_lines line
        INNER JOIN dbo.property_credit_notes note ON note.id = line.credit_note_id
        INNER JOIN dbo.property_invoice_lines invoice_line ON invoice_line.id = line.invoice_line_id
        WHERE line.invoice_line_id IS NOT NULL
          AND (invoice_line.hotel_id <> note.hotel_id OR invoice_line.invoice_id <> note.invoice_id)
    )
        THROW 51034, 'Credit-note line invoice ownership conflicts before V32.', 1;

    IF EXISTS (
        SELECT 1
        FROM sys.columns
        WHERE object_id = OBJECT_ID('dbo.property_credit_note_lines')
          AND name = 'hotel_id'
          AND is_nullable = 1
    )
        ALTER TABLE dbo.property_credit_note_lines ALTER COLUMN hotel_id BIGINT NOT NULL;

    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_property_credit_note_line_hotel')
        ALTER TABLE dbo.property_credit_note_lines ADD CONSTRAINT FK_property_credit_note_line_hotel
            FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id);

    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE object_id = OBJECT_ID('dbo.property_credit_note_lines')
          AND name = 'IX_property_credit_note_lines_hotel_note'
    )
        CREATE INDEX IX_property_credit_note_lines_hotel_note
            ON dbo.property_credit_note_lines(hotel_id, credit_note_id, invoice_line_id);
END;

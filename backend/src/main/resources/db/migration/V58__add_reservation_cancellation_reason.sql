ALTER TABLE reservations ADD cancellation_reason_code VARCHAR(50) NULL;
ALTER TABLE reservations ADD cancellation_reason NVARCHAR(500) NULL;
ALTER TABLE reservations ADD cancelled_at DATETIME2 NULL;

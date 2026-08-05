SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

IF OBJECT_ID('dbo.reservation_amendments', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.reservation_amendments (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_reservation_amendments PRIMARY KEY,
        public_id VARCHAR(64) NOT NULL,
        hotel_id BIGINT NOT NULL,
        reservation_id BIGINT NOT NULL,
        actor_user_id BIGINT NULL,
        actor_type VARCHAR(20) NOT NULL,
        original_room_type_id BIGINT NOT NULL,
        proposed_room_type_id BIGINT NOT NULL,
        payment_attempt_id BIGINT NULL,
        status VARCHAR(30) NOT NULL,
        policy_version VARCHAR(80) NOT NULL,
        original_check_in DATE NOT NULL,
        original_check_out DATE NOT NULL,
        proposed_check_in DATE NOT NULL,
        proposed_check_out DATE NOT NULL,
        original_quantity INT NOT NULL,
        proposed_quantity INT NOT NULL,
        original_adults INT NOT NULL,
        proposed_adults INT NOT NULL,
        original_children INT NOT NULL,
        proposed_children INT NOT NULL,
        original_total DECIMAL(19,0) NOT NULL,
        proposed_total DECIMAL(19,0) NOT NULL,
        price_delta DECIMAL(19,0) NOT NULL,
        original_deposit DECIMAL(19,0) NOT NULL,
        proposed_deposit DECIMAL(19,0) NOT NULL,
        preserved_discount DECIMAL(19,0) NOT NULL CONSTRAINT DF_reservation_amendment_discount DEFAULT 0,
        hold_quantity INT NOT NULL CONSTRAINT DF_reservation_amendment_hold DEFAULT 0,
        idempotency_key VARCHAR(160) NOT NULL,
        request_hash VARCHAR(64) NOT NULL,
        apply_idempotency_key VARCHAR(160) NULL,
        refund_request_id BIGINT NULL,
        expires_at DATETIME2 NOT NULL,
        applied_at DATETIME2 NULL,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_reservation_amendment_created DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2 NOT NULL CONSTRAINT DF_reservation_amendment_updated DEFAULT SYSUTCDATETIME(),
        version BIGINT NOT NULL CONSTRAINT DF_reservation_amendment_version DEFAULT 0,
        CONSTRAINT FK_reservation_amendment_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_reservation_amendment_reservation FOREIGN KEY (reservation_id) REFERENCES dbo.reservations(id),
        CONSTRAINT FK_reservation_amendment_actor FOREIGN KEY (actor_user_id) REFERENCES dbo.users(id),
        CONSTRAINT FK_reservation_amendment_original_type FOREIGN KEY (original_room_type_id) REFERENCES dbo.room_types(id),
        CONSTRAINT FK_reservation_amendment_proposed_type FOREIGN KEY (proposed_room_type_id) REFERENCES dbo.room_types(id),
        CONSTRAINT FK_reservation_amendment_attempt FOREIGN KEY (payment_attempt_id) REFERENCES dbo.property_payment_attempts(id),
        CONSTRAINT FK_reservation_amendment_refund FOREIGN KEY (refund_request_id) REFERENCES dbo.property_refund_requests(id),
        CONSTRAINT UQ_reservation_amendment_public UNIQUE (public_id),
        CONSTRAINT UQ_reservation_amendment_idempotency UNIQUE (hotel_id, idempotency_key),
        CONSTRAINT CK_reservation_amendment_status CHECK (status IN ('QUOTED','AWAITING_PAYMENT','PAYMENT_PENDING','APPLIED','EXPIRED','CANCELLED')),
        CONSTRAINT CK_reservation_amendment_dates CHECK (
            original_check_out > original_check_in AND proposed_check_out > proposed_check_in),
        CONSTRAINT CK_reservation_amendment_quantities CHECK (
            original_quantity > 0 AND proposed_quantity > 0 AND hold_quantity >= 0 AND hold_quantity <= proposed_quantity),
        CONSTRAINT CK_reservation_amendment_guests CHECK (original_adults > 0 AND proposed_adults > 0 AND original_children >= 0 AND proposed_children >= 0),
        CONSTRAINT CK_reservation_amendment_money CHECK (
            original_total >= 0 AND proposed_total >= 0 AND
            original_deposit >= 0 AND proposed_deposit >= 0 AND preserved_discount >= 0 AND
            original_deposit <= original_total AND proposed_deposit <= proposed_total AND
            price_delta = proposed_total - original_total)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.reservation_amendments') AND name = 'IX_reservation_amendment_reservation_status')
    CREATE INDEX IX_reservation_amendment_reservation_status
        ON dbo.reservation_amendments(hotel_id, reservation_id, status, expires_at);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.reservation_amendments') AND name = 'IX_reservation_amendment_hold')
    CREATE INDEX IX_reservation_amendment_hold
        ON dbo.reservation_amendments(hotel_id, proposed_room_type_id, status, proposed_check_in, proposed_check_out);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.reservation_amendments') AND name = 'UX_reservation_amendment_attempt')
    CREATE UNIQUE INDEX UX_reservation_amendment_attempt
        ON dbo.reservation_amendments(payment_attempt_id)
        WHERE payment_attempt_id IS NOT NULL;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.reservation_amendments') AND name = 'UX_reservation_amendment_refund')
    CREATE UNIQUE INDEX UX_reservation_amendment_refund
        ON dbo.reservation_amendments(refund_request_id)
        WHERE refund_request_id IS NOT NULL;

IF COL_LENGTH('dbo.property_payment_attempts', 'reservation_amendment_id') IS NULL
BEGIN
    ALTER TABLE dbo.property_payment_attempts ADD reservation_amendment_id BIGINT NULL;
    ALTER TABLE dbo.property_payment_attempts ADD CONSTRAINT FK_property_attempt_amendment
        FOREIGN KEY (reservation_amendment_id) REFERENCES dbo.reservation_amendments(id);
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.property_payment_attempts') AND name = 'IX_property_attempt_amendment')
    CREATE INDEX IX_property_attempt_amendment
        ON dbo.property_payment_attempts(hotel_id, reservation_amendment_id, status);

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;

IF OBJECT_ID('dbo.reservation_charge_lines', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.reservation_charge_lines (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_reservation_charge_lines PRIMARY KEY,
        hotel_id BIGINT NOT NULL,
        reservation_id BIGINT NOT NULL,
        charge_type VARCHAR(30) NOT NULL,
        source_id BIGINT NULL,
        source_version VARCHAR(80) NULL,
        code VARCHAR(80) NULL,
        name NVARCHAR(255) NOT NULL,
        description NVARCHAR(1000) NULL,
        unit_price DECIMAL(19,0) NOT NULL,
        quantity DECIMAL(19,3) NOT NULL,
        tax_amount DECIMAL(19,0) NOT NULL CONSTRAINT DF_charge_line_tax DEFAULT 0,
        discount_amount DECIMAL(19,0) NOT NULL CONSTRAINT DF_charge_line_discount DEFAULT 0,
        total_amount DECIMAL(19,0) NOT NULL,
        service_used_at DATETIME2 NULL,
        actor_id BIGINT NULL,
        reverses_line_id BIGINT NULL,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_charge_line_created DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_charge_line_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_charge_line_reservation FOREIGN KEY (reservation_id) REFERENCES dbo.reservations(id),
        CONSTRAINT FK_charge_line_actor FOREIGN KEY (actor_id) REFERENCES dbo.users(id),
        CONSTRAINT FK_charge_line_reversal FOREIGN KEY (reverses_line_id) REFERENCES dbo.reservation_charge_lines(id),
        CONSTRAINT CK_charge_line_type CHECK (charge_type IN ('ROOM','SERVICE','MINIBAR','SURCHARGE','TAX','FEE','DISCOUNT','ADJUSTMENT')),
        CONSTRAINT CK_charge_line_quantity CHECK (quantity > 0),
        CONSTRAINT CK_charge_line_unit_price CHECK (unit_price >= 0),
        CONSTRAINT CK_charge_line_total CHECK (total_amount >= 0)
    );
END;

IF OBJECT_ID('dbo.property_invoices', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.property_invoices (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_property_invoices PRIMARY KEY,
        hotel_id BIGINT NOT NULL,
        reservation_id BIGINT NOT NULL,
        invoice_number VARCHAR(80) NOT NULL,
        customer_snapshot_json NVARCHAR(MAX) NOT NULL,
        property_snapshot_json NVARCHAR(MAX) NOT NULL,
        subtotal DECIMAL(19,0) NOT NULL,
        tax_amount DECIMAL(19,0) NOT NULL,
        fee_amount DECIMAL(19,0) NOT NULL,
        discount_amount DECIMAL(19,0) NOT NULL,
        total_amount DECIMAL(19,0) NOT NULL,
        paid_amount DECIMAL(19,0) NOT NULL,
        refunded_amount DECIMAL(19,0) NOT NULL,
        balance_amount DECIMAL(19,0) NOT NULL,
        currency VARCHAR(3) NOT NULL CONSTRAINT DF_property_invoice_currency DEFAULT 'VND',
        status VARCHAR(20) NOT NULL,
        finalized_at DATETIME2 NULL,
        finalized_by BIGINT NULL,
        version BIGINT NOT NULL CONSTRAINT DF_property_invoice_version DEFAULT 0,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_property_invoice_created DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_property_invoice_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_property_invoice_reservation FOREIGN KEY (reservation_id) REFERENCES dbo.reservations(id),
        CONSTRAINT FK_property_invoice_finalizer FOREIGN KEY (finalized_by) REFERENCES dbo.users(id),
        CONSTRAINT UQ_property_invoice_number UNIQUE (invoice_number),
        CONSTRAINT CK_property_invoice_currency CHECK (currency = 'VND'),
        CONSTRAINT CK_property_invoice_status CHECK (status IN ('DRAFT','FINALIZED','CREDITED')),
        CONSTRAINT CK_property_invoice_finalized CHECK (status = 'DRAFT' OR finalized_at IS NOT NULL)
    );
END;

IF OBJECT_ID('dbo.property_invoice_lines', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.property_invoice_lines (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_property_invoice_lines PRIMARY KEY,
        invoice_id BIGINT NOT NULL,
        hotel_id BIGINT NOT NULL,
        line_type VARCHAR(30) NOT NULL,
        source_charge_line_id BIGINT NULL,
        code VARCHAR(80) NULL,
        name NVARCHAR(255) NOT NULL,
        description NVARCHAR(1000) NULL,
        quantity DECIMAL(19,3) NOT NULL,
        unit_price DECIMAL(19,0) NOT NULL,
        tax_amount DECIMAL(19,0) NOT NULL,
        discount_amount DECIMAL(19,0) NOT NULL,
        total_amount DECIMAL(19,0) NOT NULL,
        usage_started_at DATETIME2 NULL,
        usage_ended_at DATETIME2 NULL,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_property_invoice_line_created DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_property_invoice_line_invoice FOREIGN KEY (invoice_id) REFERENCES dbo.property_invoices(id),
        CONSTRAINT FK_property_invoice_line_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_property_invoice_line_charge FOREIGN KEY (source_charge_line_id) REFERENCES dbo.reservation_charge_lines(id),
        CONSTRAINT CK_property_invoice_line_quantity CHECK (quantity > 0),
        CONSTRAINT CK_property_invoice_line_amount CHECK (unit_price >= 0 AND total_amount >= 0)
    );
END;

IF OBJECT_ID('dbo.property_invoice_payment_allocations', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.property_invoice_payment_allocations (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_property_invoice_allocations PRIMARY KEY,
        invoice_id BIGINT NOT NULL,
        hotel_id BIGINT NOT NULL,
        transaction_id BIGINT NOT NULL,
        allocated_amount DECIMAL(19,0) NOT NULL,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_property_invoice_allocation_created DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_property_invoice_allocation_invoice FOREIGN KEY (invoice_id) REFERENCES dbo.property_invoices(id),
        CONSTRAINT FK_property_invoice_allocation_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_property_invoice_allocation_transaction FOREIGN KEY (transaction_id) REFERENCES dbo.property_financial_transactions(id),
        CONSTRAINT UQ_property_invoice_allocation UNIQUE (invoice_id, transaction_id),
        CONSTRAINT CK_property_invoice_allocation_amount CHECK (allocated_amount > 0)
    );
END;

IF OBJECT_ID('dbo.property_credit_notes', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.property_credit_notes (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_property_credit_notes PRIMARY KEY,
        hotel_id BIGINT NOT NULL,
        invoice_id BIGINT NOT NULL,
        credit_note_number VARCHAR(80) NOT NULL,
        reason NVARCHAR(1000) NOT NULL,
        amount DECIMAL(19,0) NOT NULL,
        actor_id BIGINT NOT NULL,
        approved_by BIGINT NULL,
        issued_at DATETIME2 NOT NULL CONSTRAINT DF_property_credit_note_issued DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_property_credit_note_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_property_credit_note_invoice FOREIGN KEY (invoice_id) REFERENCES dbo.property_invoices(id),
        CONSTRAINT FK_property_credit_note_actor FOREIGN KEY (actor_id) REFERENCES dbo.users(id),
        CONSTRAINT FK_property_credit_note_approver FOREIGN KEY (approved_by) REFERENCES dbo.users(id),
        CONSTRAINT UQ_property_credit_note_number UNIQUE (credit_note_number),
        CONSTRAINT CK_property_credit_note_amount CHECK (amount > 0)
    );
END;

IF OBJECT_ID('dbo.property_credit_note_lines', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.property_credit_note_lines (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_property_credit_note_lines PRIMARY KEY,
        credit_note_id BIGINT NOT NULL,
        invoice_line_id BIGINT NULL,
        description NVARCHAR(1000) NOT NULL,
        amount DECIMAL(19,0) NOT NULL,
        CONSTRAINT FK_property_credit_note_line_note FOREIGN KEY (credit_note_id) REFERENCES dbo.property_credit_notes(id),
        CONSTRAINT FK_property_credit_note_line_invoice_line FOREIGN KEY (invoice_line_id) REFERENCES dbo.property_invoice_lines(id),
        CONSTRAINT CK_property_credit_note_line_amount CHECK (amount > 0)
    );
END;

IF OBJECT_ID('dbo.checkout_overrides', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.checkout_overrides (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_checkout_overrides PRIMARY KEY,
        hotel_id BIGINT NOT NULL,
        reservation_id BIGINT NOT NULL,
        override_type VARCHAR(30) NOT NULL,
        outstanding_amount DECIMAL(19,0) NOT NULL,
        reason NVARCHAR(1000) NOT NULL,
        actor_id BIGINT NOT NULL,
        approved_by BIGINT NULL,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_checkout_override_created DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_checkout_override_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_checkout_override_reservation FOREIGN KEY (reservation_id) REFERENCES dbo.reservations(id),
        CONSTRAINT FK_checkout_override_actor FOREIGN KEY (actor_id) REFERENCES dbo.users(id),
        CONSTRAINT FK_checkout_override_approver FOREIGN KEY (approved_by) REFERENCES dbo.users(id),
        CONSTRAINT CK_checkout_override_type CHECK (override_type IN ('DEBT','OVERPAYMENT','OTHER')),
        CONSTRAINT CK_checkout_override_amount CHECK (outstanding_amount >= 0)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.reservation_charge_lines') AND name = 'IX_charge_lines_hotel_reservation')
    CREATE INDEX IX_charge_lines_hotel_reservation ON dbo.reservation_charge_lines(hotel_id, reservation_id, created_at);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.property_invoices') AND name = 'UX_property_invoice_finalized_reservation')
    CREATE UNIQUE INDEX UX_property_invoice_finalized_reservation ON dbo.property_invoices(reservation_id) WHERE status = 'FINALIZED';

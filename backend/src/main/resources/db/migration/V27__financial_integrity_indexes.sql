SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.property_payment_attempts') AND name = 'IX_property_attempt_hotel_reservation_status')
    CREATE INDEX IX_property_attempt_hotel_reservation_status ON dbo.property_payment_attempts(hotel_id, reservation_id, status, expires_at);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.property_payment_attempts') AND name = 'UX_property_attempt_provider_transaction')
    CREATE UNIQUE INDEX UX_property_attempt_provider_transaction ON dbo.property_payment_attempts(provider, environment, provider_transaction_ref) WHERE provider_transaction_ref IS NOT NULL;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.property_refund_attempts') AND name = 'UX_property_refund_provider_event')
    CREATE UNIQUE INDEX UX_property_refund_provider_event ON dbo.property_refund_attempts(provider, environment, provider_event_id) WHERE provider_event_id IS NOT NULL;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.property_invoices') AND name = 'IX_property_invoice_hotel_finalized')
    CREATE INDEX IX_property_invoice_hotel_finalized ON dbo.property_invoices(hotel_id, finalized_at, status);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.platform_subscription_orders') AND name = 'IX_platform_order_hotel_status')
    CREATE INDEX IX_platform_order_hotel_status ON dbo.platform_subscription_orders(target_hotel_id, status, expires_at);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.platform_payment_attempts') AND name = 'UX_platform_attempt_provider_transaction')
    CREATE UNIQUE INDEX UX_platform_attempt_provider_transaction ON dbo.platform_payment_attempts(provider, environment, provider_transaction_ref) WHERE provider_transaction_ref IS NOT NULL;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.platform_refund_attempts') AND name = 'UX_platform_refund_provider_event')
    CREATE UNIQUE INDEX UX_platform_refund_provider_event ON dbo.platform_refund_attempts(provider, environment, provider_event_id) WHERE provider_event_id IS NOT NULL;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.platform_refund_requests') AND name = 'IX_platform_refund_original_status')
    CREATE INDEX IX_platform_refund_original_status ON dbo.platform_refund_requests(original_transaction_id, status);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.financial_audit_events') AND name = 'IX_financial_audit_correlation')
    CREATE INDEX IX_financial_audit_correlation ON dbo.financial_audit_events(correlation_id, occurred_at);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.financial_audit_events') AND name = 'UX_financial_audit_idempotent_state')
    CREATE UNIQUE INDEX UX_financial_audit_idempotent_state
        ON dbo.financial_audit_events(context, aggregate_type, aggregate_id, idempotency_identity, new_state)
        WHERE idempotency_identity IS NOT NULL AND new_state IS NOT NULL;

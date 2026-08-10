SET XACT_ABORT ON;

BEGIN TRANSACTION;

INSERT INTO dbo.property_payment_configurations (
    hotel_id,
    enabled,
    environment,
    deposit_policy_type,
    deposit_value,
    payment_expiry_minutes,
    version,
    created_at,
    updated_at
)
SELECT
    h.id,
    1,
    'SANDBOX',
    'NONE',
    0,
    15,
    0,
    SYSUTCDATETIME(),
    SYSUTCDATETIME()
FROM dbo.hotels h
WHERE NOT EXISTS (
    SELECT 1
    FROM dbo.property_payment_configurations existing
    WHERE existing.hotel_id = h.id
);

INSERT INTO dbo.property_payment_configuration_methods (
    configuration_id,
    hotel_id,
    method,
    enabled,
    provider,
    merchant_reference_masked,
    created_at,
    updated_at
)
SELECT
    configuration.id,
    configuration.hotel_id,
    'VNPAY',
    1,
    'VNPAY',
    NULL,
    SYSUTCDATETIME(),
    SYSUTCDATETIME()
FROM dbo.property_payment_configurations configuration
WHERE configuration.enabled = 1
  AND configuration.environment = 'SANDBOX'
  AND NOT EXISTS (
      SELECT 1
      FROM dbo.property_payment_configuration_methods existing
      WHERE existing.configuration_id = configuration.id
        AND existing.method = 'VNPAY'
  );

COMMIT TRANSACTION;

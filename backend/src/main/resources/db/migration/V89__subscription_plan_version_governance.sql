ALTER TABLE subscription_plans ADD family_code varchar(50) NULL;
ALTER TABLE subscription_plans ADD version_number int NULL;
ALTER TABLE subscription_plans ADD duration_value int NULL;
ALTER TABLE subscription_plans ADD duration_unit varchar(20) NULL;
ALTER TABLE subscription_plans ADD record_version bigint NOT NULL CONSTRAINT DF_subscription_plan_record_version DEFAULT 0;
ALTER TABLE subscription_plans ADD activated_at datetime2 NULL;
ALTER TABLE subscription_plans ADD deactivated_at datetime2 NULL;
ALTER TABLE subscription_plans ADD creation_key_hash varchar(64) NULL;
ALTER TABLE subscription_plans ADD creation_payload_hash varchar(64) NULL;

UPDATE subscription_plans
SET family_code = UPPER(code), version_number = 1,
    duration_value = CASE WHEN is_lifetime = 1 THEN NULL ELSE 1 END,
    duration_unit = CASE WHEN is_lifetime = 1 THEN 'LIFETIME' WHEN billing_type = 'MONTHLY' THEN 'MONTH' ELSE 'YEAR' END,
    activated_at = CASE WHEN status = 'ACTIVE' THEN COALESCE(updated_at, created_at, SYSUTCDATETIME()) ELSE NULL END
WHERE family_code IS NULL;

UPDATE subscription_plans
SET status = 'INACTIVE', deactivated_at = COALESCE(deactivated_at, SYSUTCDATETIME())
WHERE price <= 0 AND status = 'ACTIVE';

ALTER TABLE subscription_plans ALTER COLUMN family_code varchar(50) NOT NULL;
ALTER TABLE subscription_plans ALTER COLUMN version_number int NOT NULL;
ALTER TABLE subscription_plans ADD CONSTRAINT UQ_subscription_plan_family_version UNIQUE (family_code, version_number);
CREATE UNIQUE INDEX UX_subscription_plan_creation_key ON subscription_plans(creation_key_hash) WHERE creation_key_hash IS NOT NULL;
CREATE UNIQUE INDEX UX_subscription_plan_active_family ON subscription_plans(family_code) WHERE status = 'ACTIVE';
IF EXISTS (SELECT 1 FROM plan_features GROUP BY plan_id, feature_code HAVING COUNT(*) > 1)
    THROW 51000, 'Duplicate plan feature keys must be resolved before subscription plan governance migration.', 1;
CREATE UNIQUE INDEX UX_plan_feature_code ON plan_features(plan_id, feature_code);

CREATE TABLE subscription_plan_admin_operations (
    id bigint IDENTITY(1,1) NOT NULL PRIMARY KEY,
    key_hash varchar(64) NOT NULL,
    action varchar(20) NOT NULL,
    plan_id bigint NOT NULL,
    result_status varchar(20) NOT NULL,
    request_hash varchar(64) NOT NULL,
    created_at datetime2 NOT NULL,
    CONSTRAINT UQ_subscription_plan_admin_key UNIQUE(key_hash),
    CONSTRAINT FK_subscription_plan_admin_plan FOREIGN KEY(plan_id) REFERENCES subscription_plans(id)
);
ALTER TABLE subscription_plans ADD CONSTRAINT CK_subscription_plan_version_positive CHECK (version_number > 0);
ALTER TABLE subscription_plans ADD CONSTRAINT CK_subscription_plan_duration CHECK (
    (is_lifetime = 1 AND duration_unit = 'LIFETIME' AND duration_value IS NULL)
    OR (is_lifetime = 0 AND duration_unit IN ('DAY','MONTH','YEAR') AND duration_value BETWEEN 1 AND 120));

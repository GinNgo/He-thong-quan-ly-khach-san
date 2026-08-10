IF COL_LENGTH('dbo.user_properties', 'billing_admin') IS NOT NULL
   AND NOT EXISTS (
       SELECT 1
       FROM sys.default_constraints dc
       JOIN sys.columns c
         ON c.object_id = dc.parent_object_id
        AND c.column_id = dc.parent_column_id
       WHERE dc.parent_object_id = OBJECT_ID('dbo.user_properties')
         AND c.name = 'billing_admin'
   )
BEGIN
    ALTER TABLE dbo.user_properties
        ADD CONSTRAINT DF_user_properties_billing_admin DEFAULT (0) FOR billing_admin;
END;

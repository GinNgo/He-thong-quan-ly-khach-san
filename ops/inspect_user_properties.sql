SET NOCOUNT ON;
SELECT c.name, t.name AS type_name, c.is_nullable, dc.definition AS default_definition
FROM sys.columns c
JOIN sys.types t ON t.user_type_id = c.user_type_id
LEFT JOIN sys.default_constraints dc ON dc.object_id = c.default_object_id
WHERE c.object_id = OBJECT_ID('dbo.user_properties')
ORDER BY c.column_id;

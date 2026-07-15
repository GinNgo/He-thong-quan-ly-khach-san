DECLARE @roomTypeCodeUnique sysname;
SELECT TOP 1 @roomTypeCodeUnique=kc.name
FROM sys.key_constraints kc
JOIN sys.index_columns ic ON ic.object_id=kc.parent_object_id AND ic.index_id=kc.unique_index_id
JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
WHERE kc.parent_object_id=OBJECT_ID('room_types') AND kc.type='UQ' AND c.name='code'
GROUP BY kc.name
HAVING COUNT(*)=1;
IF @roomTypeCodeUnique IS NOT NULL EXEC('ALTER TABLE room_types DROP CONSTRAINT ['+@roomTypeCodeUnique+']');

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('room_types') AND name='UK_room_types_hotel_code')
    CREATE UNIQUE INDEX UK_room_types_hotel_code ON room_types(hotel_id,code);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('locations') AND name='UX_locations_type_source_code')
    CREATE UNIQUE INDEX UX_locations_type_source_code ON locations(location_type,source_code) WHERE source_code IS NOT NULL;

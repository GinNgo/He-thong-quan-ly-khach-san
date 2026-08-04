SET QUOTED_IDENTIFIER ON;

IF OBJECT_ID('dbo.room_type_images', 'U') IS NOT NULL
BEGIN
    ;WITH ranked AS (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY room_type_id ORDER BY sort_order, id) - 1 AS normalized_order
        FROM dbo.room_type_images
    )
    UPDATE image_row SET sort_order = ranked.normalized_order
    FROM dbo.room_type_images image_row JOIN ranked ON ranked.id = image_row.id;

    ;WITH primary_rank AS (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY room_type_id ORDER BY CASE WHEN is_primary = 1 THEN 0 ELSE 1 END, sort_order, id) AS position
        FROM dbo.room_type_images
    )
    UPDATE image_row SET is_primary = CASE WHEN primary_rank.position = 1 THEN 1 ELSE 0 END
    FROM dbo.room_type_images image_row JOIN primary_rank ON primary_rank.id = image_row.id;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.room_type_images') AND name = 'UX_room_type_images_type_order_v84')
        CREATE UNIQUE INDEX UX_room_type_images_type_order_v84 ON dbo.room_type_images(room_type_id, sort_order);

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.room_type_images') AND name = 'UX_room_type_images_one_primary_v84')
        CREATE UNIQUE INDEX UX_room_type_images_one_primary_v84 ON dbo.room_type_images(room_type_id) WHERE is_primary = 1;
END;

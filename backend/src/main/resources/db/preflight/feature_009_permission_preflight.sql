SELECT role_id, function_id, COUNT(*) AS duplicate_count
FROM dbo.app_role_permission
GROUP BY role_id, function_id
HAVING COUNT(*) > 1;

SELECT rp.id, rp.role_id, rp.function_id, rp.action_mask
FROM dbo.app_role_permission rp
WHERE rp.action_mask < 0 OR (rp.action_mask & ~127) <> 0;

SELECT rp.id, f.code, rp.action_mask, f.supported_action_mask
FROM dbo.app_role_permission rp
JOIN dbo.app_function f ON f.id = rp.function_id
WHERE (rp.action_mask & ~f.supported_action_mask) <> 0
   OR (rp.action_mask <> 0 AND (rp.action_mask & 1) = 0);


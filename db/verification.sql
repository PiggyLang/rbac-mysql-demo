USE rbac_mysql_demo;

-- User -> role -> API permission. Bob has the reader role and user:read only.
SELECT u.username, r.code AS role_code, p.code AS permission_code
FROM `user` u
JOIN user_role ur ON ur.user_id = u.id
JOIN `role` r ON r.id = ur.role_id
JOIN role_permission rp ON rp.role_id = r.id
JOIN permission p ON p.id = rp.permission_id
ORDER BY u.username, p.code;

-- User -> role -> menu. Menus are navigation data, not API authorization.
SELECT u.username, r.code AS role_code, m.code AS menu_code, m.path
FROM `user` u
JOIN user_role ur ON ur.user_id = u.id
JOIN `role` r ON r.id = ur.role_id
JOIN role_menu rm ON rm.role_id = r.id
JOIN menu m ON m.id = rm.menu_id
ORDER BY u.username, m.sort_order;

-- Count only non-sensitive records for the health check comparison.
SELECT 'user' AS table_name, COUNT(*) AS row_count FROM `user`
UNION ALL SELECT 'role', COUNT(*) FROM `role`
UNION ALL SELECT 'permission', COUNT(*) FROM permission
UNION ALL SELECT 'menu', COUNT(*) FROM menu
UNION ALL SELECT 'user_role', COUNT(*) FROM user_role
UNION ALL SELECT 'role_permission', COUNT(*) FROM role_permission
UNION ALL SELECT 'role_menu', COUNT(*) FROM role_menu;

-- Negative test 1: expect ERROR 1062 for the duplicate composite relation, then rollback.
START TRANSACTION;
INSERT INTO user_role (user_id, role_id)
SELECT user_id, role_id FROM user_role LIMIT 1;
ROLLBACK;

-- Negative test 2: expect ERROR 1452 for a nonexistent referenced user, then rollback.
START TRANSACTION;
INSERT INTO user_role (user_id, role_id)
SELECT 18446744073709551615, id FROM `role` WHERE code = 'reader';
ROLLBACK;

-- For repeat-seed verification, open db/seed.sql separately in Navicat and execute it.
-- Compare row counts and custom field edits before and after that execution.

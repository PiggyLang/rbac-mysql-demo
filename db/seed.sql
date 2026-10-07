-- Idempotent additions by business key. Existing rows are never updated.
START TRANSACTION;

INSERT INTO `role` (code, name)
SELECT 'admin', 'Administrator' WHERE NOT EXISTS (SELECT 1 FROM `role` WHERE code = 'admin');
INSERT INTO `role` (code, name)
SELECT 'reader', 'Read-only user' WHERE NOT EXISTS (SELECT 1 FROM `role` WHERE code = 'reader');

INSERT INTO permission (code, name)
SELECT 'user:read', 'Read users' WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'user:read');
INSERT INTO permission (code, name)
SELECT 'user:create', 'Create users' WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'user:create');
INSERT INTO permission (code, name)
SELECT 'user:delete', 'Delete users' WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'user:delete');

INSERT INTO menu (code, name, path, sort_order)
SELECT 'users', 'User management', '/users', 10 WHERE NOT EXISTS (SELECT 1 FROM menu WHERE code = 'users');
INSERT INTO menu (code, name, path, sort_order)
SELECT 'reports', 'Reports', '/reports', 20 WHERE NOT EXISTS (SELECT 1 FROM menu WHERE code = 'reports');

-- Demo password for both accounts: learn-only-demo. Change it for any real use.
INSERT INTO `user` (username, password_hash, display_name)
SELECT 'alice', '$2a$10$zJVCdoMqvK4OHw7N7XHctuGrTtsXEfgsLGEqVNdt8jlfMn2zjqunK', 'Alice'
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE username = 'alice');
INSERT INTO `user` (username, password_hash, display_name)
SELECT 'bob', '$2a$10$auqm2Yc.5hem/ljvu1GwDu1wkjAvY.el/U7ny5vZ0bnPwKawLWJtS', 'Bob'
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE username = 'bob');

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id FROM `user` u JOIN `role` r ON r.code = 'admin'
WHERE u.username = 'alice' AND NOT EXISTS (
    SELECT 1 FROM user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id
);
INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id FROM `user` u JOIN `role` r ON r.code = 'reader'
WHERE u.username = 'bob' AND NOT EXISTS (
    SELECT 1 FROM user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id
);

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p
WHERE r.code = 'admin' AND p.code IN ('user:read', 'user:create', 'user:delete')
AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM `role` r JOIN permission p
WHERE r.code = 'reader' AND p.code = 'user:read'
AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

INSERT INTO role_menu (role_id, menu_id)
SELECT r.id, m.id FROM `role` r JOIN menu m
WHERE r.code = 'admin' AND m.code IN ('users', 'reports')
AND NOT EXISTS (SELECT 1 FROM role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
INSERT INTO role_menu (role_id, menu_id)
SELECT r.id, m.id FROM `role` r JOIN menu m
WHERE r.code = 'reader' AND m.code = 'reports'
AND NOT EXISTS (SELECT 1 FROM role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

COMMIT;

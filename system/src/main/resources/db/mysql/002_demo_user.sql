-- 仅供本地开发；不会覆盖已有同名用户。旧项目使用明文密码。
INSERT INTO ragent.t_user (id, username, password, role)
SELECT 'local-demo-user', 'demo', 'demo123456', 'user'
WHERE NOT EXISTS (SELECT 1 FROM ragent.t_user WHERE username = 'demo');

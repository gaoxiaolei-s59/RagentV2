SHOW CREATE TABLE ragent.t_user;
-- 不查询密码，避免输出凭证。
SELECT id, username, avatar, role, deleted FROM ragent.t_user WHERE username = 'demo';

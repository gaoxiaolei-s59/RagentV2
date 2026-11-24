-- 仅清理本模块提供的演示数据，其他账号不受影响。
DELETE FROM ragent.t_user WHERE id = 'local-demo-user' AND username = 'demo';
-- 如需撤销新表，请先确认无业务数据并备份后手动执行：
-- DROP TABLE ragent.t_user;

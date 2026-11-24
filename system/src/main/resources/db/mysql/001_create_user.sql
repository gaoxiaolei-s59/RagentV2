-- 与知识库共用 ragent 库，不改动知识库表。手动执行，不在启动时自动初始化。
CREATE TABLE IF NOT EXISTS ragent.t_user (
    id VARCHAR(64) NOT NULL COMMENT '用户ID，MyBatis-Plus 分配雪花ID',
    username VARCHAR(64) NOT NULL COMMENT '登录用户名',
    password VARCHAR(255) NOT NULL COMMENT '登录密码，兼容旧项目明文比对',
    avatar VARCHAR(1024) DEFAULT NULL COMMENT '头像地址',
    role VARCHAR(32) NOT NULL DEFAULT 'user' COMMENT '角色：admin / user',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常，1删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统登录用户';

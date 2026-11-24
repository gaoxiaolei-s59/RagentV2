# 用户登录

迁自 RagTest 的 user 模块，按 Controller → Service → ServiceImpl → Mapper 分层。
通过 bootstrap 启动，`mysql` profile 开启用户 Mapper、登录接口和登录拦截器。
启动示例：`java -jar bootstrap/target/bootstrap-1.0-SNAPSHOT.jar --spring.profiles.active=mysql`。

| 接口 | 用途 | 登录要求 |
| --- | --- | --- |
| POST /auth/login | 请求体 `{"userName":"demo","password":"demo123456"}`，返回 token、userId、avatar、role | 公开 |
| POST /auth/logout | 注销当前 token | 需要 |
| GET /api/user | 返回当前用户 userName、avatar | 需要 |

后续请求携带 `s-token: 登录返回的token`。沿用旧项目规则，mysql 模式下除登录外的 MVC 接口均需登录。
Cookie 不参与认证。默认 profile 保持原有无数据库启动行为。
Sa-Token 版本由父 POM 统一管理，使用 Spring Boot 4 starter；当前会话保存在内存，重启需重新登录。
角色目前仅返回给调用方，未引入角色权限管理。
本次保持旧项目明文密码校验兼容，正式使用前应另行迁移为密码哈希。

## 数据库变更审计

- 功能：用户登录；代码范围：`org.puregxl.ragent.system.user`；无业务编码、流程或字典配置。
- DDL：新增 `ragent.t_user`，代码只按 username 查询正常用户；MyBatis-Plus 自动添加 deleted=0。
- 字段：id VARCHAR(64) 主键；username VARCHAR(64) 非空唯一；password VARCHAR(255) 非空；avatar VARCHAR(1024) 可空；role VARCHAR(32) 默认 user；create_time/update_time DATETIME 自动时间；deleted TINYINT 默认0。
- 无既有表修改、外键、序列、权限表或工作流 DML。用户名全表唯一，逻辑删除后不能重用同名账号。
- 按顺序手动执行 `src/main/resources/db/mysql/001_create_user.sql` 建表，`002_demo_user.sql` 是可选本地演示数据，账号 demo / demo123456，不覆盖已有用户。
- `003_verify_user.sql` 验证表结构和演示用户；`004_rollback_user.sql` 清理演示数据，删表语句需备份并确认后手动执行。
- 未搬迁旧库用户数据；如需沿用旧账号，另行导入旧 t_user，保持字段及逻辑删除值一致。

验证记录（2026-11-24）：本机 Docker MySQL 的 ragent 库已执行建表并核对全部字段、主键及用户名唯一索引。
使用临时账号在实际 bootstrap HTTP 服务验证了未登录拦截、密码错误、登录、用户查询、登出、失效 token、逻辑删除用户拒绝登录，临时账号已清理。
演示数据脚本未执行。

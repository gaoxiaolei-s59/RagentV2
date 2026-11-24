# 知识库表结构

适用 MySQL 8.0.16+，数据库名为 `ragent`。依次执行建表脚本和只读验证脚本；这些脚本不由 Spring Boot 自动执行，也不依赖 Flyway。

- `001_create_knowledge_tables.sql`：创建数据库和三张业务表，重复执行不会删除已有数据；不会自动升级已有的同名表。
- `001_verify_knowledge_tables.sql`：查询真实字段、默认值、索引和约束。
- `001_rollback_knowledge_tables.sql`：手动删除三张表，数据不可恢复，保留数据库。

## 实体约定

实体位于 `org.puregxl.ragent.rag.knowledge.entity`，对应 `knowledge_base`、`knowledge_document`、`knowledge_chunk`。继承公共字段，使用 Lombok、MyBatis-Plus 雪花 ID、`@TableLogic` 和时间自动填充。时间类型使用 `java.util.Date`，与 framework 的填充器保持一致。两个状态枚举通过 `@EnumValue` 保存字符串，实体启用 `autoResultMap` 读取枚举。

默认启用，未删除；知识库默认按 800 字符切分、重叠 100 字符。文档创建时需要复制实际使用的切分参数。文档默认 `PENDING`，切片向量默认 `PENDING`。向量模型和维度允许暂时为空，向量入库前由业务校验。文件字节数使用 BIGINT，正文使用 LONGTEXT，校验值使用 SHA-256。

禁用和逻辑删除不由数据库外键级联；知识库服务已在事务中实现级联逻辑删除；后续文档及检索服务仍需维护父级有效性，文件/向量清理待对应存储服务接入。物理外键防止孤立文档及切片，切片的复合外键还保证知识库归属与文档一致。父子 ID 不应作为普通修改接口的可编辑字段。片段序号先使用普通排序索引，重复序号校验和重新切分策略在业务流程中实现。

实体是持久化对象，不直接作为接口请求或响应；后续 VO 需要将 Long ID 按字符串返回，避免前端整数精度丢失。

## 数据库变更审计

- 范围：三层实体和表结构，以及知识库 CRUD、分页和启用/禁用。文档上传已接入 RustFS 和 MySQL；其余文档/Chunk CRUD、切分和向量入库尚未实现。
- DDL：新增三张表，包含主键、分页/状态/文件校验值索引、字段检查约束和父子外键。
- 知识库管理的既有表依赖：`knowledge_base` 读写基本配置与启用状态；`knowledge_document`、`knowledge_chunk` 按知识库 ID 级联写入 `del_flag`、`update_time`；`knowledge_chunk.vector_status` 用于模型修改保护。本轮无需新增 DDL。
- 序列：无，ID 由应用生成。
- 配置表 DML、历史数据修复、正式种子数据：均无。
- 测试数据：可在事务中插入父子记录验证，再执行 ROLLBACK，不保留测试业务数据。
- 验证及回滚：使用同目录脚本；回滚脚本不应随验证执行。
- 运行依赖：本地 MySQL；实体新增不引入模型服务或向量数据库依赖。

后续业务需通过 DTO 校验文件信息、校验值格式、字符串长度及切分参数；这些实体本身不代表相应业务流程已经实现。

## 本地验证记录

2026-11-11：在已有 `local-mysql` 容器（MySQL 8.0.45）创建 `ragent` 数据库和三张表。通过 information_schema 核对字段、默认值、索引和约束；MyBatis-Plus 元数据与三张表共 41 个字段完全对应。

使用临时 Java/JDBC 验证程序和项目现有 MyBatis-Plus 配置完成雪花 ID、时间自动填充、枚举读写、中文及 emoji 正文、更新、禁用及逻辑删除验证；非法重叠长度和跨知识库切片关联被数据库拒绝。验证事务已回滚，未留下测试业务数据。该验证不包含尚未实现的 Controller、上传或向量处理流程。

实体阶段的完整构建曾被未完成的 Controller 阻断；知识库管理实现后该问题已解决。JDK 17 执行 `mvn -o -Dknowledge.mysql.test=true clean verify`，38 个测试全部通过，其中知识库真实 MySQL 集成测试 7 个。测试事务全部回滚，三张表均未留下测试业务数据。

可执行 JAR 以 `mysql` profile 启动后，已使用真实 HTTP 验证知识库创建、详情、修改、禁用、分页和删除；临时验证记录已清理，验证应用已停止。


## 文档上传数据库变更审计

- 结论：本轮无新增 DDL、序列或配置表 DML，复用三张表；无需执行新的数据库迁移。
- 代码范围：`KnowledgeChunkController`、`KnowledgeDocumentServiceImpl`、`KnowledgeDocumentMapper`。
- `knowledge_base`：读取名称、启用/删除状态、切分配置；最终保存时按 ID 锁定未删除记录并再次校验启用状态。
- `knowledge_document`：插入 id、knowledge_base_id、name、original_filename、file_extension、content_type、file_size、storage_key、sha256、process_status、chunk_size、chunk_overlap、enabled、del_flag、create_time、update_time；error_message 初始为空。字段均来自已有建表脚本。
- `knowledge_chunk`：上传不创建切片，不读写该表；状态 PENDING 等待后续解析流程。
- 测试数据：真实联调用例按每次创建的知识库 ID 隔离；结束后删除其文档对象和父子测试记录，不清空既有表或桶。
- 验证 SQL：继续使用同目录 `001_verify_knowledge_tables.sql`。本次没有额外回滚 SQL；若仅回退上传代码，不需要删除原有业务表或文件。
- 外部依赖：本地 MySQL、RustFS S3 API；无模型调用、向量入库或自动解析任务。

2026-11-24 验证：framework/infra/rag 的 `clean verify` 成功，共 52 个测试通过（上传业务单测 11 个、真实 RustFS/MySQL 联调 3 个）。可执行 JAR 已通过真实 multipart HTTP 上传，核对 RustFS 对象字节及 MySQL 已提交元数据；测试记录、对象和临时文件均已清理。无需新增表结构。

启动模块调整：应用入口和两份 YAML 已统一迁入 `bootstrap`，真实 MySQL/RustFS 集成测试也在 `bootstrap/src/test`；业务实体、Mapper 和 SQL 继续由 rag 维护。该装配迁移不涉及 DDL、配置表 DML 或业务数据修改。

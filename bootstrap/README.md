# RAgent Bootstrap 启动模块

`bootstrap` 是唯一可运行的 Spring Boot 4.0.1 / Java 17 启动模块，artifactId 为 `bootstrap`。依赖方向为 `bootstrap → rag → infra → framework`，由 `rag` 提供业务代码，启动类和应用配置统一位于 `bootstrap`。当前提供模型能力接口、知识库管理接口，文档上传已接入本地 RustFS，尚未包含完整切分及向量检索流程。

## 自动装配

`BootstrapApplication` 使用 `@SpringBootApplication(scanBasePackageClasses = {BootstrapApplication.class, RagController.class})`，扫描 bootstrap 装配包和 rag 业务包。framework/infra 通过自动装配接入。

```text
BootstrapApplication 启动
→ 扫描 bootstrap 与 rag 业务组件
→ Spring Boot 读取依赖 JAR 中的 AutoConfiguration.imports
→ framework：WebConfiguration / DataBaseConfiguration 按条件装配
→ infra：InfraConfiguration 绑定 ai 配置并装配资源、客户端、路由服务
→ RagController 通过构造器注入 LLMService / EmbeddingService / RerankService / TokenCounterService
```

应用仅显式扫描 bootstrap 和 rag，不手动扫描或导入 framework/infra。默认 profile 无数据源时跳过数据库装配；启动阶段不会调用模型服务或 RustFS。

## 数据库依赖

rag 业务模块显式引入 `mybatis-plus-spring`、`mybatis-plus-jsqlparser`、`mybatis-spring` 和 `spring-jdbc`，通过 framework 的 `DataBaseConfiguration` 接入 MyBatis-Plus。父 POM 的 `dependencyManagement` 管理依赖版本，`mybatis-plus.version` 和 `mybatis-spring.version` 分别统一控制 MyBatis-Plus 与 MyBatis-Spring 版本，子模块不单独声明版本。

bootstrap 已引入运行时 MySQL JDBC 驱动，版本由父 POM 的 Spring Boot BOM 管理。激活 `mysql` profile 后创建本地 DataSource、事务管理器并扫描知识库 Mapper；默认 profile 仍无需数据库。当前使用面向本地开发的 `DriverManagerDataSource`。

## 构建与启动

在项目根目录使用 JDK 17：

```bash
mvn -pl bootstrap -am clean package
java -jar bootstrap/target/bootstrap-1.0-SNAPSHOT.jar
```

也可以直接在 IDEA 运行 `org.puregxl.ragent.bootstrap.BootstrapApplication`。默认端口 8080，可通过 `SERVER_PORT` 修改。

模型配置在 `bootstrap/src/main/resources/application.yml`：

- `OLLAMA_BASE_URL`：默认 `http://localhost:11434`。
- `OLLAMA_CHAT_MODEL`：默认 `qwen3:8b`，需要自己在模型服务准备。
- `OLLAMA_EMBEDDING_MODEL`：默认 `nomic-embed-text`，需要自己在模型服务准备。
- 默认重排使用 `noop`，保持原始顺序并按 topN 截取。

## 接口

以下模型能力接口均使用 POST 和 JSON 请求体，返回 framework 的 `Result`。

| 地址 | 请求示例 | 作用 |
| --- | --- | --- |
| `/api/rag/chat` | `{"text":"你好"}` | 调用配置中的聊天模型 |
| `/api/rag/embedding` | `{"text":"文档片段"}` | 获取文本向量 |
| `/api/rag/tokens` | `{"text":"你好"}` | 本地估算 Token，无需模型服务 |
| `/api/rag/rerank` | `{"query":"问题","topN":1,"candidates":[{"id":"1","docId":"doc-1","text":"片段"}]}` | 重排候选片段 |

启动后可先验证不依赖外部服务的接口：

```bash
curl -X POST http://localhost:8080/api/rag/tokens \
  -H 'Content-Type: application/json' \
  -d '{"text":"你好"}'
```

应返回成功码 `0` 和 `data: 2`。聊天和向量化需要先启动并配置模型服务。

## 自动化测试

```bash
mvn -pl bootstrap -am test
```

已有缓存时可添加 `-o`；`-am` 一起构建并测试上游模块，避免使用本机旧 SNAPSHOT。

11 个模型集成测试位于 bootstrap，使用正式 `BootstrapApplication` 和 rag 的正式 Controller。模型端通过绑定 127.0.0.1 随机端口的 `LocalModelServer` 模拟，无需真实模型密钥。

覆盖：消费者自动装配、同步聊天、HTTP 500 后切换、单条/批量向量化、百炼重排及 docId 保留、SSE 回调、Token 估算、参数校验、业务异常、全部候选失败。

用于业务异常测试的 `/api/rag/business-error` 仅由测试配置导入，不包含在正式 JAR 中。应用集成测试、模拟模型服务及测试配置位于 `bootstrap/src/test`；上传业务单测及上传 demo 位于 `rag/src/test`。

全项目验证使用 `mvn clean verify`。实际供应商连通性及鉴权仍需在配置真实服务后联调。

## 知识库管理

按 `Controller → Service → ServiceImpl → Mapper` 分层，Request DTO 接收参数，Response DTO 返回数据，外层统一使用 `Result`。先执行 `rag/src/main/resources/db/mysql/001_create_knowledge_tables.sql` 创建表，再启动：

```bash
java -jar bootstrap/target/bootstrap-1.0-SNAPSHOT.jar --spring.profiles.active=mysql
```

`bootstrap/src/main/resources/application-mysql.yml` 默认连接 `127.0.0.1:3306/ragent`，本地账号为 `root/root`；可通过 `MYSQL_URL`、`MYSQL_USERNAME`、`MYSQL_PASSWORD` 覆盖。

| 方法 | 地址 | 作用 |
| --- | --- | --- |
| POST | `/knowledge-base` | 创建，返回字符串 ID |
| PUT | `/knowledge-base/{id}` | 完整修改基本配置 |
| DELETE | `/knowledge-base/{id}` | 级联逻辑删除知识库、文档、Chunk |
| GET | `/knowledge-base/{id}` | 查询详情，ID 按字符串返回 |
| GET | `/knowledge-base?pageNum=1&pageSize=10&name=示例&enabled=true` | 分页，支持名称模糊查询和启用状态过滤 |
| PATCH | `/knowledge-base/{id}/enabled` | 启用或禁用，请求体 `{"enabled":false}` |

创建及修改示例：

```json
{
  "name": "项目资料",
  "description": "项目知识库",
  "embeddingModelId": "local-embedding",
  "embeddingDimension": 768,
  "chunkSize": 800,
  "chunkOverlap": 100
}
```

模型 ID 和维度是配置示例，需要与实际服务匹配。创建最少只需 `name`；切分默认 800 字符、重叠 100 字符。PUT 为完整修改：未传的可选字段清空，未传的切分参数使用默认值；启用状态通过独立接口修改。分页返回 `pageNum`、`pageSize`、`total`、`records`，每页最多 100 条，按创建时间、ID 倒序。

已有成功向量索引时，直接修改向量模型或维度会被拒绝，需后续实现重建流程。禁用只改变知识库自身状态，保留子记录的启用设置；后续检索服务必须校验父级状态。删除目前只处理 MySQL 逻辑标记，文件和向量物理清理由后续存储功能接入。

真实 MySQL 集成测试（需先启动 MySQL 并建表）：

```bash
mvn -pl bootstrap -am -Dtest=KnowledgeBaseMysqlIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dknowledge.mysql.test=true test
```

测试事务自动回滚，不保留测试记录。默认构建跳过该测试，继续使用本地模拟模型测试；全量包含 MySQL 验证可执行 `mvn -pl bootstrap -am -Dknowledge.mysql.test=true clean verify`。

## 文档上传（RustFS）

现有 `KnowledgeChunkController` 的上传方法已实现，调用链为 `Controller → KnowledgeDocumentService → KnowledgeDocumentServiceImpl → RustFS / Mapper`。接口地址为：

```text
POST /knowledge-base/{kb-id}/docs/upload
Content-Type: multipart/form-data
```

先启动已有 Docker 容器并运行 mysql profile 应用：

```bash
docker start rustfs local-mysql
java -jar bootstrap/target/bootstrap-1.0-SNAPSHOT.jar --spring.profiles.active=mysql
```

先创建并取得知识库 ID，再上传文件（替换 ID 和本地文件路径）：

```bash
curl -X POST http://localhost:8080/knowledge-base/你的知识库ID/docs/upload \
  -F 'file=@/absolute/path/example.txt' \
  -F 'name=示例文档' \
  -F 'chunkSize=800' \
  -F 'chunkOverlap=100'
```

`file` 必填；`name`、`chunkSize`、`chunkOverlap` 可选。未传名称时使用原文件名，未传切分参数时继承知识库配置。默认允许 txt、md、pdf、doc、docx、xls、xlsx、ppt、pptx、csv，限制单文件 50MB；格式支持仅表示可以保存原文件，不代表已经实现对应解析器。

返回 `Result<KnowledgeDocumentResponse>`，包含字符串文档 ID、知识库 ID、文件信息、SHA-256、对象 key、实际切分参数及时间。上传后文档状态为 `PENDING`，本次不自动解析、切分或向量化。

RustFS 配置位于 `bootstrap/src/main/resources/application-mysql.yml`：

| 环境变量 | 本地默认值 | 用途 |
| --- | --- | --- |
| `RUSTFS_ENDPOINT` | `http://127.0.0.1:9000` | S3 API 地址，控制台通常使用 9001 |
| `RUSTFS_ACCESS_KEY` | `rustfsadmin` | 本地开发访问账号 |
| `RUSTFS_SECRET_KEY` | `rustfsadmin` | 本地开发密钥 |
| `RUSTFS_BUCKET` | `ragent-documents` | 保存原文件的桶 |
| `RUSTFS_REGION` | `us-east-1` | S3 签名区域 |

通过官方 AWS Java SDK v2 使用 path-style 访问 RustFS。仅在上传时检查桶是否存在，不存在时创建，不开启公开访问；启动应用不访问 RustFS。账号和密钥使用环境变量覆盖，不将真实凭据写入代码。数据库保存桶内对象 key，更换桶时需要同步迁移原文件。

业务处理：校验知识库和文件 → 流式临时文件及 SHA-256 → 上传唯一对象 key → 短事务内重新锁定并检查知识库 → 保存文档记录。数据库写入或提交失败时尝试删除本次对象，补偿失败会保留原始异常并记录待清理 key。临时文件在结束时清理。这里是跨系统的尽力补偿，不是 MySQL 与对象存储的分布式事务。

真实 MySQL + RustFS 联调测试会上传、读回并校验对象内容，然后清理仅由测试创建的记录和对象，保留桶及已有业务数据：

```bash
mvn -pl bootstrap -am -Dtest=KnowledgeDocumentRustFsIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dknowledge.storage.test=true test
```

包含两组真实数据库测试及所有上游模块的完整验证：

```bash
mvn -pl bootstrap -am -Dknowledge.mysql.test=true -Dknowledge.storage.test=true clean verify
```

普通构建无需启动 MySQL/RustFS，会跳过这两组显式开启的外部服务测试。

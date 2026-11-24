# RAgent RAG 业务模块

`rag`（`ragent-rag`）保存模型能力接口、知识库与文档上传业务，依赖 `infra → framework`。本模块打包为普通 JAR，不包含启动类或 `application*.yml`，不执行 Spring Boot repackage。

启动类、YAML、运行时数据库驱动和应用集成测试统一位于 `bootstrap`。只运行 `org.puregxl.ragent.bootstrap.BootstrapApplication` 或其可执行 JAR；启动步骤、环境变量及接口说明见 [bootstrap/README.md](../bootstrap/README.md)。

- Controller、Service、Mapper、Request/Response、实体等业务代码位于 `src/main/java`。
- 业务表 SQL 位于 `src/main/resources/db/mysql`，由业务模块继续维护。
- 无外部依赖的上传业务单测及上传 demo 位于 `src/test`。
- 模型协议集成测试和需要 MySQL/RustFS 的联调测试位于 `bootstrap/src/test`。

在项目根目录运行业务单测：

```bash
mvn -pl rag -am test
```

构建并启动完整应用：

```bash
mvn -pl bootstrap -am clean package
java -jar bootstrap/target/bootstrap-1.0-SNAPSHOT.jar --spring.profiles.active=mysql
```

# 基础层迁移设计（用户已确认）

迁移 RagTest 的 infra/framework 主源码到 RagentV2，源仓库保持原样。
统一 org.puregxl.ragent 包名和 groupId，父工程 ragent-parent，模块 ragent-framework/ragent-infra，版本保留 1.0-SNAPSHOT。
framework 提供公共 DTO、异常、Web/SSE、数据库扩展和 MQ 接口；infra 依赖 framework，提供模型客户端、路由、熔断及 Token 估算。
以 AutoConfiguration.imports 为入口，不依赖启动类扫描；缺失用户 Bean 时提供默认实现，数据库仅在 DataSource 存在时装配。
保留 Java 17、Spring Boot 4.0.1 等源项目版本。不迁移业务、密钥和旧测试目录的历史实现。
验收：Maven clean verify、独立自动发现、配置绑定、用户 Bean 覆盖、无数据源启动、有数据源数据库装配、模型故障切换。外部服务不参与本地验收。

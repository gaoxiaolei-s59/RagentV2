# 基础层迁移实施计划

**Goal:** 迁移可构建且支持独立自动装配的 infra/framework。
**Architecture:** infra → framework，两个 JAR 通过 imports 注册配置，按条件创建 Bean。
**Tech Stack:** Java 17 / Maven / Spring Boot 4.0.1 / JUnit 5。

- [x] 统一根和模块 POM，添加最小容器测试，运行失败基线。
- [x] 复制两个模块主源码、保留许可证、替换包名、合并重复 DTO。
- [x] 补齐 infra 的 HTTP/线程池和模型 Bean 条件装配，更新 imports。
- [x] 补齐 Web/数据库条件装配与自定义 Bean 退让。
- [x] 使用 JDK 17 运行 mvn -o clean verify，排查并修复迁移问题。
- [x] 编写接入示例、装配流程与验证边界，完成源码检查。

验证文件：infra/src/test/java/org/puregxl/ragent/infra/InfraAutoConfigurationTest.java；framework/src/test/java/org/puregxl/ragent/framework/FrameworkAutoConfigurationTest.java。
实现文件：根和两个模块 pom.xml、两个模块 src/main 下源码及 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports。

## 验收结果

2026-10-26：JDK 17 执行 `mvn -o clean verify`，三个 reactor 项目全部 SUCCESS，15 个测试通过（framework 5、infra 10），两个 JAR 中均包含自动装配 imports。独立代码复查发现的字段二次注入和同 provider 冲突已修复并经回归测试验证。

迁移验收时目录尚未初始化 Git；用户随后要求提交，已初始化本地仓库并提交迁移成果。源 RagTest 未修改。

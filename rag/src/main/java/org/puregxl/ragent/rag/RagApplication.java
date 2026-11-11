package org.puregxl.ragent.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RAG 应用入口。默认只扫描当前业务包；framework 和 infra 通过各自
 * AutoConfiguration.imports 自动装配，无需配置跨模块扫描或手动导入。
 */
@SpringBootApplication
public class RagApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }
}

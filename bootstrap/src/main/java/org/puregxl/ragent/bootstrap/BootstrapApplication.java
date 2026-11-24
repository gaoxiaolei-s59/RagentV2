package org.puregxl.ragent.bootstrap;

import org.puregxl.ragent.rag.RagController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RAgent 唯一启动入口，集中加载 bootstrap 配置并装配 rag 业务模块。
 * <p>扫描 bootstrap 和 rag 包；framework、infra 仍通过自动装配接入，
 * 避免直接扫描基础模块导致重复注册。</p>
 */
@SpringBootApplication(scanBasePackageClasses = {BootstrapApplication.class, RagController.class})
public class BootstrapApplication {
    public static void main(String[] args) {
        SpringApplication.run(BootstrapApplication.class, args);
    }
}

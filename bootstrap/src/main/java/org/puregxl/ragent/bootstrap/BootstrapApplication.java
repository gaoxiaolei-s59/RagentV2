package org.puregxl.ragent.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RAgent 唯一启动入口，集中加载 bootstrap 配置并装配 rag、system 业务模块。
 * <p>扫描 bootstrap、rag 和 system.user 包；framework、infra 仍通过自动装配接入，
 * 避免直接扫描基础模块导致重复注册。</p>
 */
@SpringBootApplication(scanBasePackages = {"org.puregxl.ragent.bootstrap", "org.puregxl.ragent.rag", "org.puregxl.ragent.system"})
public class BootstrapApplication {
    public static void main(String[] args) {
        SpringApplication.run(BootstrapApplication.class, args);
    }
}

package org.puregxl.ragent.system.user.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 复用 bootstrap 装配的 MySQL 数据源和 framework 的 SqlSessionFactory。 */
@Configuration(proxyBeanMethods = false)
@Profile("mysql")
@MapperScan("org.puregxl.ragent.system.user.mapper")
public class UserMapperConfiguration {
}

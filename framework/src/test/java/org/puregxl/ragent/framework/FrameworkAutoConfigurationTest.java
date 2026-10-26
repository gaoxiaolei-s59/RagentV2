package org.puregxl.ragent.framework;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.puregxl.ragent.framework.web.GlobalExceptionHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.assertThat;

class FrameworkAutoConfigurationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Consumer {}

    @Configuration(proxyBeanMethods = false)
    static class Database {
        @Bean DataSource dataSource() { return new DriverManagerDataSource(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomWeb {
        @Bean GlobalExceptionHandler customHandler() { return new GlobalExceptionHandler(); }
    }

    @Test void noDatabaseOrWebRequired() {
        new ApplicationContextRunner().withUserConfiguration(Consumer.class).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(SqlSessionFactory.class)
                .doesNotHaveBean(GlobalExceptionHandler.class);
        });
    }

    @Test void webHandlerDiscoveredWithoutScanning() {
        new WebApplicationContextRunner().withUserConfiguration(Consumer.class).run(context ->
            assertThat(context).hasNotFailed().hasSingleBean(GlobalExceptionHandler.class));
    }

    @Test void respectsCustomWebHandler() {
        new WebApplicationContextRunner().withUserConfiguration(Consumer.class, CustomWeb.class).run(context ->
            assertThat(context).hasNotFailed().hasSingleBean(GlobalExceptionHandler.class).hasBean("customHandler"));
    }

    @Test void assemblesDatabaseWhenDataSourceProvided() {
        new ApplicationContextRunner().withUserConfiguration(Consumer.class, Database.class).run(context ->
            assertThat(context).hasNotFailed().hasSingleBean(SqlSessionFactory.class));
    }

    @Test void databaseClassesMayBeAbsentForConsumers() {
        new ApplicationContextRunner().withUserConfiguration(Consumer.class)
            .withClassLoader(new FilteredClassLoader("com.baomidou.mybatisplus", "org.apache.ibatis", "org.mybatis"))
            .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("sqlSessionFactory"));
    }
}

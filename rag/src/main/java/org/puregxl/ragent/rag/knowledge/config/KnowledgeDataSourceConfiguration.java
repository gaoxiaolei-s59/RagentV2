package org.puregxl.ragent.rag.knowledge.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * mysql profile 下的本地数据库配置。
 * <p>MapperScan 注册知识库 Mapper，EnableTransactionManagement 启用 Service 事务代理；
 * SqlSessionFactory、分页插件和字段填充器复用 framework 自动装配。</p>
 */
@Configuration(proxyBeanMethods = false)
@Profile("mysql")
@EnableTransactionManagement
@MapperScan("org.puregxl.ragent.rag.knowledge.mapper")
public class KnowledgeDataSourceConfiguration {
    /**
     * 创建面向本地开发的非池化 MySQL 数据源。
     *
     * @param url JDBC 连接地址
     * @param username 数据库用户名
     * @param password 数据库密码，由配置或环境变量提供
     * @return 供 MyBatis 和事务管理器共用的数据源
     */
    @Bean
    public DataSource dataSource(@Value("${spring.datasource.url}") String url,
                                 @Value("${spring.datasource.username}") String username,
                                 @Value("${spring.datasource.password}") String password) {
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        source.setUrl(url);
        source.setUsername(username);
        source.setPassword(password);
        return source;
    }

    /**
     * 创建 JDBC 事务管理器，使父子表更新参与同一个 Spring 事务。
     *
     * @param dataSource 与 MyBatis 共用的数据源
     * @return 本地数据库事务管理器
     */
    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}

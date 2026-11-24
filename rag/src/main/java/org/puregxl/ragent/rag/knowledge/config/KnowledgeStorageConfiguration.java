package org.puregxl.ragent.rag.knowledge.config;

import org.puregxl.ragent.rag.knowledge.storage.DocumentStorage;
import org.puregxl.ragent.rag.knowledge.storage.RustFsDocumentStorage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Duration;

/** mysql profile 下的 RustFS S3 客户端，构造 Bean 时不访问外部服务。 */
@Configuration(proxyBeanMethods = false)
@Profile("mysql")
@EnableConfigurationProperties(KnowledgeStorageProperties.class)
public class KnowledgeStorageConfiguration {
    /** SDK 客户端由 Spring 管理关闭，连接地址和凭据从配置读取。 */
    @Bean(destroyMethod = "close")
    public S3Client documentS3Client(KnowledgeStorageProperties properties) {
        return S3Client.builder()
            .endpointOverride(properties.getEndpoint())
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
            .region(Region.of(properties.getRegion()))
            .forcePathStyle(true)
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .httpClientBuilder(UrlConnectionHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(60)))
            .overrideConfiguration(builder -> builder.apiCallTimeout(Duration.ofMinutes(2))
                .apiCallAttemptTimeout(Duration.ofSeconds(70)))
            .build();
    }

    /** 使用 RustFS 的 S3 兼容接口实现原文件保存和补偿删除。 */
    @Bean
    public DocumentStorage documentStorage(S3Client documentS3Client, KnowledgeStorageProperties properties) {
        return new RustFsDocumentStorage(documentS3Client, properties);
    }
}

package org.puregxl.ragent.infra.config;

import okhttp3.OkHttpClient;
import org.puregxl.ragent.infra.chat.*;
import org.puregxl.ragent.infra.embedding.*;
import org.puregxl.ragent.infra.model.*;
import org.puregxl.ragent.infra.rerank.*;
import org.puregxl.ragent.infra.token.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

/**
 * 模型基础设施自动装配入口。通过 imports 发现，无需业务启动类扫描本模块。
 * 默认实现只构造本地对象，不在启动阶段请求模型；同类型业务 Bean 优先。
 * 同步和流式 HTTP 客户端按名称隔离，线程池随 Spring 容器关闭。
 */
@AutoConfiguration
@ConditionalOnClass(OkHttpClient.class)
@ConditionalOnProperty(prefix = "ragent.infra", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(AIModelProperties.class)
public class InfraConfiguration {
    /**
     * 同步请求设定总超时，避免单次模型调用长期占用业务线程。
     */
    @Bean
    @ConditionalOnMissingBean(name = "syncHttpClient")
    public OkHttpClient syncHttpClient() {
        return new OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(10))
                .writeTimeout(Duration.ofSeconds(30)).readTimeout(Duration.ofSeconds(30))
                .callTimeout(Duration.ofSeconds(45)).retryOnConnectionFailure(true).build();
    }

    /**
     * SSE 允许持续读取；首包超时和取消由路由层管理。
     */
    @Bean
    @ConditionalOnMissingBean(name = "streamingHttpClient")
    public OkHttpClient streamingHttpClient() {
        return new OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(30))
                .writeTimeout(Duration.ofSeconds(60)).readTimeout(Duration.ZERO)
                .callTimeout(Duration.ZERO).retryOnConnectionFailure(true).build();
    }

    /**
     * 有界队列隔离流式任务；饱和时拒绝，由调用层报告错误，不无限堆积。
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "modelStreamExecutor")
    public ExecutorService modelStreamExecutor() {
        int cpu = Runtime.getRuntime().availableProcessors();
        return new ThreadPoolExecutor(Math.max(2, cpu / 2), Math.max(4, cpu),
                60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(200),
                new org.springframework.scheduling.concurrent.CustomizableThreadFactory("model-stream-"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    @ConditionalOnMissingBean
    public ModelHealthStore modelHealthStore(AIModelProperties properties) {
        return new ModelHealthStore(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ModelSelector modelSelector(AIModelProperties properties, ModelHealthStore health) {
        return new ModelSelector(properties, health);
    }

    @Bean
    @ConditionalOnMissingBean
    public ModelRoutingExecutor modelRoutingExecutor(ModelHealthStore health) {
        return new ModelRoutingExecutor(health);
    }

    @Bean
    @ConditionalOnMissingBean(TokenCounterService.class)
    public HeuristicTokenCounterService tokenCounterService() {
        return new HeuristicTokenCounterService();
    }

    @Bean
    @ConditionalOnMissingBean(value = BaiLianChatClient.class, name = "baiLianChatClient")
    public BaiLianChatClient baiLianChatClient(@Qualifier("syncHttpClient") OkHttpClient sync,
                                               @Qualifier("streamingHttpClient") OkHttpClient streaming,
                                               @Qualifier("modelStreamExecutor") Executor executor) {
        return new BaiLianChatClient(sync, streaming, executor);
    }

    @Bean
    @ConditionalOnMissingBean(value = OllamaChatClient.class, name = "ollamaChatClient")
    public OllamaChatClient ollamaChatClient(@Qualifier("syncHttpClient") OkHttpClient sync,
                                             @Qualifier("streamingHttpClient") OkHttpClient streaming,
                                             @Qualifier("modelStreamExecutor") Executor executor) {
        return new OllamaChatClient(sync, streaming, executor);
    }

    @Bean
    @ConditionalOnMissingBean(value = SiliconFlowChatClient.class, name = "siliconFlowChatClient")
    public SiliconFlowChatClient siliconFlowChatClient(@Qualifier("syncHttpClient") OkHttpClient sync,
                                                       @Qualifier("streamingHttpClient") OkHttpClient streaming,
                                                       @Qualifier("modelStreamExecutor") Executor executor) {
        return new SiliconFlowChatClient(sync, streaming, executor);
    }

    @Bean
    @ConditionalOnMissingBean(value = OllamaEmbeddingClient.class, name = "ollamaEmbeddingClient")
    public OllamaEmbeddingClient ollamaEmbeddingClient(@Qualifier("syncHttpClient") OkHttpClient sync) {
        return new OllamaEmbeddingClient(sync);
    }

    @Bean
    @ConditionalOnMissingBean(value = SiliconFlowEmbeddingClient.class, name = "siliconFlowEmbeddingClient")
    public SiliconFlowEmbeddingClient siliconFlowEmbeddingClient(@Qualifier("syncHttpClient") OkHttpClient sync) {
        return new SiliconFlowEmbeddingClient(sync);
    }

    @Bean
    @ConditionalOnMissingBean(value = BaiLianRerankClient.class, name = "baiLianRerankClient")
    public BaiLianRerankClient baiLianRerankClient(@Qualifier("syncHttpClient") OkHttpClient sync) {
        return new BaiLianRerankClient(sync);
    }

    @Bean
    @ConditionalOnMissingBean(value = NoopRerankClient.class, name = "noopRerankClient")
    public NoopRerankClient noopRerankClient() {
        return new NoopRerankClient();
    }

    /**
     * 业务依赖服务接口；默认路由器收集已注册客户端，实现选择、熔断和故障切换。
     */
    @Bean
    @ConditionalOnMissingBean(LLMService.class)
    public RoutingLLMService llmService(ModelSelector selector, ModelHealthStore health,
                                        ModelRoutingExecutor executor, List<ChatClient> clients) {
        return new RoutingLLMService(selector, health, executor, clients);
    }

    @Bean
    @ConditionalOnMissingBean(EmbeddingService.class)
    public RoutingEmbeddingService embeddingService(ModelSelector selector,
                                                    ModelRoutingExecutor executor, List<EmbeddingClient> clients) {
        return new RoutingEmbeddingService(selector, executor, clients);
    }

    @Bean
    @ConditionalOnMissingBean(RerankService.class)
    public RoutingRerankService rerankService(ModelSelector selector,
                                              ModelRoutingExecutor executor, List<RerankClient> clients) {
        return new RoutingRerankService(selector, executor, clients);
    }
}

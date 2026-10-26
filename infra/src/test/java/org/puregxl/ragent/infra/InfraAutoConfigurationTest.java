package org.puregxl.ragent.infra;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import okhttp3.OkHttpClient;
import org.puregxl.ragent.infra.chat.ChatClient;
import org.puregxl.ragent.infra.model.ModelHealthStore;
import org.puregxl.ragent.framework.convention.ChatRequest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import org.puregxl.ragent.infra.chat.LLMService;
import org.puregxl.ragent.infra.config.AIModelProperties;
import org.puregxl.ragent.infra.embedding.EmbeddingService;
import org.puregxl.ragent.infra.rerank.RerankService;
import org.puregxl.ragent.infra.token.TokenCounterService;
import static org.assertj.core.api.Assertions.assertThat;

class InfraAutoConfigurationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Consumer {}

    @Test
    void discoversInfraWithoutScanningAndBindsProperties() {
        new ApplicationContextRunner().withUserConfiguration(Consumer.class)
            .withPropertyValues("ai.chat.default-model=test-model")
            .run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(LLMService.class)
                    .hasSingleBean(EmbeddingService.class).hasSingleBean(RerankService.class)
                    .hasSingleBean(TokenCounterService.class)
                    .hasBean("syncHttpClient").hasBean("streamingHttpClient").hasBean("modelStreamExecutor");
                assertThat(context.getBean(AIModelProperties.class).getChat().getDefaultModel()).isEqualTo("test-model");
                assertThat(context.getBean(TokenCounterService.class).countTokens("你好")).isEqualTo(2);
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class Overrides {
        @Bean LLMService customLlm() { return mock(LLMService.class); }
        @Bean TokenCounterService customTokenCounter() { return text -> 42; }
        @Bean OkHttpClient syncHttpClient() { return new OkHttpClient(); }
        @Bean(destroyMethod = "shutdown") ExecutorService modelStreamExecutor() { return Executors.newSingleThreadExecutor(); }
    }

    @Test void respectsApplicationOverrides() {
        new ApplicationContextRunner().withUserConfiguration(Consumer.class, Overrides.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(LLMService.class).hasSingleBean(TokenCounterService.class);
            assertThat(context.getBean(LLMService.class)).isSameAs(context.getBean("customLlm"));
            assertThat(context.getBean(TokenCounterService.class).countTokens("hello")).isEqualTo(42);
            assertThat(context).hasBean("syncHttpClient").doesNotHaveBean("llmService");
        });
    }

    @Test void canDisableInfra() {
        new ApplicationContextRunner().withUserConfiguration(Consumer.class)
            .withPropertyValues("ragent.infra.enabled=false").run(context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(LLMService.class).doesNotHaveBean(AIModelProperties.class));
    }

    @Test void closesOwnedExecutorWithContext() {
        AtomicReference<ExecutorService> executor = new AtomicReference<>();
        new ApplicationContextRunner().withUserConfiguration(Consumer.class).run(context -> {
            executor.set(context.getBean("modelStreamExecutor", ExecutorService.class));
            assertThat(executor.get().isShutdown()).isFalse();
        });
        assertThat(executor.get().isShutdown()).isTrue();
    }

    /** 使用内存客户端模拟首选模型故障，验证配置绑定、候选排序、降级与熔断整条链路。 */
    @Test void fallsBackAndSkipsOpenModelOnNextCall() {
        ChatClient client = mock(ChatClient.class);
        when(client.provider()).thenReturn("test");
        when(client.chat(any(), any())).thenAnswer(invocation -> {
            var target = invocation.getArgument(1, org.puregxl.ragent.infra.model.ModelTarget.class);
            if (target.id().equals("first")) throw new IllegalStateException("模拟首选模型故障");
            return "fallback-response";
        });
        new ApplicationContextRunner().withUserConfiguration(Consumer.class)
            .withBean("testChatClient", ChatClient.class, () -> client)
            .withPropertyValues("ai.providers.test.url=http://localhost", "ai.selection.failure-threshold=1",
                "ai.chat.default-model=first", "ai.chat.candidates[0].id=first",
                "ai.chat.candidates[0].provider=test", "ai.chat.candidates[0].model=first-model",
                "ai.chat.candidates[1].id=second", "ai.chat.candidates[1].provider=test",
                "ai.chat.candidates[1].model=second-model")
            .run(context -> {
                assertThat(context).hasNotFailed();
                LLMService service = context.getBean(LLMService.class);
                assertThat(service.chat(ChatRequest.builder().build())).isEqualTo("fallback-response");
                assertThat(context.getBean(ModelHealthStore.class).isUnavailable("first")).isTrue();
                assertThat(service.chat(ChatRequest.builder().build())).isEqualTo("fallback-response");
                verify(client, times(3)).chat(any(), any());
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class UnrelatedPrimary {
        @Bean @org.springframework.context.annotation.Primary
        OkHttpClient unrelatedHttpClient() { return new OkHttpClient(); }
        @Bean @org.springframework.context.annotation.Primary
        java.util.concurrent.Executor unrelatedExecutor() { return Runnable::run; }
    }

    @Test void namedResourcesAreNotReplacedByUnrelatedPrimaryBeans() {
        new ApplicationContextRunner().withUserConfiguration(Consumer.class, UnrelatedPrimary.class).run(context -> {
            var client = context.getBean(org.puregxl.ragent.infra.chat.BaiLianChatClient.class);
            assertThat(org.springframework.test.util.ReflectionTestUtils.getField(client, "syncHttpClient"))
                .isSameAs(context.getBean("syncHttpClient"));
            assertThat(org.springframework.test.util.ReflectionTestUtils.getField(client, "streamingHttpClient"))
                .isSameAs(context.getBean("streamingHttpClient"));
            assertThat(org.springframework.test.util.ReflectionTestUtils.getField(client, "modelStreamExecutor"))
                .isSameAs(context.getBean("modelStreamExecutor"));
        });
    }

    @Test void customProviderCanReplaceDefaultUnderAnotherBeanName() {
        var custom = mock(org.puregxl.ragent.infra.rerank.RerankClient.class);
        when(custom.provider()).thenReturn("noop");
        new ApplicationContextRunner().withUserConfiguration(Consumer.class)
            .withBean("myRerankClient", org.puregxl.ragent.infra.rerank.RerankClient.class, () -> custom)
            .withPropertyValues("ai.rerank.candidates[0].id=noop-model", "ai.rerank.candidates[0].provider=noop")
            .run(context -> {
                assertThat(context).hasNotFailed();
                var chunks = java.util.List.<org.puregxl.ragent.framework.convention.RetrievedChunk>of();
                when(custom.rerank(eq("query"), eq(chunks), eq(1), any())).thenReturn(chunks);
                assertThat(context.getBean(RerankService.class).rerank("query", chunks, 1)).isSameAs(chunks);
                verify(custom).rerank(eq("query"), eq(chunks), eq(1), any());
            });
    }
}

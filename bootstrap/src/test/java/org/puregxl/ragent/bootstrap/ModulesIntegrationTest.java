package org.puregxl.ragent.bootstrap;

import com.google.gson.*;
import org.puregxl.ragent.testfixture.TestErrorConfiguration;
import org.junit.jupiter.api.*;
import org.puregxl.ragent.framework.web.GlobalExceptionHandler;
import org.puregxl.ragent.infra.chat.*;
import org.puregxl.ragent.infra.embedding.EmbeddingService;
import org.puregxl.ragent.infra.rerank.RerankService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

/** 从测试消费者的真实 HTTP 入口验证 framework → infra → 模型 HTTP 协议整条链路。 */
@SpringBootTest(classes = BootstrapApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.main.banner-mode=off", "server.address=127.0.0.1", "ai.selection.failure-threshold=100",
        "logging.level.org.puregxl.ragent.infra.model.ModelRoutingExecutor=OFF",
        "logging.level.org.puregxl.ragent.framework.web.GlobalExceptionHandler=OFF"})
@org.springframework.context.annotation.Import(TestErrorConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ModulesIntegrationTest {
    private static final LocalModelServer MODEL = new LocalModelServer();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @LocalServerPort int port;
    @Autowired ApplicationContext context;
    @Autowired LLMService llm;
    @Autowired EmbeddingService embedding;

    /** 所有供应商地址指向测试服务器；测试专用占位 Key 不对应任何真实账号。 */
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry properties) {
        for (String provider : List.of("ollama", "bailian")) {
            properties.add("ai.providers." + provider + ".url", MODEL::baseUrl);
            properties.add("ai.providers." + provider + ".api-key", () -> "local-test-key");
        }
        properties.add("ai.providers.ollama.endpoints.chat", () -> "/v1/chat/completions");
        properties.add("ai.providers.ollama.endpoints.embedding", () -> "/v1/embeddings");
        properties.add("ai.providers.bailian.endpoints.rerank", () -> "/rerank");
        properties.add("ai.chat.default-model", () -> "primary");
        candidate(properties, "chat", 0, "primary", "ollama", "primary-model");
        candidate(properties, "chat", 1, "backup", "ollama", "backup-model");
        candidate(properties, "embedding", 0, "embedding", "ollama", "embedding-model");
        candidate(properties, "rerank", 0, "rerank", "bailian", "rerank-model");
    }

    private static void candidate(DynamicPropertyRegistry registry, String capability, int index,
                                  String id, String provider, String model) {
        String prefix = "ai." + capability + ".candidates[" + index + "].";
        registry.add(prefix + "id", () -> id);
        registry.add(prefix + "provider", () -> provider);
        registry.add(prefix + "model", () -> model);
    }

    @BeforeEach void clearRequests() { MODEL.clear(); }
    @AfterAll static void stopModelServer() { MODEL.close(); }

    @Test void discoversBothModulesWithoutScanningTheirPackages() {
        assertThat(context.getBeansOfType(LLMService.class)).hasSize(1);
        assertThat(context.getBeansOfType(EmbeddingService.class)).hasSize(1);
        assertThat(context.getBeansOfType(RerankService.class)).hasSize(1);
        assertThat(context.getBeansOfType(GlobalExceptionHandler.class)).hasSize(1);
        assertThat(context.containsBean("sqlSessionFactory")).isFalse();
    }

    @Test void chatUsesRealHttpClientAndUnifiedResult() throws Exception {
        JsonObject result = post("/api/rag/chat", "{\"text\":\"hello\"}");
        assertThat(result.get("code").getAsString()).isEqualTo("0");
        assertThat(result.get("data").getAsString()).isEqualTo("answer:hello");
        assertThat(MODEL.received()).hasSize(1);
        var request = MODEL.received().get(0);
        assertThat(request.path()).isEqualTo("/v1/chat/completions");
        assertThat(request.body().get("model").getAsString()).isEqualTo("primary-model");
        assertThat(request.body().getAsJsonArray("messages").get(0).getAsJsonObject().get("role").getAsString())
            .isEqualTo("user");
    }

    @Test void chatFallsBackAfterUpstreamHttpFailure() throws Exception {
        JsonObject result = post("/api/rag/chat", "{\"text\":\"fallback\"}");
        assertThat(result.get("data").getAsString()).isEqualTo("answer:fallback");
        assertThat(MODEL.received().stream().map(r -> r.body().get("model").getAsString()).toList())
            .containsExactly("primary-model", "backup-model");
    }

    @Test void embeddingPassesThroughControllerAndParsesVector() throws Exception {
        JsonObject result = post("/api/rag/embedding", "{\"text\":\"文档片段\"}");
        assertThat(result.get("code").getAsString()).isEqualTo("0");
        assertThat(result.getAsJsonArray("data")).hasSize(3);
        assertThat(result.getAsJsonArray("data").get(1).getAsFloat()).isEqualTo(0.2f);
        assertThat(MODEL.received().get(0).body().getAsJsonArray("input").get(0).getAsString()).isEqualTo("文档片段");
    }

    @Test void batchEmbeddingUsesOneRequestForMultipleTexts() {
        assertThat(embedding.embedBatch(List.of("first", "second")))
            .containsExactly(List.of(0.1f, 0.2f, 0.3f), List.of(0.1f, 0.2f, 0.3f));
        assertThat(MODEL.received()).hasSize(1);
        assertThat(MODEL.received().get(0).body().getAsJsonArray("input")).hasSize(2);
    }

    @Test void rerankPreservesDocumentIdAndUsesProviderAuthentication() throws Exception {
        JsonObject result = post("/api/rag/rerank", """
            {"query":"问题","topN":1,"candidates":[
              {"id":"chunk-1","docId":"doc-1","text":"片段一"},
              {"id":"chunk-2","docId":"doc-2","text":"片段二"}]}
            """);
        assertThat(result.get("code").getAsString()).isEqualTo("0");
        assertThat(result.getAsJsonArray("data")).hasSize(1);
        JsonObject hit = result.getAsJsonArray("data").get(0).getAsJsonObject();
        assertThat(hit.get("id").getAsString()).isEqualTo("chunk-2");
        assertThat(hit.get("docId").getAsString()).isEqualTo("doc-2");
        assertThat(hit.get("score").getAsFloat()).isEqualTo(0.99f);
        assertThat(MODEL.received().get(0).authorization()).isEqualTo("Bearer local-test-key");
    }

    @Test void streamReadsActualSseAndCompletesCallback() throws Exception {
        StringBuffer content = new StringBuffer();
        CompletableFuture<String> completed = new CompletableFuture<>();
        StreamCancellationHandle handle = llm.streamChat("stream", new StreamCallback() {
            @Override public void onContent(String part) { content.append(part); }
            @Override public void onComplete() { completed.complete(content.toString()); }
            @Override public void onError(Throwable error) { completed.completeExceptionally(error); }
        });
        try {
            assertThat(completed.get(5, TimeUnit.SECONDS)).isEqualTo("你好，世界");
            assertThat(MODEL.received().get(0).body().get("stream").getAsBoolean()).isTrue();
        } finally {
            handle.cancel();
        }
    }

    @Test void tokenEstimateDoesNotCallModelServer() throws Exception {
        JsonObject result = post("/api/rag/tokens", "{\"text\":\"你好\"}");
        assertThat(result.get("data").getAsInt()).isEqualTo(2);
        assertThat(MODEL.received()).isEmpty();
    }

    @Test void validationErrorsUseFrameworkResponseAndSkipModelCall() throws Exception {
        JsonObject result = post("/api/rag/chat", "{\"text\":\" \"}");
        assertThat(result.get("code").getAsString()).isEqualTo("A000001");
        assertThat(result.get("message").getAsString()).isEqualTo("文本不能为空");
        assertThat(MODEL.received()).isEmpty();
    }

    @Test void businessExceptionUsesFrameworkResponse() throws Exception {
        var response = HTTP.send(HttpRequest.newBuilder(uri("/api/rag/business-error"))
            .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
        assertThat(result.get("code").getAsString()).isNotEqualTo("0");
        assertThat(result.get("message").getAsString()).isEqualTo("测试业务异常");
    }

    @Test void exhaustedModelsReturnUnifiedFailure() throws Exception {
        JsonObject result = post("/api/rag/chat", "{\"text\":\"all-fail\"}");
        assertThat(result.get("code").getAsString()).isNotEqualTo("0");
        assertThat(MODEL.received()).hasSize(2);
    }

    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }

    private JsonObject post(String path, String json) throws Exception {
        var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
}

package org.puregxl.ragent.rag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.puregxl.ragent.framework.convention.Result;
import org.puregxl.ragent.framework.convention.RetrievedChunk;
import org.puregxl.ragent.framework.web.Results;
import org.puregxl.ragent.infra.chat.LLMService;
import org.puregxl.ragent.infra.embedding.EmbeddingService;
import org.puregxl.ragent.infra.rerank.RerankService;
import org.puregxl.ragent.infra.token.TokenCounterService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 通过接口注入自动装配的基础服务，提供可直接调用的能力验证入口。 */
@RestController
@RequestMapping("/api/rag")
public class RagController {
    public record TextInput(@NotBlank(message = "文本不能为空") String text) {}
    public record RerankInput(@NotBlank String query, @NotNull List<RetrievedChunk> candidates, int topN) {}

    private final LLMService llm;
    private final EmbeddingService embedding;
    private final RerankService rerank;
    private final TokenCounterService tokens;

    public RagController(LLMService llm, EmbeddingService embedding,
                         RerankService rerank, TokenCounterService tokens) {
        this.llm = llm;
        this.embedding = embedding;
        this.rerank = rerank;
        this.tokens = tokens;
    }

    /** 请求经过 MVC、配置绑定、模型路由和真实模型 HTTP 协议后统一返回。 */
    @PostMapping("/chat")
    Result<String> chat(@Valid @RequestBody TextInput input) {
        return Results.success(llm.chat(input.text()));
    }

    @PostMapping("/embedding")
    Result<List<Float>> embedding(@Valid @RequestBody TextInput input) {
        return Results.success(embedding.embed(input.text()));
    }

    @PostMapping("/rerank")
    Result<List<RetrievedChunk>> rerank(@Valid @RequestBody RerankInput input) {
        return Results.success(rerank.rerank(input.query(), input.candidates(), input.topN()));
    }

    @PostMapping("/tokens")
    Result<Integer> tokens(@Valid @RequestBody TextInput input) {
        return Results.success(tokens.countTokens(input.text()));
    }
}

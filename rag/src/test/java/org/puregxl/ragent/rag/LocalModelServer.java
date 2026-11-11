package org.puregxl.ragent.rag;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 仅绑定本机随机端口的模型模拟服务，不访问外网。
 * 模拟 OpenAI 聊天/Embedding、百炼 Rerank 和 SSE 协议，并保存收到的请求供断言。
 */
final class LocalModelServer implements AutoCloseable {
    record Received(String path, JsonObject body, String authorization) {}
    private final HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final ConcurrentLinkedQueue<Received> received = new ConcurrentLinkedQueue<>();

    LocalModelServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::respond);
            server.setExecutor(executor);
            server.start();
        } catch (IOException e) {
            executor.shutdownNow();
            throw new UncheckedIOException(e);
        }
    }

    String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    List<Received> received() { return List.copyOf(received); }
    void clear() { received.clear(); }

    /** 根据请求内容触发确定的成功或失败，测试真正经过模型客户端和路由器。 */
    private void respond(HttpExchange exchange) throws IOException {
        try (exchange) {
            String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonObject body = JsonParser.parseString(raw).getAsJsonObject();
            String path = exchange.getRequestURI().getPath();
            received.add(new Received(path, body, exchange.getRequestHeaders().getFirst("Authorization")));
            if (path.equals("/v1/chat/completions")) {
                chat(exchange, body);
            } else if (path.equals("/v1/embeddings")) {
                JsonArray data = new JsonArray();
                JsonArray input = body.getAsJsonArray("input");
                for (int i = 0; i < input.size(); i++) {
                    JsonObject item = new JsonObject();
                    item.addProperty("index", i);
                    item.add("embedding", JsonParser.parseString("[0.1,0.2,0.3]"));
                    data.add(item);
                }
                JsonObject response = new JsonObject();
                response.add("data", data);
                send(exchange, 200, "application/json", response.toString());
            } else if (path.equals("/rerank")) {
                send(exchange, 200, "application/json", """
                    {"output":{"results":[{"index":1,"relevance_score":0.99}]}}
                    """);
            } else {
                send(exchange, 404, "application/json", "{}");
            }
        }
    }

    private void chat(HttpExchange exchange, JsonObject body) throws IOException {
        String prompt = body.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString();
        boolean primary = body.get("model").getAsString().equals("primary-model");
        if (prompt.equals("all-fail") || (prompt.equals("fallback") && primary)) {
            send(exchange, 500, "application/json", "{\"error\":\"模拟上游失败\"}");
        } else if (body.has("stream") && body.get("stream").getAsBoolean()) {
            send(exchange, 200, "text/event-stream", """
                data: {"choices":[{"delta":{"content":"你好"}}]}

                data: {"choices":[{"delta":{"content":"，世界"}}]}

                data: [DONE]

                """);
        } else {
            JsonObject message = new JsonObject();
            message.addProperty("role", "assistant");
            message.addProperty("content", "answer:" + prompt);
            JsonObject choice = new JsonObject();
            choice.add("message", message);
            JsonArray choices = new JsonArray();
            choices.add(choice);
            JsonObject response = new JsonObject();
            response.add("choices", choices);
            send(exchange, 200, "application/json", response.toString());
        }
    }

    private void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}

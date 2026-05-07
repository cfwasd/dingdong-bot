package com.napcat.llm.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.napcat.agent.llm.ChatMessage;
import com.napcat.agent.llm.LlmProvider;
import com.napcat.agent.llm.LlmResponse;
import com.napcat.agent.session.Session;
import com.napcat.agent.tool.ToolSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class OllamaProvider implements LlmProvider {

    private final String baseUrl;
    private final String model;
    private final long timeout;

    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient client;

    public OllamaProvider(String baseUrl, String model, long timeout) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.timeout = timeout;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public String getProviderName() {
        return "ollama";
    }

    @Override
    public CompletableFuture<LlmResponse> chat(Session session, String input, List<ToolSchema> tools) {
        CompletableFuture<LlmResponse> future = new CompletableFuture<>();
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("model", model);
            root.put("stream", false);

            ArrayNode messages = root.putArray("messages");
            for (ChatMessage msg : session.getHistory()) {
                ObjectNode node = messages.addObject();
                node.put("role", msg.getRole());
                if (msg.getContent() != null) {
                    node.put("content", msg.getContent());
                }
                if (msg.getName() != null) {
                    node.put("name", msg.getName());
                }
                if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                    ArrayNode tcArray = node.putArray("tool_calls");
                    for (ChatMessage.ToolCallData tc : msg.getToolCalls()) {
                        ObjectNode tcNode = tcArray.addObject();
                        tcNode.put("id", tc.getId());
                        tcNode.put("type", tc.getType());
                        ObjectNode fnNode = tcNode.putObject("function");
                        fnNode.put("name", tc.getFunction().getName());
                        fnNode.put("arguments", tc.getFunction().getArguments());
                    }
                }
            }

            String json = mapper.writeValueAsString(root);
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/chat")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, java.io.IOException e) {
                    future.completeExceptionally(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws java.io.IOException {
                    try (ResponseBody body = response.body()) {
                        String resp = body != null ? body.string() : "";
                        future.complete(parseResponse(resp));
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                }
            });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    private LlmResponse parseResponse(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            LlmResponse resp = new LlmResponse();
            resp.setContent(root.path("message").path("content").asText());
            return resp;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Ollama response", e);
        }
    }
}

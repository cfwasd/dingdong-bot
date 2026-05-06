package com.napcat.llm.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class AnthropicProvider implements LlmProvider {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final double temperature;
    private final long timeout;

    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient client;

    public AnthropicProvider(String baseUrl, String apiKey, String model, int maxTokens, double temperature, long timeout) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.timeout = timeout;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public String getProviderName() {
        return "anthropic";
    }

    @Override
    public CompletableFuture<LlmResponse> chat(Session session, String input, List<ToolSchema> tools) {
        CompletableFuture<LlmResponse> future = new CompletableFuture<>();
        try {
            var root = mapper.createObjectNode();
            root.put("model", model);
            root.put("max_tokens", maxTokens);
            root.put("temperature", temperature);

            var messages = root.putArray("messages");
            for (ChatMessage msg : session.getHistory()) {
                if ("system".equals(msg.getRole())) continue;
                var node = messages.addObject();
                node.put("role", msg.getRole());
                node.put("content", msg.getContent());
            }

            String systemPrompt = session.getHistory().stream()
                    .filter(m -> "system".equals(m.getRole()))
                    .findFirst()
                    .map(ChatMessage::getContent)
                    .orElse(null);
            if (systemPrompt != null) {
                root.put("system", systemPrompt);
            }

            if (tools != null && !tools.isEmpty()) {
                var toolsNode = root.putArray("tools");
                for (ToolSchema tool : tools) {
                    var toolNode = toolsNode.addObject();
                    toolNode.put("name", tool.getName());
                    toolNode.put("description", tool.getDescription());
                    var inputSchema = toolNode.putObject("input_schema");
                    inputSchema.put("type", "object");
                    var props = inputSchema.putObject("properties");
                    for (var entry : tool.getParameters().entrySet()) {
                        var prop = props.putObject(entry.getKey());
                        prop.put("type", entry.getValue().getType());
                        prop.put("description", entry.getValue().getDescription());
                    }
                    var req = inputSchema.putArray("required");
                    if (tool.getRequired() != null) {
                        tool.getRequired().forEach(req::add);
                    }
                }
            }

            String json = mapper.writeValueAsString(root);
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(baseUrl + "/v1/messages")
                    .post(body)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
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
            JsonNode content = root.path("content");
            if (content.isArray() && content.size() > 0) {
                JsonNode first = content.get(0);
                resp.setContent(first.path("text").asText());
                if ("tool_use".equals(first.path("type").asText())) {
                    var tc = new LlmResponse.ToolCall();
                    tc.setId(first.path("id").asText());
                    tc.setName(first.path("name").asText());
                    tc.setArguments(first.path("input").toString());
                    resp.setToolCalls(List.of(tc));
                }
            }
            return resp;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Anthropic response", e);
        }
    }
}



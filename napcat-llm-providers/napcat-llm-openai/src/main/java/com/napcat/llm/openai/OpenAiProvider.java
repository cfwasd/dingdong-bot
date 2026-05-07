package com.napcat.llm.openai;

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
public class OpenAiProvider implements LlmProvider {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final double temperature;
    private final long timeout;

    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient client;

    public OpenAiProvider(String baseUrl, String apiKey, String model, int maxTokens, double temperature, long timeout) {
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
        return "openai";
    }

    @Override
    public CompletableFuture<LlmResponse> chat(Session session, String input, List<ToolSchema> tools) {
        CompletableFuture<LlmResponse> future = new CompletableFuture<>();

        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("model", model);
            root.put("max_tokens", maxTokens);
            root.put("temperature", temperature);

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
                // 序列化 assistant 的 tool_calls
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

            if (tools != null && !tools.isEmpty()) {
                ArrayNode toolsNode = root.putArray("tools");
                for (ToolSchema tool : tools) {
                    ObjectNode toolNode = toolsNode.addObject();
                    toolNode.put("type", "function");
                    ObjectNode func = toolNode.putObject("function");
                    func.put("name", tool.getName());
                    func.put("description", tool.getDescription());
                    ObjectNode params = func.putObject("parameters");
                    params.put("type", "object");
                    ObjectNode props = params.putObject("properties");
                    for (var entry : tool.getParameters().entrySet()) {
                        ObjectNode prop = props.putObject(entry.getKey());
                        prop.put("type", entry.getValue().getType());
                        prop.put("description", entry.getValue().getDescription());
                        if (entry.getValue().getEnums() != null && !entry.getValue().getEnums().isEmpty()) {
                            ArrayNode enums = prop.putArray("enum");
                            entry.getValue().getEnums().forEach(enums::add);
                        }
                    }
                    ArrayNode req = params.putArray("required");
                    if (tool.getRequired() != null) {
                        tool.getRequired().forEach(req::add);
                    }
                }
            }

            String json = mapper.writeValueAsString(root);
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
            Request.Builder builder = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .post(body);
            if (apiKey != null && !apiKey.isEmpty()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }

            client.newCall(builder.build()).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, java.io.IOException e) {
                    log.error("OpenAI request failed", e);
                    future.completeExceptionally(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws java.io.IOException {
                    try (ResponseBody body = response.body()) {
                        if (body == null) {
                            future.completeExceptionally(new RuntimeException("Empty response"));
                            return;
                        }
                        String respJson = body.string();
                        log.debug("OpenAI response: {}", respJson);
                        LlmResponse llmResp = parseResponse(respJson);
                        future.complete(llmResp);
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
            JsonNode choice = root.path("choices").get(0);
            JsonNode message = choice.path("message");

            LlmResponse response = new LlmResponse();
            response.setContent(message.path("content").asText());

            JsonNode toolCalls = message.path("tool_calls");
            if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                List<LlmResponse.ToolCall> calls = new java.util.ArrayList<>();
                for (JsonNode tc : toolCalls) {
                    LlmResponse.ToolCall call = new LlmResponse.ToolCall();
                    call.setId(tc.path("id").asText());
                    call.setName(tc.path("function").path("name").asText());
                    call.setArguments(tc.path("function").path("arguments").asText());
                    calls.add(call);
                }
                response.setToolCalls(calls);
            }

            return response;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OpenAI response", e);
        }
    }
}

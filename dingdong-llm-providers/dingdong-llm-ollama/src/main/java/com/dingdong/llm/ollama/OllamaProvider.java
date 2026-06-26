package com.dingdong.llm.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.dingdong.agent.llm.ChatMessage;
import com.dingdong.agent.llm.LlmProvider;
import com.dingdong.agent.llm.LlmResponse;
import com.dingdong.agent.session.Session;
import com.dingdong.agent.tool.ToolSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.util.ArrayList;
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
                } else {
                    node.put("content", "");
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

            // 序列化工具定义 — Ollama 0.3+ 支持 function calling
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
            log.debug("Ollama request: {}", json);
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
            JsonNode message = root.path("message");

            // 文本内容
            resp.setContent(message.path("content").asText(null));

            // tool_calls — Ollama 0.3+ 格式
            JsonNode toolCalls = message.path("tool_calls");
            if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                List<LlmResponse.ToolCall> calls = new ArrayList<>();
                for (JsonNode tc : toolCalls) {
                    LlmResponse.ToolCall call = new LlmResponse.ToolCall();
                    call.setId(tc.path("id").asText(null));
                    JsonNode fn = tc.path("function");
                    call.setName(fn.path("name").asText());
                    // arguments 可能是字符串或对象
                    JsonNode args = fn.path("arguments");
                    if (args.isObject()) {
                        call.setArguments(args.toString());
                    } else {
                        call.setArguments(args.asText("{}"));
                    }
                    calls.add(call);
                }
                resp.setToolCalls(calls);
            }

            return resp;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Ollama response", e);
        }
    }
}

package com.napcat.agent.agent;

import com.napcat.agent.llm.ChatMessage;
import com.napcat.agent.llm.LlmProvider;
import com.napcat.agent.llm.LlmResponse;
import com.napcat.agent.session.Session;
import com.napcat.agent.session.SessionKey;
import com.napcat.agent.session.SessionManager;
import com.napcat.agent.tool.ToolRegistry;
import com.napcat.agent.tool.ToolSchema;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
public class NapCatAgent {

    private final LlmProvider llmProvider;
    private final ToolRegistry toolRegistry;
    private final SessionManager sessionManager;
    private final String defaultSystemPrompt;
    private final int defaultMaxRounds;

    public NapCatAgent(LlmProvider llmProvider, ToolRegistry toolRegistry, SessionManager sessionManager,
                       String defaultSystemPrompt, int defaultMaxRounds) {
        this.llmProvider = llmProvider;
        this.toolRegistry = toolRegistry;
        this.sessionManager = sessionManager;
        this.defaultSystemPrompt = defaultSystemPrompt;
        this.defaultMaxRounds = defaultMaxRounds;
    }

    // ========= 便捷方法：仅 userId（私聊场景，保持向后兼容） =========

    /** @deprecated 使用 {@link #chat(long, long, String)} 明确指定 groupId */
    @Deprecated
    public CompletableFuture<String> chat(long userId, String input) {
        return chat(SessionKey.ofPrivate(userId), input,
                AgentConfig.builder()
                        .maxRounds(defaultMaxRounds)
                        .systemPrompt(defaultSystemPrompt)
                        .build(),
                null);
    }

    // ========= 推荐方法：userId + groupId =========

    /**
     * 群聊场景：自动使用 userId + groupId 复合键隔离会话。
     *
     * @param userId  用户 QQ 号
     * @param groupId 群号（私聊时传 0 或使用 {@link SessionKey#PRIVATE}）
     * @param input   用户输入文本
     */
    public CompletableFuture<String> chat(long userId, long groupId, String input) {
        return chat(new SessionKey(userId, groupId), input,
                AgentConfig.builder()
                        .maxRounds(defaultMaxRounds)
                        .systemPrompt(defaultSystemPrompt)
                        .build(),
                null);
    }

    /**
     * 群聊 + 自定义配置 + 工具回调。
     *
     * @param userId             用户 QQ 号
     * @param groupId            群号（私聊时传 0）
     * @param input              用户输入
     * @param config             Agent 配置
     * @param toolProcessConsumer 工具执行过程回调（可为 null）
     */
    public CompletableFuture<String> chat(long userId, long groupId, String input, AgentConfig config,
                                          Consumer<String> toolProcessConsumer) {
        return chat(new SessionKey(userId, groupId), input, config, toolProcessConsumer);
    }

    // ========= 底层方法：SessionKey =========

    /**
     * 以 SessionKey 发起对话。
     */
    public CompletableFuture<String> chat(SessionKey sessionKey, String input, AgentConfig config,
                                          Consumer<String> toolProcessConsumer) {
        Session session = sessionManager.get(sessionKey);

        // 新会话时注入 system prompt，并自动追加可用工具说明
        if (session.getHistory().isEmpty()) {
            String prompt = buildEffectivePrompt(config);
            if (prompt != null && !prompt.isBlank()) {
                session.addMessage(new ChatMessage("system", prompt, null));
            }
        }

        ChatMessage userMsg = new ChatMessage("user", input, null);
        java.util.List<String> imageUrls = extractImageUrls(input);
        if (!imageUrls.isEmpty()) {
            userMsg.setImageUrls(imageUrls);
            // 文本中保留 [图片] 占位，避免 URL 重复干扰模型
            userMsg.setContent(input.replaceAll("\\[图片:[^\\]]+\\]", "[图片]"));
        }
        session.addMessage(userMsg);

        // 消息确认：立即通知用户"已收到，正在处理"
        if (config.getAckCallback() != null) {
            try {
                config.getAckCallback().run();
            } catch (Exception e) {
                log.warn("[Agent] Ack callback failed for {}", sessionKey, e);
            }
        }

        return reactLoop(session, config, 0, toolProcessConsumer);
    }

    /**
     * 构建最终 system prompt = 用户配置的 systemPrompt + 可用工具清单。
     * 工具清单确保即使模型 function-calling 能力弱，也能从文本中了解可用工具。
     */
    /**
     * 从用户输入文本中提取图片 URL（[图片:xxx] 格式）。只保留 http/https 协议的可访问地址。
     */
    private static java.util.List<String> extractImageUrls(String input) {
        if (input == null || input.isBlank()) return java.util.Collections.emptyList();
        java.util.List<String> urls = new java.util.ArrayList<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[图片:([^\\]]+)\\]");
        java.util.regex.Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            String url = matcher.group(1).trim();
            if (url.isEmpty()) continue;
            if (url.startsWith("http://") || url.startsWith("https://")) {
                urls.add(url);
            } else {
                log.warn("[Agent] Ignoring non-HTTP image URL from NapCat: {}", url);
            }
        }
        if (!urls.isEmpty()) {
            log.debug("[Agent] Extracted image URLs: {}", urls);
        }
        return urls;
    }

    private String buildEffectivePrompt(AgentConfig config) {
        StringBuilder sb = new StringBuilder();

        String prompt = config.getSystemPrompt();
        if (prompt == null || prompt.isBlank()) {
            prompt = defaultSystemPrompt;
        }
        if (prompt != null && !prompt.isBlank()) {
            sb.append(prompt);
        }

        List<ToolSchema> tools = toolRegistry.getSchemas();
        if (!tools.isEmpty()) {
            sb.append("\n\n## 可用工具\n");
            for (ToolSchema tool : tools) {
                sb.append("- **").append(tool.getName()).append("**：").append(tool.getDescription());
                if (tool.getRequired() != null && !tool.getRequired().isEmpty()) {
                    sb.append(" 必填参数：").append(String.join("、", tool.getRequired()));
                }
                sb.append("\n");
            }
            sb.append("\n当需要上述工具的能力时，请调用对应函数，不要直接回复\"我无法xxx\"。");
        }

        return sb.toString().trim().isEmpty() ? null : sb.toString().trim();
    }

    private CompletableFuture<String> reactLoop(Session session, AgentConfig config, int round,
                                                 Consumer<String> toolProcessConsumer) {
        if (round >= config.getMaxRounds()) {
            String msg = "思考次数过多，请简化问题。";
            session.addMessage(new ChatMessage("assistant", msg, null));
            return CompletableFuture.completedFuture(msg);
        }

        log.debug("[Agent] Round {}/{} for {}", round + 1, config.getMaxRounds(), session.getKey());

        List<ToolSchema> tools = toolRegistry.getSchemas();

        return llmProvider.chat(session, null, tools)
                .thenCompose(response -> {
                    if (response.hasToolCalls()) {
                        // 记录 assistant tool_calls 到历史（包含 reasoning_content）
                        ChatMessage assistantMsg = ChatMessage.fromToolCalls(response.getToolCalls());
                        if (response.getReasoningContent() != null && !response.getReasoningContent().isEmpty()) {
                            assistantMsg.setReasoningContent(response.getReasoningContent());
                        }
                        session.addMessage(assistantMsg);

                        for (LlmResponse.ToolCall tc : response.getToolCalls()) {
                            log.debug("[Agent] Tool call: {}({})", tc.getName(), tc.getArguments());
                            Object result = toolRegistry.invoke(tc.getName(), tc.getArguments());
                            String resultStr = result == null ? "null" : result.toString();
                            session.addMessage(new ChatMessage("tool", resultStr, tc.getName(), tc.getId()));
                            log.debug("[Agent] Tool result: {} -> {}", tc.getName(), resultStr);

                            // 工具执行过程上报
                            if (config.isShowToolProcess() && toolProcessConsumer != null) {
                                String toolMsg = String.format("🔧 调用工具：%s(%s)\n📊 结果：%s",
                                        tc.getName(), tc.getArguments(), resultStr);
                                toolProcessConsumer.accept(toolMsg);
                            }
                        }

                        return reactLoop(session, config, round + 1, toolProcessConsumer);
                    } else {
                        String content = response.getContent();
                        ChatMessage assistantMsg = new ChatMessage("assistant", content, null);
                        if (response.getReasoningContent() != null && !response.getReasoningContent().isEmpty()) {
                            assistantMsg.setReasoningContent(response.getReasoningContent());
                        }
                        session.addMessage(assistantMsg);
                        return CompletableFuture.completedFuture(content);
                    }
                })
                .exceptionally(ex -> {
                    // 区分客户端错误和服务器错误，客户端错误不返回给QQ
                    String errorMsg = ex.getMessage();
                    if (errorMsg != null && (errorMsg.contains("API请求错误: 4") || errorMsg.contains("invalid_request"))) {
                        log.warn("[Agent] API request error in round {}: {}", round, ex.getMessage());
                        return null; // 不返回消息给用户
                    } else {
                        log.error("[Agent] Error in round {}", round, ex);
                        String fallbackMsg = "处理出错了，请稍后再试。";
                        session.addMessage(new ChatMessage("assistant", fallbackMsg, null));
                        return fallbackMsg;
                    }
                });
    }
}

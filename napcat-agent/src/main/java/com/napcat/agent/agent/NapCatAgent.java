package com.napcat.agent.agent;

import com.napcat.agent.llm.ChatMessage;
import com.napcat.agent.llm.LlmProvider;
import com.napcat.agent.llm.LlmResponse;
import com.napcat.agent.session.Session;
import com.napcat.agent.session.SessionManager;
import com.napcat.agent.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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

    public CompletableFuture<String> chat(long userId, String input) {
        return chat(userId, input, AgentConfig.builder()
                .maxRounds(defaultMaxRounds)
                .systemPrompt(defaultSystemPrompt)
                .build());
    }

    public CompletableFuture<String> chat(long userId, String input, AgentConfig config) {
        Session session = sessionManager.get(userId);
        session.addMessage(new ChatMessage("user", input, null));
        return reactLoop(session, config, 0);
    }

    private CompletableFuture<String> reactLoop(Session session, AgentConfig config, int round) {
        if (round >= config.getMaxRounds()) {
            String msg = "思考次数过多，请简化问题。";
            session.addMessage(new ChatMessage("assistant", msg, null));
            return CompletableFuture.completedFuture(msg);
        }

        log.debug("[Agent] Round {}/{} for user {}", round + 1, config.getMaxRounds(), session.getUserId());

        List<com.napcat.agent.tool.ToolSchema> tools = toolRegistry.getSchemas();

        return llmProvider.chat(session, null, tools)
                .thenCompose(response -> {
                    if (response.hasToolCalls()) {
                        StringBuilder toolResults = new StringBuilder();
                        for (LlmResponse.ToolCall tc : response.getToolCalls()) {
                            log.debug("[Agent] Tool call: {}({})", tc.getName(), tc.getArguments());
                            Object result = toolRegistry.invoke(tc.getName(), tc.getArguments());
                            String resultStr = result == null ? "null" : result.toString();
                            toolResults.append("[").append(tc.getName()).append("] ").append(resultStr).append("\n");
                            session.addMessage(new ChatMessage("tool", resultStr, tc.getName()));
                        }
                        return reactLoop(session, config, round + 1);
                    } else {
                        String content = response.getContent();
                        session.addMessage(new ChatMessage("assistant", content, null));
                        return CompletableFuture.completedFuture(content);
                    }
                })
                .exceptionally(ex -> {
                    log.error("[Agent] Error in round {}", round, ex);
                    String errorMsg = "处理出错了，请稍后再试。";
                    session.addMessage(new ChatMessage("assistant", errorMsg, null));
                    return errorMsg;
                });
    }
}

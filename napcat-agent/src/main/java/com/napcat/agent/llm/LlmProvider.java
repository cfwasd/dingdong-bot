package com.napcat.agent.llm;

import com.napcat.agent.session.Session;
import com.napcat.agent.tool.ToolSchema;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface LlmProvider {
    String getProviderName();
    CompletableFuture<LlmResponse> chat(Session session, String input, List<ToolSchema> tools);
}

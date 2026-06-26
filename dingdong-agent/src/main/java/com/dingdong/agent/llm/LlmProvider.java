package com.dingdong.agent.llm;

import com.dingdong.agent.session.Session;
import com.dingdong.agent.tool.ToolSchema;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface LlmProvider {
    String getProviderName();
    CompletableFuture<LlmResponse> chat(Session session, String input, List<ToolSchema> tools);
}

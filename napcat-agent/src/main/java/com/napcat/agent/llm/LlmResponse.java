package com.napcat.agent.llm;

import lombok.Data;

import java.util.List;

@Data
public class LlmResponse {
    private String content;
    private String reasoningContent;
    private List<ToolCall> toolCalls;

    @Data
    public static class ToolCall {
        private String id;
        private String name;
        private String arguments;
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}

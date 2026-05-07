package com.napcat.agent.llm;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessage {
    /** 角色：system / user / assistant / tool */
    private String role;
    private String content;
    /** tool role 时表示工具名 */
    private String name;
    /** tool role 时关联的工具调用ID */
    private String toolCallId;
    /** assistant role 时的 tool_calls 列表 */
    private List<ToolCallData> toolCalls;

    /** reasoning/thinking 模式下的推理内容 */
    private String reasoningContent;

    public ChatMessage(String role, String content, String name) {
        this.role = role;
        this.content = content;
        this.name = name;
    }
    /**
     * 构造带 tool_call_id 的 tool 响应消息
     */
    public ChatMessage(String role, String content, String name, String toolCallId) {
        this.role = role;
        this.content = content;
        this.name = name;
        this.toolCallId = toolCallId;
    }

    /**
     * 从 LlmResponse.ToolCall 列表构造 assistant 的 tool_calls 消息。
     * 用于写入 session 历史，确保后续 LLM 请求包含完整的工具调用链。
     */
    public static ChatMessage fromToolCalls(List<LlmResponse.ToolCall> calls) {
        ChatMessage msg = new ChatMessage();
        msg.setRole("assistant");
        msg.setContent(null);
        List<ToolCallData> list = new java.util.ArrayList<>();
        for (LlmResponse.ToolCall tc : calls) {
            list.add(buildToolCall(tc.getId(), "function", tc.getName(), tc.getArguments()));
        }
        msg.setToolCalls(list);
        return msg;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallData {
        private String id;
        private String type;
        private FunctionData function;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionData {
        private String name;
        private String arguments;
    }

    /** 构造 ToolCallData 的便捷方法 */
    public static ToolCallData buildToolCall(String id, String type, String name, String arguments) {
        return new ToolCallData(id, type, new FunctionData(name, arguments));
    }
}

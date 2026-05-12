package com.napcat.agent.tool.builtin;

import com.napcat.agent.memory.MemoryStore;
import com.napcat.agent.session.SessionKey;
import com.napcat.agent.tool.ToolRegistry;
import com.napcat.core.annotation.Tool;
import com.napcat.core.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 记忆检索工具。
 * 让 Agent 在对话过程中主动查询用户的历史记忆。
 * 底层通过 MemoryStore.retrieve() 实现：优先检索每日摘要（memory_summaries），
 * 其次检索详细记忆（memories）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "napcat.memory", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MemoryTool {

    @Autowired(required = false)
    private MemoryStore memoryStore;

    @Tool(
        name = "retrieve_memory",
        description = "检索用户的历史记忆。当用户提到过去的事情、询问自己的偏好或历史记录时调用。优先返回每日摘要，其次返回详细记忆。"
    )
    public String retrieveMemory(
        @ToolParam(value = "query", description = "查询关键词或问题，如用户的名字、喜好、过往经历等", required = true) String query,
        @ToolParam(value = "limit", description = "最多返回几条记忆，默认 5 条", required = false) Integer limit
    ) {
        SessionKey key = ToolRegistry.getCurrentSessionKey();
        if (key == null) {
            return "Error: No active session";
        }
        if (memoryStore == null) {
            return "Error: Memory store not available";
        }
        int max = limit != null && limit > 0 ? limit : 5;
        List<String> memories = memoryStore.retrieve(key, query, max);
        if (memories.isEmpty()) {
            return "未找到关于该主题的记忆。";
        }
        return String.join("\n", memories);
    }
}

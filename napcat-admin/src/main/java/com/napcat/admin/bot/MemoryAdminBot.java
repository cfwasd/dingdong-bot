package com.napcat.admin.bot;

import com.napcat.agent.memory.DailyMemorySummarizer;
import com.napcat.agent.memory.MemoryStore;
import com.napcat.agent.memory.MemoryTestDataInjector;
import com.napcat.agent.session.SessionKey;
import com.napcat.core.annotation.Command;
import com.napcat.core.annotation.OnGroupMessage;
import com.napcat.core.annotation.OnPrivateMessage;
import com.napcat.core.annotation.RoleFilter;
import com.napcat.core.event.GroupMessageEvent;
import com.napcat.core.event.PrivateMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 记忆管理测试命令（仅超级用户可用）。
 * 提供手动触发记忆归纳、注入测试数据、清空记忆等调试能力。
 */
@Slf4j
@Component
public class MemoryAdminBot {

    @Autowired(required = false)
    private DailyMemorySummarizer summarizer;

    @Autowired(required = false)
    private MemoryStore memoryStore;

    @Autowired(required = false)
    private MemoryTestDataInjector testInjector;

    /** 手动触发每日记忆归纳 */
    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/summarize", description = "手动触发每日记忆归纳", adminOnly = true)
    @RoleFilter(RoleFilter.Role.SUPERUSER)
    public String summarizeNow() {
        if (summarizer == null) {
            return "记忆归纳组件未启用";
        }
        try {
            summarizer.runDailySummary();
            return "记忆归纳已手动触发，请查看日志";
        } catch (Exception e) {
            log.error("手动归纳失败", e);
            return "归纳失败: " + e.getMessage();
        }
    }

    /** 手动注入测试记忆数据 */
    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/inject-test-memory", description = "注入测试记忆数据", adminOnly = true)
    @RoleFilter(RoleFilter.Role.SUPERUSER)
    public String injectTestMemory() {
        if (testInjector == null) {
            return "测试数据注入器未启用（需设置 napcat.memory.test-data-enabled=true）";
        }
        try {
            testInjector.injectTestData();
            return "测试记忆数据已注入";
        } catch (Exception e) {
            log.error("注入测试数据失败", e);
            return "注入失败: " + e.getMessage();
        }
    }

    /** 清空指定用户的记忆 */
    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/clear-memory {userId} {groupId}", description = "清空指定用户记忆", adminOnly = true)
    @RoleFilter(RoleFilter.Role.SUPERUSER)
    public String clearMemory(com.napcat.core.handler.CommandHandler.CommandArgs args) {
        if (memoryStore == null) {
            return "记忆存储未启用";
        }
        try {
            long userId = args.getLong("userId");
            long groupId = args.getLong("groupId");
            SessionKey key = new SessionKey(userId, groupId);
            memoryStore.clear(key);
            return String.format("已清空用户 %d 在群 %d 的记忆", userId, groupId);
        } catch (Exception e) {
            log.error("清空记忆失败", e);
            return "清空失败: " + e.getMessage();
        }
    }
}

package com.napcat.admin.bot;

import com.napcat.agent.memory.MemoryExtractor;
import com.napcat.agent.memory.MemoryStore;
import com.napcat.agent.session.Session;
import com.napcat.agent.session.SessionKey;
import com.napcat.agent.session.SessionManager;
import com.napcat.core.annotation.Command;
import com.napcat.core.annotation.OnGroupMessage;
import com.napcat.core.annotation.OnPrivateMessage;
import com.napcat.core.event.GroupMessageEvent;
import com.napcat.core.event.PrivateMessageEvent;
import com.napcat.core.exception.StopRoutingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 会话管理指令：/new 重置当前会话上下文。
 * 清除前自动提取长期记忆。
 */
@Component
public class SessionBot {

    @Autowired(required = false)
    private SessionManager sessionManager;

    @Autowired(required = false)
    private MemoryExtractor memoryExtractor;

    @Autowired(required = false)
    private MemoryStore memoryStore;

    /** 群聊中 /new 重置该用户在当前群的会话 */
    @OnGroupMessage
    @Command(value = "/new", description = "重置当前会话上下文")
    public void clearGroupSession(GroupMessageEvent event) {
        if (sessionManager == null) return;
        SessionKey key = new SessionKey(event.getUserId(), event.getGroupId());
        Session session = sessionManager.get(key);
        if (session != null && !session.getHistory().isEmpty()) {
            if (memoryExtractor != null) {
                memoryExtractor.extractAndPersistSync(key, session);
            }
            if (memoryStore != null) {
                memoryStore.persistFullSession(key, formatSessionHistory(session));
            }
        }
        sessionManager.getAndRemove(key);
        event.reply("会话已重置");
        throw new StopRoutingException();
    }

    /** 私聊中 /new 重置该用户的私聊会话 */
    @OnPrivateMessage
    @Command(value = "/new", description = "重置当前会话上下文")
    public void clearPrivateSession(PrivateMessageEvent event) {
        if (sessionManager == null) return;
        SessionKey key = SessionKey.ofPrivate(event.getUserId());
        Session session = sessionManager.get(key);
        if (session != null && !session.getHistory().isEmpty()) {
            if (memoryExtractor != null) {
                memoryExtractor.extractAndPersistSync(key, session);
            }
            if (memoryStore != null) {
                memoryStore.persistFullSession(key, formatSessionHistory(session));
            }
        }
        sessionManager.getAndRemove(key);
        event.reply("会话已重置");
        throw new StopRoutingException();
    }

    private String formatSessionHistory(Session session) {
        StringBuilder sb = new StringBuilder();
        for (var msg : session.getHistory()) {
            if ("user".equals(msg.getRole()) || "assistant".equals(msg.getRole())) {
                sb.append("[").append(msg.getRole()).append("]: ")
                        .append(msg.getContent() != null ? msg.getContent() : "").append("\n");
            }
        }
        return sb.toString().trim();
    }
}

package com.dingdong.admin.bot;

import com.dingdong.channel.api.ChannelEvent;
import com.dingdong.channel.api.ChannelMessageEvent;
import com.dingdong.agent.memory.MemoryExtractor;
import com.dingdong.agent.memory.MemoryStore;
import com.dingdong.agent.session.Session;
import com.dingdong.agent.session.SessionKey;
import com.dingdong.agent.session.SessionManager;
import com.dingdong.core.annotation.Command;
import com.dingdong.core.annotation.OnGroupMessage;
import com.dingdong.core.annotation.OnPrivateMessage;
import com.dingdong.core.exception.StopRoutingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SessionBot {

    @Autowired(required = false)
    private SessionManager sessionManager;

    @Autowired(required = false)
    private MemoryExtractor memoryExtractor;

    @Autowired(required = false)
    private MemoryStore memoryStore;

    @OnGroupMessage
    @Command(value = "/new", description = "重置当前会话上下文")
    public String clearGroupSession(ChannelEvent event) {
        if (sessionManager == null) return "会话管理未启用";
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        SessionKey key = new SessionKey(userId, groupId);
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
        throw new StopRoutingException();
    }

    @OnPrivateMessage
    @Command(value = "/new", description = "重置当前会话上下文")
    public String clearPrivateSession(ChannelEvent event) {
        if (sessionManager == null) return "会话管理未启用";
        long userId = resolveUserId(event);
        SessionKey key = SessionKey.ofPrivate(userId);
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
        throw new StopRoutingException();
    }

    private long resolveUserId(ChannelEvent event) {
        if (event instanceof com.dingdong.core.event.MessageEvent me) return me.getUserId();
        if (event instanceof ChannelMessageEvent chMsg) return chMsg.getUserId();
        return 0;
    }

    private long resolveGroupId(ChannelEvent event) {
        if (event instanceof com.dingdong.core.event.GroupMessageEvent ge) return ge.getGroupId();
        if (event instanceof ChannelMessageEvent chMsg) return chMsg.getGroupId();
        return 0;
    }

    private String formatSessionHistory(Session session) {
        return session.getFormattedHistory();
    }
}

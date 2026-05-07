package com.napcat.agent.session;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SessionManager {

    private final Map<Long, Session> sessions = new ConcurrentHashMap<>();
    private final long ttlSeconds;

    public SessionManager() {
        this(3600);
    }

    public SessionManager(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * 获取或创建用户会话。原子操作，消除 check-then-act 竞态条件。
     */
    public Session get(long userId) {
        Session existing = sessions.get(userId);
        if (existing != null && !existing.isExpired(ttlSeconds)) {
            return existing;
        }

        // 过期或不存在 → 原子创建新会话
        Session newSession = new Session(userId);
        Session old = sessions.put(userId, newSession);
        if (old != null && !old.isExpired(ttlSeconds)) {
            // 另一个线程抢先创建了有效会话，恢复它
            sessions.put(userId, old);
            return old;
        }
        if (old != null) {
            log.debug("Session expired and replaced for user: {}", userId);
        }
        return newSession;
    }

    public void clear(long userId) {
        sessions.remove(userId);
    }

    public void clearExpired() {
        sessions.entrySet().removeIf(e -> e.getValue().isExpired(ttlSeconds));
    }
}

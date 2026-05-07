package com.napcat.agent.session;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SessionManager {

    private final Map<SessionKey, Session> sessions = new ConcurrentHashMap<>();
    private final long ttlSeconds;
    private final int maxHistoryMessages;

    public SessionManager() {
        this(3600, 0);
    }

    public SessionManager(long ttlSeconds) {
        this(ttlSeconds, 0);
    }

    public SessionManager(long ttlSeconds, int maxHistoryMessages) {
        this.ttlSeconds = ttlSeconds;
        this.maxHistoryMessages = maxHistoryMessages;
    }

    /**
     * 获取或创建会话。原子操作，消除 check-then-act 竞态条件。
     */
    public Session get(SessionKey key) {
        Session existing = sessions.get(key);
        if (existing != null && !existing.isExpired(ttlSeconds)) {
            return existing;
        }

        // 过期或不存在 → 原子创建新会话
        Session newSession = new Session(key, maxHistoryMessages);
        Session old = sessions.put(key, newSession);
        if (old != null && !old.isExpired(ttlSeconds)) {
            // 另一个线程抢先创建了有效会话，恢复它
            sessions.put(key, old);
            return old;
        }
        if (old != null) {
            log.debug("Session expired and replaced for key: {}", key);
        }
        return newSession;
    }

    /**
     * @deprecated 使用 {@link #get(SessionKey)} 代替
     */
    @Deprecated
    public Session get(long userId) {
        return get(SessionKey.ofPrivate(userId));
    }

    /**
     * 清除指定会话。
     */
    public void clear(SessionKey key) {
        sessions.remove(key);
        log.debug("Session cleared: {}", key);
    }

    /**
     * @deprecated 使用 {@link #clear(SessionKey)} 代替
     */
    @Deprecated
    public void clear(long userId) {
        clear(SessionKey.ofPrivate(userId));
    }

    /**
     * 清除所有过期会话。
     */
    public void clearExpired() {
        sessions.entrySet().removeIf(e -> e.getValue().isExpired(ttlSeconds));
    }
}

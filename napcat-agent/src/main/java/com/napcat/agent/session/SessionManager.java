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

    public Session get(long userId) {
        Session session = sessions.get(userId);
        if (session == null || session.isExpired(ttlSeconds)) {
            if (session != null) {
                log.debug("Session expired for user: {}", userId);
            }
            session = new Session(userId);
            sessions.put(userId, session);
        }
        return session;
    }

    public void clear(long userId) {
        sessions.remove(userId);
    }

    public void clearExpired() {
        sessions.entrySet().removeIf(e -> e.getValue().isExpired(ttlSeconds));
    }
}

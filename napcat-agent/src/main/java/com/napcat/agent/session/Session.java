package com.napcat.agent.session;

import com.napcat.agent.llm.ChatMessage;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Session {
    private final long userId;
    private final List<ChatMessage> history = new ArrayList<>();
    private final long createdAt = System.currentTimeMillis();
    private volatile long lastAccessedAt = System.currentTimeMillis();

    public void addMessage(ChatMessage message) {
        history.add(message);
        lastAccessedAt = System.currentTimeMillis();
    }

    public boolean isExpired(long ttlSeconds) {
        return (System.currentTimeMillis() - lastAccessedAt) > ttlSeconds * 1000;
    }

    public void clear() {
        history.clear();
        lastAccessedAt = System.currentTimeMillis();
    }
}

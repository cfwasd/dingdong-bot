package com.napcat.agent.session;

/**
 * 会话复合键：userId + 场景标识（私聊或具体群号）。
 * <p>
 * groupId == {@link #PRIVATE} (0) 表示私聊，groupId > 0 表示对应群号。
 * 同一用户在不同群聊、或私聊与群聊之间的会话完全隔离。
 */
public record SessionKey(long userId, long groupId) {

    /** 私聊场景的 groupId 值 */
    public static final long PRIVATE = 0L;

    /** 是否为私聊会话 */
    public boolean isPrivate() {
        return groupId == PRIVATE;
    }

    /** 是否为群聊会话 */
    public boolean isGroup() {
        return groupId > PRIVATE;
    }

    /** 私聊会话键 */
    public static SessionKey ofPrivate(long userId) {
        return new SessionKey(userId, PRIVATE);
    }

    /** 群聊会话键 */
    public static SessionKey ofGroup(long userId, long groupId) {
        return new SessionKey(userId, groupId);
    }
}

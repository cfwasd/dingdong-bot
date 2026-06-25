package com.dingdong.channel.api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 消息目标抽象。
 * 表示一条消息的发送目标（私聊 / 群聊）。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChannelMessageTarget {
    /** 发送者用户身份 */
    private ChannelIdentity user;
    /** 群身份（私聊时为 null） */
    private ChannelIdentity group;
    /** 目标类型: "private" | "group" */
    private String targetType;

    public boolean isGroup() {
        return "group".equals(targetType);
    }

    public boolean isPrivate() {
        return "private".equals(targetType);
    }
}

package com.dingdong.channel.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

/**
 * 渠道消息事件。
 * 所有消息类事件的基类。
 */
@Getter
@Setter
public class ChannelMessageEvent extends ChannelEvent {
    /** 消息 ID */
    private long messageId;
    /** 纯文本内容 */
    private String plainText;
    /** 发送者信息 */
    @JsonIgnore
    private ChannelMessageSender sender;
    /** 消息目标 */
    private ChannelMessageTarget messageTarget;
    /** 统一的回复 API */
    @JsonIgnore
    private MessageSender api;

    /** 获取用户映射 ID */
    @JsonIgnore
    public long getUserId() {
        return messageTarget != null && messageTarget.getUser() != null
                ? messageTarget.getUser().getMappedId() : 0;
    }

    /** 获取群映射 ID（私聊返回 0） */
    @JsonIgnore
    public long getGroupId() {
        return messageTarget != null && messageTarget.getGroup() != null
                ? messageTarget.getGroup().getMappedId() : 0;
    }
}

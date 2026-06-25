package com.dingdong.channel.api;

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
    private ChannelMessageSender sender;
    /** 消息目标 */
    private ChannelMessageTarget messageTarget;
    /** 统一的回复 API */
    private MessageSender api;
}

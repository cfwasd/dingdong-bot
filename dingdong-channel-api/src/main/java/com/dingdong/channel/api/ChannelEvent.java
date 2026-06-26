package com.dingdong.channel.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 渠道事件基类。
 * 所有渠道（OneBot11 / QQ官方）的事件都继承此类。
 */
@Getter
@Setter
public abstract class ChannelEvent {
    /** 渠道标识: "onebot" | "qqofficial" */
    private String channelId;
    /** 事件时间戳（秒） */
    private long timestamp;
    /** 事件类型（如 "message", "notice", "request", "meta_event"） */
    @JsonProperty("post_type")
    private String postType;
    /** 标记此事件是否已被命令处理器处理（不序列化） */
    private transient boolean commandHandled;
}

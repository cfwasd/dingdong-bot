package com.dingdong.qqofficial;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * QQ 官方通道配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "dingdong.qq-official")
public class QqOfficialProperties {
    private boolean enabled = false;
    private String appId;
    private String appSecret;
    private boolean sandbox = false;
    private String triggerMode = "private-all-group-mention-or-wake";
    private List<String> wakeWords;
    private String commandPrefix = "/";
    /** WebSocket 订阅事件 intents，默认 1<<25 (群聊+私聊事件) */
    private int intents = 1 << 25;
}

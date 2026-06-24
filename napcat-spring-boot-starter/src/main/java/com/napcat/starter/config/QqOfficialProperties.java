package com.napcat.starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * QQ 官方 Bot 配置，对应 napcat.qq-official.*。
 */
@Data
@ConfigurationProperties(prefix = "napcat.qq-official")
public class QqOfficialProperties {

    /** 是否启用 */
    private boolean enabled = false;

    /** QQ 开放平台 AppID */
    private String appId = "";

    /** QQ 开放平台 AppSecret */
    private String appSecret = "";

    /** 沙箱模式 */
    private boolean sandbox = true;

    /** WebSocket 网关地址（为空时自动获取） */
    private String gatewayUrl = "";

    /** 重连间隔（毫秒） */
    private long reconnectInterval = 5000;

    /** HTTP 请求超时（毫秒） */
    private long apiTimeout = 30000;

    /** 命令前缀 */
    private String commandPrefix = "";

    /** 触发模式：all / wake-word / mention-or-wake / private-all-group-mention-or-wake */
    private String triggerMode = "all";

    /** 唤醒词列表 */
    private List<String> wakeWords = new ArrayList<>(List.of("小助手", "bot"));
}

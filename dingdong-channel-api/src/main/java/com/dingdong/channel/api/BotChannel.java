package com.dingdong.channel.api;

import java.util.function.Consumer;

/**
 * 通道适配器接口。
 * 每个渠道（OneBot11 / QQ官方）实现此接口接入统一事件管道。
 */
public interface BotChannel {

    /** 渠道标识: "onebot" | "qqofficial" */
    String getChannelId();

    /** 启动通道（建立连接 / 启动 WebHook） */
    void start();

    /** 停止通道 */
    void stop();

    /** 通道是否已连接 */
    boolean isConnected();

    /** 设置事件消费者 */
    void setEventConsumer(Consumer<ChannelEvent> consumer);

    /** 获取消息发送器 */
    MessageSender getMessageSender();
}

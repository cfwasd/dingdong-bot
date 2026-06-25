package com.dingdong.channel.api;

import java.util.concurrent.CompletableFuture;

/**
 * 消息发送接口。
 * 渠道无关，Agent 和命令处理器通过此接口发送消息。
 */
public interface MessageSender {

    /** 回复当前消息上下文 */
    CompletableFuture<MessageResult> reply(String text);

    /** 发送到指定目标 */
    CompletableFuture<MessageResult> sendTo(ChannelMessageTarget target, String text);

    /** 发送私聊消息 */
    CompletableFuture<MessageResult> sendPrivateMessage(ChannelIdentity user, String text);

    /** 发送群消息 */
    CompletableFuture<MessageResult> sendGroupMessage(ChannelIdentity group, String text);

    /** 是否支持发送图片 */
    default boolean canSendImage() { return true; }

    /** 是否支持发送文件 */
    default boolean canSendFile() { return false; }

    /** 通道是否可用 */
    boolean isAvailable();
}

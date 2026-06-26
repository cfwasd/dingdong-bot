package com.dingdong.core;

import com.dingdong.channel.api.ChannelIdentity;
import com.dingdong.channel.api.ChannelMessageTarget;
import com.dingdong.channel.api.MessageResult;
import com.dingdong.channel.api.MessageSender;
import com.dingdong.core.api.NapCatApi;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

/**
 * OneBot11 通道的 MessageSender 实现。
 */
@Slf4j
public class OneBotMessageSender implements MessageSender {

    private final NapCatApi api;

    public OneBotMessageSender(NapCatApi api) { this.api = api; }

    @Override
    public CompletableFuture<MessageResult> reply(String text) {
        return CompletableFuture.completedFuture(MessageResult.fail("reply() requires context"));
    }

    @Override
    public CompletableFuture<MessageResult> sendTo(ChannelMessageTarget target, String text) {
        if (target.isGroup()) return sendGroupMessage(target.getGroup(), text);
        return sendPrivateMessage(target.getUser(), text);
    }

    @Override
    public CompletableFuture<MessageResult> sendPrivateMessage(ChannelIdentity user, String text) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                api.sendPrivateMessage(user.getMappedId(), text);
                return MessageResult.ok("");
            } catch (Exception e) {
                log.warn("Failed to send private message", e);
                return MessageResult.fail(e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<MessageResult> sendGroupMessage(ChannelIdentity group, String text) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                api.sendGroupMessage(group.getMappedId(), text);
                return MessageResult.ok("");
            } catch (Exception e) {
                log.warn("Failed to send group message", e);
                return MessageResult.fail(e.getMessage());
            }
        });
    }

    @Override
    public boolean isAvailable() { return api.isAvailable(); }
}

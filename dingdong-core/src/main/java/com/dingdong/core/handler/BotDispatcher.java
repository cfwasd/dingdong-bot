package com.dingdong.core.handler;

import com.dingdong.channel.api.ChannelEvent;
import com.dingdong.core.event.GroupMessageEvent;
import com.dingdong.core.event.PrivateMessageEvent;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public interface BotDispatcher {
    void onGroupMessage(Consumer<GroupMessageEvent> handler);
    void onPrivateMessage(Consumer<PrivateMessageEvent> handler);
    void onEvent(Class<? extends ChannelEvent> type, Consumer<ChannelEvent> handler);
    void registerCommand(String template, BiConsumer<ChannelEvent, CommandHandler.CommandArgs> handler);
    void registerCommand(String template, BiConsumer<ChannelEvent, CommandHandler.CommandArgs> handler, Predicate<ChannelEvent> filter);
}

package com.napcat.core.handler;

import com.dingdong.channel.api.ChannelEvent;
import com.napcat.core.event.GroupMessageEvent;
import com.napcat.core.event.MessageEvent;
import com.napcat.core.event.PrivateMessageEvent;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public interface BotDispatcher {
    void onGroupMessage(Consumer<GroupMessageEvent> handler);
    void onPrivateMessage(Consumer<PrivateMessageEvent> handler);
    void onEvent(Class<? extends ChannelEvent> type, Consumer<ChannelEvent> handler);
    void registerCommand(String template, BiConsumer<MessageEvent, CommandHandler.CommandArgs> handler);
    void registerCommand(String template, BiConsumer<MessageEvent, CommandHandler.CommandArgs> handler, Predicate<MessageEvent> filter);
}

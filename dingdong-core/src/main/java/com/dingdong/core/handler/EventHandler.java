package com.dingdong.core.handler;

import com.dingdong.channel.api.ChannelEvent;
import com.dingdong.core.event.GroupMessageEvent;
import com.dingdong.core.event.PrivateMessageEvent;

public interface EventHandler<E extends ChannelEvent> {
    Class<E> getEventType();
    void handle(E event);

    interface GroupMessageHandler extends EventHandler<GroupMessageEvent> {
        @Override
        default Class<GroupMessageEvent> getEventType() {
            return GroupMessageEvent.class;
        }
    }

    interface PrivateMessageHandler extends EventHandler<PrivateMessageEvent> {
        @Override
        default Class<PrivateMessageEvent> getEventType() {
            return PrivateMessageEvent.class;
        }
    }
}

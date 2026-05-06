package com.napcat.core.handler;

import com.napcat.core.event.GroupMessageEvent;
import com.napcat.core.event.OB11Event;
import com.napcat.core.event.PrivateMessageEvent;

public interface EventHandler<E extends OB11Event> {
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

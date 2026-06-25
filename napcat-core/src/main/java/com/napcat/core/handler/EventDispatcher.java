package com.napcat.core.handler;

import com.dingdong.channel.api.ChannelEvent;
import com.napcat.core.api.NapCatApi;
import com.napcat.core.config.BotProperties;
import com.napcat.core.event.OB11Event;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
public class EventDispatcher {

    private final HandlerRegistry registry;
    private final BotProperties properties;
    private final NapCatApi api;
    private final Executor executor;
    private final boolean sync;

    public EventDispatcher(HandlerRegistry registry, BotProperties properties, NapCatApi api) {
        this(registry, properties, api, false);
    }

    public EventDispatcher(HandlerRegistry registry, BotProperties properties, NapCatApi api, boolean sync) {
        this(registry, properties, api, sync, Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "napcat-event-worker");
            t.setDaemon(true);
            return t;
        }));
    }

    public EventDispatcher(HandlerRegistry registry, BotProperties properties, NapCatApi api, boolean sync, Executor executor) {
        this.registry = registry;
        this.properties = properties;
        this.api = api;
        this.sync = sync;
        this.executor = executor;
    }

    public void dispatch(ChannelEvent event) {
        if (event == null) return;

        if (properties.isIgnoreSelfMessage()
                && event instanceof com.napcat.core.event.MessageEvent msgEvent
                && msgEvent.getUserId() == properties.getSelfId()) {
            log.info("Ignoring self message, userId={}", msgEvent.getUserId());
            return;
        }

        log.info("Dispatching event: type={}, selfId={}, class={}",
                event.getPostType(), event instanceof OB11Event ob11 ? ob11.getSelfId() : 0, event.getClass().getSimpleName());

        com.napcat.core.context.EventContext ctx = com.napcat.core.context.EventContextHolder.get();

        if (sync) {
            try {
                registry.dispatch(event);
            } catch (Exception e) {
                log.error("Event dispatch error", e);
            }
        } else {
            executor.execute(() -> {
                if (ctx != null) {
                    com.napcat.core.context.EventContextHolder.set(ctx);
                }
                try {
                    registry.dispatch(event);
                } catch (Exception e) {
                    log.error("Event dispatch error", e);
                } finally {
                    com.napcat.core.context.EventContextHolder.clear();
                }
            });
        }
    }
}

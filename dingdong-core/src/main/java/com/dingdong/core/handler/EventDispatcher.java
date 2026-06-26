package com.dingdong.core.handler;

import com.dingdong.channel.api.ChannelEvent;
import com.dingdong.core.api.NapCatApi;
import com.dingdong.core.config.BotProperties;
import com.dingdong.core.event.OB11Event;
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
            Thread t = new Thread(r, "dingdong-event-worker");
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
                && event instanceof com.dingdong.core.event.MessageEvent msgEvent
                && msgEvent.getUserId() == properties.getSelfId()) {
            log.info("Ignoring self message, userId={}", msgEvent.getUserId());
            return;
        }

        log.debug("Dispatching event: type={}, selfId={}, class={}",
                event.getPostType(), event instanceof OB11Event ob11 ? ob11.getSelfId() : 0, event.getClass().getSimpleName());

        com.dingdong.core.context.EventContext ctx = com.dingdong.core.context.EventContextHolder.get();

        // 非 OneBot 渠道（如 QQ Official）同步分发，确保 commandHandled 标记在调用方立即可见
        boolean isSync = sync || !"onebot".equals(event.getChannelId());

        if (isSync) {
            try {
                registry.dispatch(event);
            } catch (Exception e) {
                log.error("Event dispatch error", e);
            }
        } else {
            executor.execute(() -> {
                if (ctx != null) {
                    com.dingdong.core.context.EventContextHolder.set(ctx);
                }
                try {
                    registry.dispatch(event);
                } catch (Exception e) {
                    log.error("Event dispatch error", e);
                } finally {
                    com.dingdong.core.context.EventContextHolder.clear();
                }
            });
        }
    }
}

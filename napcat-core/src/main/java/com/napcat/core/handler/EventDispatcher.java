package com.napcat.core.handler;

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

    public void dispatch(OB11Event event) {
        if (event == null) return;

        if (properties.isIgnoreSelfMessage() && event.getSelfId() == properties.getSelfId()) {
            return;
        }

        if (sync) {
            try {
                registry.dispatch(event);
            } catch (Exception e) {
                log.error("Event dispatch error", e);
            }
        } else {
            executor.execute(() -> {
                try {
                    registry.dispatch(event);
                } catch (Exception e) {
                    log.error("Event dispatch error", e);
                }
            });
        }
    }
}

package com.napcat.starter.config;

import com.napcat.core.adapter.BotAdapter;
import com.napcat.core.api.NapCatApi;
import com.napcat.core.event.OB11Event;
import com.napcat.core.handler.EventDispatcher;
import com.napcat.core.handler.HandlerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class NapCatLifecycle implements SmartLifecycle {

    private final List<BotAdapter> adapters;
    private final EventDispatcher dispatcher;
    private final NapCatApi api;
    private final HandlerRegistry registry;
    private final ApplicationContext ctx;
    private volatile boolean running = false;

    @Override
    public void start() {
        for (BotAdapter adapter : adapters) {
            adapter.setEventConsumer(this::onEvent);
            adapter.start();
            log.info("NapCat adapter started: {}", adapter.getId());
        }
        running = true;
    }

    private void onEvent(OB11Event event) {
        com.napcat.core.context.EventContextHolder.set(
                new com.napcat.core.context.EventContext(event, api)
        );
        try {
            dispatcher.dispatch(event);
        } finally {
            com.napcat.core.context.EventContextHolder.clear();
        }
    }

    @Override
    public void stop() {
        for (BotAdapter adapter : adapters) {
            adapter.stop();
        }
        running = false;
        log.info("NapCat adapter stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }
}

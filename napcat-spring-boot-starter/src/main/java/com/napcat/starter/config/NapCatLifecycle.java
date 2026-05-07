package com.napcat.starter.config;

import com.napcat.core.adapter.BotAdapter;
import com.napcat.core.adapter.MessageRouter;
import com.napcat.core.api.NapCatApi;
import com.napcat.core.context.EventContext;
import com.napcat.core.context.EventContextHolder;
import com.napcat.core.event.OB11Event;
import com.napcat.core.handler.EventDispatcher;
import com.napcat.core.handler.HandlerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;

import java.util.List;

/**
 * Spring 生命周期管理：在所有 Bean 初始化完成后启动适配器，
 * 绑定 MessageRouter → EventDispatcher 的事件管道。
 */
@Slf4j
public class NapCatLifecycle implements SmartLifecycle {

    private final List<BotAdapter> adapters;
    private final EventDispatcher dispatcher;
    private final NapCatApi api;
    private final HandlerRegistry registry;
    private final MessageRouter messageRouter;
    private final ApplicationContext ctx;
    private volatile boolean running = false;

    public NapCatLifecycle(List<BotAdapter> adapters, EventDispatcher dispatcher,
                           NapCatApi api, HandlerRegistry registry,
                           MessageRouter messageRouter, ApplicationContext ctx) {
        this.adapters = adapters;
        this.dispatcher = dispatcher;
        this.api = api;
        this.registry = registry;
        this.messageRouter = messageRouter;
        this.ctx = ctx;
    }

    @Override
    public void start() {
        // 事件管道：MessageRouter → EventDispatcher
        messageRouter.setEventConsumer(this::onEvent);

        // 所有适配器共享同一个 MessageRouter
        for (BotAdapter adapter : adapters) {
            adapter.setMessageHandler(messageRouter);
            adapter.start();
            log.info("NapCat adapter started: {}", adapter.getId());
        }
        running = true;
    }

    private void onEvent(OB11Event event) {
        // 注入 API 到事件对象，确保异步回调（Agent 等）跨线程 reply 可用
        if (event instanceof com.napcat.core.event.MessageEvent me) {
            me.setApi(api);
        }
        EventContextHolder.set(new EventContext(event, api));
        try {
            dispatcher.dispatch(event);
        } finally {
            EventContextHolder.clear();
        }
    }

    @Override
    public void stop() {
        for (BotAdapter adapter : adapters) {
            adapter.stop();
        }
        running = false;
        log.info("NapCat adapters stopped");
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

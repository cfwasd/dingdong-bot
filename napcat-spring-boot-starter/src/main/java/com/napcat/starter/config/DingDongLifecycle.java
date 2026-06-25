package com.napcat.starter.config;

import com.dingdong.channel.api.BotChannel;
import com.napcat.core.handler.EventDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.util.List;

/**
 * 叮咚生命周期管理器。
 * 启动/停止所有 BotChannel。
 */
@Slf4j
public class DingDongLifecycle implements SmartLifecycle {

    private final List<BotChannel> channels;
    private final EventDispatcher eventDispatcher;
    private volatile boolean running;

    public DingDongLifecycle(List<BotChannel> channels, EventDispatcher eventDispatcher) {
        this.channels = channels;
        this.eventDispatcher = eventDispatcher;
    }

    @Override
    public void start() {
        log.info("DingDong Lifecycle: starting {} channels", channels.size());

        for (BotChannel channel : channels) {
            try {
                // 绑定事件管道到 EventDispatcher
                channel.setEventConsumer(eventDispatcher::dispatch);
                channel.start();
                log.info("Channel started: {} (connected: {})", channel.getChannelId(), channel.isConnected());
            } catch (Exception e) {
                log.error("Failed to start channel: {}", channel.getChannelId(), e);
            }
        }

        running = true;
        log.info("DingDong Lifecycle started");
    }

    @Override
    public void stop() {
        log.info("DingDong Lifecycle: stopping channels");
        for (BotChannel channel : channels) {
            try {
                channel.stop();
                log.info("Channel stopped: {}", channel.getChannelId());
            } catch (Exception e) {
                log.warn("Failed to stop channel: {}", channel.getChannelId(), e);
            }
        }
        running = false;
    }

    @Override
    public boolean isRunning() { return running; }

    @Override
    public int getPhase() {
        // 在 NapCatLifecycle 之后启动
        return Integer.MAX_VALUE;
    }
}

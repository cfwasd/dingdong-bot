package com.dingdong.boot.starter.config;

import com.dingdong.channel.api.BotChannel;
import com.dingdong.core.handler.EventDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.util.ArrayList;
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
        log.info("══════════ DingDong 启动中 ({}) ══════════", channels.size());

        List<String> started = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (BotChannel channel : channels) {
            try {
                channel.setEventConsumer(eventDispatcher::dispatch);
                channel.start();
                started.add(channel.getChannelId());
                log.info("  ✅ {} 通道启动成功", channel.getChannelId());
            } catch (Exception e) {
                failed.add(channel.getChannelId());
                log.error("  ❌ {} 通道启动失败: {}", channel.getChannelId(), e.getMessage());
            }
        }

        running = true;
        if (started.isEmpty()) {
            log.warn("⚠️ 没有成功启动任何通道！");
        } else {
            log.info("══════════ 启动完成: {} 个通道就绪 ══════════", started.size());
        }
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

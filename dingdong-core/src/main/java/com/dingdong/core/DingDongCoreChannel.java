package com.dingdong.core;

import com.dingdong.channel.api.BotChannel;
import com.dingdong.channel.api.ChannelEvent;
import com.dingdong.channel.api.MessageSender;
import com.dingdong.core.adapter.BotAdapter;
import com.dingdong.core.api.NapCatApi;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

/**
 * OneBot11 通道的 BotChannel 适配器。
 */
@Slf4j
public class DingDongCoreChannel implements BotChannel {

    private final BotAdapter adapter;
    private final NapCatApi api;
    private volatile Consumer<ChannelEvent> eventConsumer;

    public DingDongCoreChannel(BotAdapter adapter) {
        this.adapter = adapter;
        this.api = new NapCatApi(adapter);
        this.adapter.setMessageHandler(rawJson -> {});
    }

    @Override public String getChannelId() { return "onebot"; }
    @Override public void start() { adapter.start(); }
    @Override public void stop() { adapter.stop(); }
    @Override public boolean isConnected() { return adapter.isConnected(); }
    @Override public void setEventConsumer(Consumer<ChannelEvent> consumer) { this.eventConsumer = consumer; }

    @Override
    public MessageSender getMessageSender() {
        return new OneBotMessageSender(api);
    }

    public BotAdapter getAdapter() { return adapter; }
    public NapCatApi getApi() { return api; }
}

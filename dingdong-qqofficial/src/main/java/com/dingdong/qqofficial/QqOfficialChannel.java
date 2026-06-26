package com.dingdong.qqofficial;

import com.dingdong.channel.api.BotChannel;
import com.dingdong.channel.api.ChannelEvent;
import com.dingdong.channel.api.MessageSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.function.Consumer;

/**
 * QQ 官方通道实现。
 */
@Slf4j
public class QqOfficialChannel implements BotChannel {

    private final QqOfficialProperties properties;
    private final QqOfficialTokenManager tokenManager;
    private final QqOfficialApi api;
    private final QqOfficialIdMapper idMapper;
    private final ObjectMapper objectMapper;
    private volatile Consumer<ChannelEvent> eventConsumer;
    private volatile boolean running;
    private QqOfficialWsClient wsClient;
    private QqOfficialDispatcher.AgentInvoker agentInvoker;

    public QqOfficialChannel(QqOfficialProperties properties) {
        this.properties = properties;
        this.tokenManager = new QqOfficialTokenManager(properties.getAppId(), properties.getAppSecret());
        this.api = new QqOfficialApi(properties.getAppId(), tokenManager, properties.isSandbox());
        this.idMapper = new QqOfficialIdMapper();
        this.objectMapper = new ObjectMapper();
    }

    @Override public String getChannelId() { return "qqofficial"; }

    public void setAgentInvoker(QqOfficialDispatcher.AgentInvoker agentInvoker) {
        this.agentInvoker = agentInvoker;
    }

    @Override
    public void start() {
        log.info("Starting QQ Official channel...");
        tokenManager.start();

        // 等待 token 就绪
        int retries = 0;
        while (!tokenManager.isTokenValid() && retries < 30) {
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            retries++;
        }
        if (!tokenManager.isTokenValid()) {
            log.error("QQ Official token not available after 30s, aborting start");
            return;
        }

        try {
            String gatewayUrl = api.getGatewayUrl();
            log.info("QQ Official gateway URL: {}", gatewayUrl);

            Consumer<ChannelEvent> consumer = eventConsumer;
            QqOfficialDispatcher dispatcher = new QqOfficialDispatcher(this, consumer, agentInvoker);

            wsClient = new QqOfficialWsClient(new URI(gatewayUrl), tokenManager.getToken(),
                    properties.getIntents(), dispatcher);
            wsClient.connect();
            running = true;
            log.info("QQ Official channel started (WS connecting...)");
        } catch (Exception e) {
            log.error("Failed to start QQ Official WS client", e);
        }
    }

    @Override
    public void stop() {
        running = false;
        if (wsClient != null) {
            try { wsClient.closeBlocking(); } catch (Exception e) { log.warn("WS close error", e); }
        }
        tokenManager.stop();
        log.info("QQ Official channel stopped");
    }

    @Override public boolean isConnected() { return running && wsClient != null && wsClient.isOpen(); }

    @Override public void setEventConsumer(Consumer<ChannelEvent> consumer) { this.eventConsumer = consumer; }

    @Override
    public MessageSender getMessageSender() {
        return new QqOfficialMessageSender(api, idMapper, properties);
    }

    public QqOfficialApi getApi() { return api; }
    public QqOfficialIdMapper getIdMapper() { return idMapper; }
    public QqOfficialTokenManager getTokenManager() { return tokenManager; }
    public QqOfficialProperties getProperties() { return properties; }
}

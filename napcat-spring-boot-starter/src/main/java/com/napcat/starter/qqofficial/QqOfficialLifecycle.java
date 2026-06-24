package com.napcat.starter.qqofficial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.napcat.agent.agent.NapCatAgent;
import com.napcat.core.handler.HandlerRegistry;
import com.napcat.starter.config.QqOfficialProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.io.IOException;
import java.time.Duration;

/**
 * QQ 官方 Bot 生命周期管理。负责初始化 WebSocket 连接和启停。
 */
@Slf4j
public class QqOfficialLifecycle implements SmartLifecycle {

    private final QqOfficialProperties props;
    private final ObjectMapper mapper;
    private final NapCatAgent agent;
    private final HandlerRegistry handlerRegistry;

    private volatile boolean running;
    private QqOfficialApi api;
    private QqOfficialWsClient wsClient;

    public QqOfficialLifecycle(QqOfficialProperties props, ObjectMapper mapper,
                               NapCatAgent agent, HandlerRegistry handlerRegistry) {
        this.props = props;
        this.mapper = mapper;
        this.agent = agent;
        this.handlerRegistry = handlerRegistry;
    }

    @Override
    public void start() {
        if (running) return;

        api = new QqOfficialApi(props.getAppId(), props.getAppSecret(),
                props.isSandbox(), Duration.ofMillis(props.getApiTimeout()), mapper);

        QqOfficialIdMapper idMapper = new QqOfficialIdMapper();

        QqOfficialDispatcher dispatcher = new QqOfficialDispatcher(
                idMapper, props, handlerRegistry,
                (userId, groupId, prompt) -> agent.chat(userId, groupId, prompt),
                api);

        String gatewayUrl = props.getGatewayUrl();
        if (gatewayUrl == null || gatewayUrl.isBlank()) {
            try {
                gatewayUrl = api.getGatewayUrl();
                log.info("Got QQ official gateway URL: {}", gatewayUrl);
            } catch (IOException e) {
                log.error("Failed to get QQ official gateway URL", e);
                return;
            }
        }

        String token;
        try {
            token = api.getAccessToken();
        } catch (IOException e) {
            log.error("Failed to get QQ official access token", e);
            return;
        }

        wsClient = new QqOfficialWsClient(gatewayUrl, token, props.getReconnectInterval(),
                mapper, dispatcher);
        wsClient.start();
        running = true;
        log.info("QQ Official bot started");
    }

    @Override
    public void stop() {
        running = false;
        if (wsClient != null) {
            wsClient.stop();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}

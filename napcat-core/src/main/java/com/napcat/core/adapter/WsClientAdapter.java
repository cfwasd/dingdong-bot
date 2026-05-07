package com.napcat.core.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.napcat.core.api.ApiRequest;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * WebSocket Client 适配器：主动连接 NapCat 的 WebSocket Server。
 * 双工通信，推荐模式。
 */
@Slf4j
public class WsClientAdapter implements BotAdapter {

    private final String url;
    private final String token;
    private final long reconnectInterval;
    private final ObjectMapper mapper;

    private WebSocketClient client;
    private Consumer<String> messageHandler;
    private volatile boolean running = false;
    private final AtomicInteger reconnectAttempt = new AtomicInteger(0);
    private ScheduledExecutorService reconnectScheduler;

    public WsClientAdapter(String url, String token) {
        this(url, token, 5000, new ObjectMapper());
    }

    public WsClientAdapter(String url, String token, long reconnectInterval, ObjectMapper mapper) {
        this.url = url;
        this.token = token;
        this.reconnectInterval = reconnectInterval;
        this.mapper = mapper;
    }

    @Override
    public String getId() {
        return "ws-client-" + url;
    }

    @Override
    public void start() {
        running = true;
        reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "napcat-ws-reconnect");
            t.setDaemon(true);
            return t;
        });
        connect();
    }

    private void connect() {
        if (!running) return;
        try {
            client = new WebSocketClient(new URI(url)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.info("[{}] WebSocket connected", getId());
                    reconnectAttempt.set(0);
                }

                @Override
                public void onMessage(String message) {
                    if (messageHandler != null) {
                        messageHandler.accept(message);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.warn("[{}] WebSocket closed: code={}, reason={}, remote={}", getId(), code, reason, remote);
                    scheduleReconnect();
                }

                @Override
                public void onError(Exception ex) {
                    log.error("[{}] WebSocket error", getId(), ex);
                }
            };
            if (token != null && !token.isEmpty()) {
                client.addHeader("Authorization", "Bearer " + token);
            }
            client.connect();
        } catch (Exception e) {
            log.error("[{}] Failed to connect", getId(), e);
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (!running) return;
        int attempt = reconnectAttempt.incrementAndGet();
        long delay = Math.min(reconnectInterval * attempt, 60000);
        log.info("[{}] Reconnecting in {}ms (attempt {})", getId(), delay, attempt);
        if (reconnectScheduler != null && !reconnectScheduler.isShutdown()) {
            reconnectScheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void stop() {
        running = false;
        if (reconnectScheduler != null) {
            reconnectScheduler.shutdown();
        }
        if (client != null) {
            client.close();
        }
    }

    @Override
    public boolean isConnected() {
        return client != null && client.isOpen();
    }

    @Override
    public void sendApiRequest(ApiRequest<?> request) {
        try {
            String json = mapper.writeValueAsString(request);
            log.debug("[{}] Sending API request: action={}, echo={}", getId(), request.getAction(), request.getEcho());
            if (client != null && client.isOpen()) {
                client.send(json);
            } else {
                log.warn("[{}] WebSocket not connected, cannot send request action={}", getId(), request.getAction());
            }
        } catch (Exception e) {
            log.error("[{}] Failed to send request action={}", getId(), request.getAction(), e);
        }
    }

    @Override
    public void setMessageHandler(Consumer<String> handler) {
        this.messageHandler = handler;
    }
}

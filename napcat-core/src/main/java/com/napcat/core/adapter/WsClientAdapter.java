package com.napcat.core.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.napcat.core.api.ApiRequest;
import com.napcat.core.api.ApiResponse;
import com.napcat.core.event.OB11Event;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Slf4j
public class WsClientAdapter implements BotAdapter {

    private final String url;
    private final String token;
    private final long reconnectInterval;
    private final long heartInterval;
    private final ObjectMapper mapper;

    private WebSocketClient client;
    private Consumer<OB11Event> eventConsumer;
    private Consumer<ApiResponse> responseConsumer;
    private volatile boolean running = false;
    private final AtomicInteger reconnectAttempt = new AtomicInteger(0);
    private ScheduledExecutorService heartbeatExecutor;

    public WsClientAdapter(String url, String token) {
        this(url, token, 5000, 30000, new ObjectMapper());
    }

    public WsClientAdapter(String url, String token, long reconnectInterval, long heartInterval, ObjectMapper mapper) {
        this.url = url;
        this.token = token;
        this.reconnectInterval = reconnectInterval;
        this.heartInterval = heartInterval;
        this.mapper = mapper;
    }

    @Override
    public String getId() {
        return "ws-client-" + url;
    }

    @Override
    public void start() {
        running = true;
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
                    if (heartInterval > 0) {
                        startHeartbeat();
                    }
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.warn("[{}] WebSocket closed: code={}, reason={}", getId(), code, reason);
                    stopHeartbeat();
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

    private void handleMessage(String message) {
        try {
            // 先尝试解析为 API 响应
            ApiResponse response = mapper.readValue(message, ApiResponse.class);
            if (response.getEcho() != null && responseConsumer != null) {
                responseConsumer.accept(response);
                return;
            }
            // 否则解析为事件
            if (eventConsumer != null) {
                com.napcat.core.event.EventDecoder decoder = new com.napcat.core.event.EventDecoder(mapper);
                OB11Event event = decoder.decode(message);
                if (event != null) {
                    eventConsumer.accept(event);
                }
            }
        } catch (Exception e) {
            log.error("[{}] Failed to handle message: {}", getId(), message, e);
        }
    }

    private void startHeartbeat() {
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ws-heartbeat-" + getId());
            t.setDaemon(true);
            return t;
        });
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (client != null && client.isOpen()) {
                client.send("{\"meta_event_type\":\"heartbeat\"}");
            }
        }, heartInterval, heartInterval, TimeUnit.MILLISECONDS);
    }

    private void stopHeartbeat() {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor = null;
        }
    }

    private void scheduleReconnect() {
        if (!running) return;
        int attempt = reconnectAttempt.incrementAndGet();
        long delay = Math.min(reconnectInterval * attempt, 60000);
        log.info("[{}] Reconnecting in {}ms (attempt {})", getId(), delay, attempt);
        Executors.newSingleThreadScheduledExecutor().schedule(this::connect, delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        running = false;
        stopHeartbeat();
        if (client != null) {
            client.close();
        }
    }

    @Override
    public boolean isConnected() {
        return client != null && client.isOpen();
    }

    @Override
    public void sendApiRequest(ApiRequest<?> request, Consumer<ApiResponse> callback) {
        this.responseConsumer = callback;
        try {
            String json = mapper.writeValueAsString(request);
            if (client != null && client.isOpen()) {
                client.send(json);
            } else {
                log.warn("[{}] WebSocket not connected, cannot send request", getId());
            }
        } catch (Exception e) {
            log.error("[{}] Failed to send request", getId(), e);
        }
    }

    @Override
    public void setEventConsumer(Consumer<OB11Event> consumer) {
        this.eventConsumer = consumer;
    }
}

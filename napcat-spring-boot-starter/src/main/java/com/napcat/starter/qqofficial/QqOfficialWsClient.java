package com.napcat.starter.qqofficial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * QQ 官方 Bot WebSocket 客户端。
 * 连接 QQ Gateway，接收事件推送。
 *
 * 协议流程：连接 → Op10 Hello → Op2 Identify → READY → Op1 心跳/Op11 ACK → Op0 Dispatch 事件
 */
@Slf4j
public class QqOfficialWsClient {

    // Gateway OpCode
    private static final int OP_DISPATCH = 0;
    private static final int OP_HEARTBEAT = 1;
    private static final int OP_IDENTIFY = 2;
    private static final int OP_RESUME = 6;
    private static final int OP_RECONNECT = 7;
    private static final int OP_INVALID_SESSION = 9;
    private static final int OP_HELLO = 10;
    private static final int OP_HEARTBEAT_ACK = 11;

    // Intents
    private static final int INTENT_GROUP_AND_C2C = 1 << 25;

    private final String gatewayUrl;
    private final String accessToken;
    private final long reconnectIntervalMs;
    private final ObjectMapper mapper;
    private final Consumer<JsonNode> eventConsumer;

    private WebSocketClient client;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> heartbeatTask;
    private volatile boolean running;
    private volatile String sessionId;
    private volatile long lastSeq;
    private final AtomicInteger reconnectAttempt = new AtomicInteger(0);

    public QqOfficialWsClient(String gatewayUrl, String accessToken, long reconnectIntervalMs,
                              ObjectMapper mapper, Consumer<JsonNode> eventConsumer) {
        this.gatewayUrl = gatewayUrl;
        this.accessToken = accessToken;
        this.reconnectIntervalMs = reconnectIntervalMs;
        this.mapper = mapper;
        this.eventConsumer = eventConsumer;
    }

    public void start() {
        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "qq-official-ws");
            t.setDaemon(true);
            return t;
        });
        connect();
    }

    public void stop() {
        running = false;
        cancelHeartbeat();
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (client != null) {
            client.close();
        }
    }

    private void connect() {
        if (!running) return;
        try {
            client = new WebSocketClient(new URI(gatewayUrl)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.info("QQ official WebSocket connected");
                }

                @Override
                public void onMessage(String raw) {
                    log.info("QQ official WS raw: {}", raw);
                    handleMessage(raw);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.warn("QQ official WebSocket closed: code={}, reason={}, remote={}", code, reason, remote);
                    cancelHeartbeat();
                    scheduleReconnect();
                }

                @Override
                public void onError(Exception ex) {
                    log.error("QQ official WebSocket error", ex);
                }
            };
            client.connect();
        } catch (Exception e) {
            log.error("Failed to create QQ official WebSocket connection", e);
            scheduleReconnect();
        }
    }

    private void handleMessage(String raw) {
        try {
            JsonNode root = mapper.readTree(raw);
            int op = root.path("op").asInt(-1);

            switch (op) {
                case OP_HELLO -> handleHello(root);
                case OP_DISPATCH -> handleDispatch(root);
                case OP_HEARTBEAT_ACK -> log.debug("Heartbeat ACK");
                case OP_RECONNECT -> {
                    log.info("Server requested reconnect");
                    reconnect();
                }
                case OP_INVALID_SESSION -> {
                    log.warn("Invalid session, will re-identify");
                    sessionId = null;
                    reconnect();
                }
                default -> log.debug("Unknown op={}: {}", op, raw.length() > 200 ? raw.substring(0, 200) : raw);
            }
        } catch (Exception e) {
            log.error("Failed to handle QQ official WebSocket message", e);
        }
    }

    private void handleHello(JsonNode root) {
        int heartbeatInterval = root.path("d").path("heartbeat_interval").asInt(30000);
        log.info("Hello received, heartbeat_interval={}ms", heartbeatInterval);
        sendIdentify();
        startHeartbeat(heartbeatInterval);
    }

    private void handleDispatch(JsonNode root) {
        long seq = root.path("s").asLong(0);
        if (seq > 0) lastSeq = seq;

        String eventType = root.path("t").asText("");
        JsonNode data = root.path("d");

        log.debug("Dispatch: t={}, s={}", eventType, seq);

        if ("READY".equals(eventType)) {
            sessionId = data.path("session_id").asText("");
            log.info("READY, session_id={}", sessionId);
            reconnectAttempt.set(0);
            return;
        }

        if (eventConsumer != null && data != null && !data.isNull()) {
            try {
                // 将事件类型注入 data，方便 Dispatcher 直接使用
                ((ObjectNode) data).put("__event_type", eventType);
                eventConsumer.accept(data);
            } catch (Exception e) {
                log.error("Event consumer error for event {}", eventType, e);
            }
        }
    }

    private void sendIdentify() {
        try {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("token", "QQBot " + accessToken);
            d.put("intents", INTENT_GROUP_AND_C2C);
            d.put("shard", new int[]{0, 1});

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("op", OP_IDENTIFY);
            payload.put("d", d);

            send(mapper.writeValueAsString(payload));
            log.info("Identify sent");
        } catch (Exception e) {
            log.error("Failed to send Identify", e);
        }
    }

    private void sendHeartbeat() {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("op", OP_HEARTBEAT);
            payload.put("d", lastSeq);
            send(mapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.error("Failed to send heartbeat", e);
        }
    }

    private void startHeartbeat(int intervalMs) {
        cancelHeartbeat();
        long actualInterval = Math.max(intervalMs, 10000);
        heartbeatTask = scheduler.scheduleAtFixedRate(
                this::sendHeartbeat, actualInterval, actualInterval, TimeUnit.MILLISECONDS);
    }

    private void cancelHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
    }

    private void send(String text) {
        if (client != null && client.isOpen()) {
            client.send(text);
        }
    }

    private void reconnect() {
        if (client != null) {
            try { client.close(); } catch (Exception ignored) {}
        }
        cancelHeartbeat();
        if (running) {
            connect();
        }
    }

    private void scheduleReconnect() {
        if (!running) return;
        int attempt = reconnectAttempt.incrementAndGet();
        long delay = Math.min(reconnectIntervalMs * attempt, 60000);
        log.info("Scheduling reconnect in {}ms (attempt {})", delay, attempt);
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
        }
    }
}

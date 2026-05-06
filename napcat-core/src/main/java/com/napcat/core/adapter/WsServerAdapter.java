package com.napcat.core.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.napcat.core.api.ApiRequest;
import com.napcat.core.api.ApiResponse;
import com.napcat.core.event.EventDecoder;
import com.napcat.core.event.OB11Event;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
public class WsServerAdapter implements BotAdapter {

    private final String host;
    private final int port;
    private final String token;
    private final ObjectMapper mapper;
    private final EventDecoder decoder;

    private WsServer server;
    private Consumer<OB11Event> eventConsumer;
    private Consumer<ApiResponse> responseConsumer;
    private final Map<WebSocket, Boolean> clients = new ConcurrentHashMap<>();

    public WsServerAdapter(String host, int port, String token) {
        this(host, port, token, new ObjectMapper());
    }

    public WsServerAdapter(String host, int port, String token, ObjectMapper mapper) {
        this.host = host;
        this.port = port;
        this.token = token;
        this.mapper = mapper;
        this.decoder = new EventDecoder(mapper);
    }

    @Override
    public String getId() {
        return "ws-server-" + host + ":" + port;
    }

    @Override
    public void start() {
        server = new WsServer(new InetSocketAddress(host, port));
        server.start();
        log.info("[{}] WebSocket server started on {}:{}", getId(), host, port);
    }

    @Override
    public void stop() {
        try {
            if (server != null) {
                server.stop();
            }
        } catch (Exception e) {
            log.error("[{}] Failed to stop server", getId(), e);
        }
    }

    @Override
    public boolean isConnected() {
        return server != null && !clients.isEmpty();
    }

    @Override
    public void sendApiRequest(ApiRequest<?> request, Consumer<ApiResponse> callback) {
        this.responseConsumer = callback;
        try {
            String json = mapper.writeValueAsString(request);
            for (WebSocket client : clients.keySet()) {
                if (client.isOpen()) {
                    client.send(json);
                }
            }
        } catch (Exception e) {
            log.error("[{}] Failed to send request", getId(), e);
        }
    }

    @Override
    public void setEventConsumer(Consumer<OB11Event> consumer) {
        this.eventConsumer = consumer;
    }

    private class WsServer extends WebSocketServer {

        public WsServer(InetSocketAddress address) {
            super(address);
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            if (token != null && !token.isEmpty()) {
                String auth = handshake.getFieldValue("Authorization");
                if (!auth.equals("Bearer " + token)) {
                    log.warn("[{}] Unauthorized connection from {}", getId(), conn.getRemoteSocketAddress());
                    conn.close(1008, "Unauthorized");
                    return;
                }
            }
            clients.put(conn, true);
            log.info("[{}] Client connected: {}", getId(), conn.getRemoteSocketAddress());
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            clients.remove(conn);
            log.info("[{}] Client disconnected: {}", getId(), conn.getRemoteSocketAddress());
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            handleMessage(message);
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            log.error("[{}] WebSocket error", getId(), ex);
        }

        @Override
        public void onStart() {
            log.info("[{}] Server started", getId());
        }
    }

    private void handleMessage(String message) {
        try {
            ApiResponse response = mapper.readValue(message, ApiResponse.class);
            if (response.getEcho() != null && responseConsumer != null) {
                responseConsumer.accept(response);
                return;
            }
            if (eventConsumer != null) {
                OB11Event event = decoder.decode(message);
                if (event != null) {
                    eventConsumer.accept(event);
                }
            }
        } catch (Exception e) {
            log.error("[{}] Failed to handle message: {}", getId(), message, e);
        }
    }
}

package com.napcat.core.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.napcat.core.api.ApiRequest;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * WebSocket Server 适配器：开启 WS Server，等待 NapCat 主动连接。
 * 适合中心化 Bot 服务，多个 NapCat 实例连接同一个 Bot 后端。
 */
@Slf4j
public class WsServerAdapter implements BotAdapter {

    private final String host;
    private final int port;
    private final String token;
    private final ObjectMapper mapper;

    private WsServer server;
    private Consumer<String> messageHandler;
    private final Map<WebSocket, Boolean> clients = new ConcurrentHashMap<>();

    public WsServerAdapter(String host, int port, String token) {
        this(host, port, token, new ObjectMapper());
    }

    public WsServerAdapter(String host, int port, String token, ObjectMapper mapper) {
        this.host = host;
        this.port = port;
        this.token = token;
        this.mapper = mapper;
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
    public void sendApiRequest(ApiRequest<?> request) {
        try {
            String json = mapper.writeValueAsString(request);
            log.debug("[{}] Sending API request: action={}, echo={}", getId(), request.getAction(), request.getEcho());
            for (WebSocket client : clients.keySet()) {
                if (client.isOpen()) {
                    client.send(json);
                }
            }
        } catch (Exception e) {
            log.error("[{}] Failed to send request action={}", getId(), request.getAction(), e);
        }
    }

    @Override
    public void setMessageHandler(Consumer<String> handler) {
        this.messageHandler = handler;
    }

    private class WsServer extends WebSocketServer {

        public WsServer(InetSocketAddress address) {
            super(address);
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            if (token != null && !token.isEmpty()) {
                String auth = handshake.getFieldValue("Authorization");
                if (auth == null || !auth.equals("Bearer " + token)) {
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
            log.info("[{}] Client disconnected: code={}, reason={}", getId(), code, reason);
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            if (messageHandler != null) {
                messageHandler.accept(message);
            }
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            log.error("[{}] WebSocket error from {}", getId(), conn != null ? conn.getRemoteSocketAddress() : "null", ex);
        }

        @Override
        public void onStart() {
            log.info("[{}] Server started on {}", getId(), getAddress());
        }
    }
}

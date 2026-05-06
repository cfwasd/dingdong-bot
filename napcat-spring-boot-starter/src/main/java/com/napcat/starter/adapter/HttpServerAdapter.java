package com.napcat.starter.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.napcat.core.adapter.BotAdapter;
import com.napcat.core.api.ApiRequest;
import com.napcat.core.api.ApiResponse;
import com.napcat.core.event.EventDecoder;
import com.napcat.core.event.OB11Event;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

@Slf4j
@RestController
@ConditionalOnProperty(prefix = "napcat.adapter", name = "type", havingValue = "http-server")
public class HttpServerAdapter implements BotAdapter {

    private final String path;
    private final String token;
    private final ObjectMapper mapper;
    private final EventDecoder decoder;

    private Consumer<OB11Event> eventConsumer;
    private Consumer<ApiResponse> responseConsumer;
    private final LinkedBlockingQueue<ApiRequest<?>> pendingRequests = new LinkedBlockingQueue<>();

    public HttpServerAdapter(String path, String token, ObjectMapper mapper) {
        this.path = path;
        this.token = token;
        this.mapper = mapper;
        this.decoder = new EventDecoder(mapper);
    }

    @Override
    public String getId() {
        return "http-server-" + path;
    }

    @Override
    public void start() {
        log.info("[{}] HTTP server adapter started, listening on POST {}", getId(), path);
    }

    @Override
    public void stop() {
        log.info("[{}] HTTP server adapter stopped", getId());
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public void sendApiRequest(ApiRequest<?> request, Consumer<ApiResponse> callback) {
        this.responseConsumer = callback;
        pendingRequests.offer(request);
        log.warn("[{}] HTTP server adapter cannot actively send API requests. Consider using HTTP client adapter alongside.", getId());
    }

    @Override
    public void setEventConsumer(Consumer<OB11Event> consumer) {
        this.eventConsumer = consumer;
    }

    @PostMapping("${napcat.adapter.http-server.path:/napcat/webhook}")
    public ResponseEntity<String> onWebhook(HttpServletRequest request, @RequestBody String body) {
        try {
            if (token != null && !token.isEmpty()) {
                String auth = request.getHeader("Authorization");
                if (auth == null || !auth.equals("Bearer " + token)) {
                    return ResponseEntity.status(401).body("Unauthorized");
                }
            }

            String postType = extractPostType(body);
            if (postType != null) {
                // 事件上报
                OB11Event event = decoder.decode(body);
                if (event != null && eventConsumer != null) {
                    eventConsumer.accept(event);
                }
            } else {
                // API 响应
                ApiResponse response = mapper.readValue(body, ApiResponse.class);
                if (response.getEcho() != null && responseConsumer != null) {
                    responseConsumer.accept(response);
                }
            }
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            log.error("[{}] Failed to handle webhook", getId(), e);
            return ResponseEntity.status(500).body("error");
        }
    }

    private String extractPostType(String json) {
        try {
            return mapper.readTree(json).path("post_type").asText(null);
        } catch (IOException e) {
            return null;
        }
    }
}

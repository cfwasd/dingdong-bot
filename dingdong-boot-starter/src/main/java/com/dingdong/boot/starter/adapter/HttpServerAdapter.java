package com.dingdong.boot.starter.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dingdong.core.adapter.BotAdapter;
import com.dingdong.core.api.ApiRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * HTTP Server 适配器：接收 NapCat 的 HTTP 上报（Webhook 模式）。
 * 
 * 同时内置反向 HTTP Client，接收上报时可主动调用 NapCat API（如 reply）。
 * 
 * NapCat 配置示例：
 * <pre>
 * "httpClients": [{"enable": true, "url": "http://bot-server:8080/dingdong/webhook"}]
 * </pre>
 */
@Slf4j
@RestController
@ConditionalOnProperty(prefix = "dingdong.adapter", name = "type", havingValue = "http-server")
public class HttpServerAdapter implements BotAdapter {

    private final String path;
    private final String token;
    private final ObjectMapper mapper;

    /** 反向 HTTP Client，用于主动调用 NapCat API */
    private final String napcatApiUrl;
    private final String napcatApiToken;
    private final long napcatApiTimeout;
    private final OkHttpClient httpClient;

    private Consumer<String> messageHandler;

    public HttpServerAdapter(
            ObjectMapper mapper,
            String path, String token,
            String napcatApiUrl, String napcatApiToken, long napcatApiTimeout) {
        this.mapper = mapper;
        this.path = path;
        this.token = token;
        this.napcatApiUrl = napcatApiUrl != null && !napcatApiUrl.isEmpty() ? napcatApiUrl : null;
        this.napcatApiToken = napcatApiToken;
        this.napcatApiTimeout = napcatApiTimeout > 0 ? napcatApiTimeout : 30000;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(this.napcatApiTimeout, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(this.napcatApiTimeout, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public String getId() {
        return "http-server-" + path;
    }

    @Override
    public void start() {
        log.info("[{}] HTTP server adapter started, path={}, napcatApiUrl={}",
                getId(), path, napcatApiUrl != null ? napcatApiUrl : "none (reply will fail)");
    }

    @Override
    public void stop() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        log.info("[{}] HTTP server adapter stopped", getId());
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public void sendApiRequest(ApiRequest<?> request) {
        if (napcatApiUrl == null) {
            log.error("[{}] Cannot send API request: no napcatApiUrl configured. " +
                    "HTTP server adapter needs a separate dingdong.http-client.url for active API calls. " +
                    "Action: {}, echo: {}", getId(), request.getAction(), request.getEcho());
            if (messageHandler != null) {
                String errorJson = String.format(
                        "{\"status\":\"failed\",\"retcode\":-1,\"echo\":\"%s\",\"message\":\"No API URL configured\"}",
                        request.getEcho());
                messageHandler.accept(errorJson);
            }
            return;
        }
        try {
            String json = mapper.writeValueAsString(request);
            log.debug("[{}] Sending API request via reverse HTTP: action={}, echo={}",
                    getId(), request.getAction(), request.getEcho());

            okhttp3.RequestBody body = okhttp3.RequestBody.create(json, MediaType.parse("application/json"));
            Request.Builder builder = new Request.Builder()
                    .url(napcatApiUrl)
                    .post(body);
            if (napcatApiToken != null && !napcatApiToken.isEmpty()) {
                builder.header("Authorization", "Bearer " + napcatApiToken);
            }

            httpClient.newCall(builder.build()).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    log.error("[{}] Reverse HTTP request failed: action={}", getId(), request.getAction(), e);
                    if (messageHandler != null) {
                        String errorJson = String.format(
                                "{\"status\":\"failed\",\"retcode\":-1,\"echo\":\"%s\",\"message\":\"%s\"}",
                                request.getEcho(), e.getMessage());
                        messageHandler.accept(errorJson);
                    }
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (ResponseBody responseBody = response.body()) {
                        if (responseBody != null) {
                            String respJson = responseBody.string();
                            if (messageHandler != null) {
                                messageHandler.accept(respJson);
                            }
                        }
                    }
                }
            });
        } catch (Exception e) {
            log.error("[{}] Failed to send API request action={}", getId(), request.getAction(), e);
        }
    }

    @Override
    public void setMessageHandler(Consumer<String> handler) {
        this.messageHandler = handler;
    }

    @PostMapping("${dingdong.adapter.http-server.path:/dingdong/webhook}")
    public ResponseEntity<String> onWebhook(HttpServletRequest request, @RequestBody String body) {
        try {
            if (token != null && !token.isEmpty()) {
                String auth = request.getHeader("Authorization");
                if (auth == null || !auth.equals("Bearer " + token)) {
                    return ResponseEntity.status(401).body("Unauthorized");
                }
            }

            log.debug("[{}] Received webhook: {}", getId(),
                    body.length() > 200 ? body.substring(0, 200) + "..." : body);

            if (messageHandler != null) {
                messageHandler.accept(body);
            }

            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            log.error("[{}] Failed to handle webhook", getId(), e);
            return ResponseEntity.status(500).body("error");
        }
    }
}

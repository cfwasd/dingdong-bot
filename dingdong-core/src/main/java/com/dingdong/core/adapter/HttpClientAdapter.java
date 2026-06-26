package com.dingdong.core.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dingdong.core.api.ApiRequest;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * HTTP Client 适配器：主动发送 HTTP 请求调用 NapCat API。
 * 
 * OneBot11 标准：POST /{action}，请求体为 params JSON。
 * NapCat 同时兼容通用端点格式：POST 任意路径，请求体为 {"action":"...","params":{...}}。
 * 本实现使用通用格式发送到 baseUrl。
 * 
 * 注意：纯 HTTP Client 模式下无法被动接收事件，需配合 HTTP Server 接收上报。
 */
@Slf4j
public class HttpClientAdapter implements BotAdapter {

    private final String baseUrl;
    private final String token;
    private final long timeout;
    private final ObjectMapper mapper;
    private final OkHttpClient client;

    private Consumer<String> messageHandler;

    public HttpClientAdapter(String baseUrl, String token) {
        this(baseUrl, token, 30000, new ObjectMapper());
    }

    public HttpClientAdapter(String baseUrl, String token, long timeout, ObjectMapper mapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token;
        this.timeout = timeout;
        this.mapper = mapper;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public String getId() {
        return "http-client-" + baseUrl;
    }

    @Override
    public void start() {
        log.info("[{}] HTTP client adapter started", getId());
    }

    @Override
    public void stop() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public void sendApiRequest(ApiRequest<?> request) {
        try {
            String json = mapper.writeValueAsString(request);
            log.debug("[{}] Sending API request: action={}, echo={}", getId(), request.getAction(), request.getEcho());

            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
            Request.Builder builder = new Request.Builder()
                    .url(baseUrl)
                    .post(body);
            if (token != null && !token.isEmpty()) {
                builder.header("Authorization", "Bearer " + token);
            }

            client.newCall(builder.build()).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    log.error("[{}] HTTP request failed: action={}", getId(), request.getAction(), e);
                    // 构造错误响应送入 messageHandler，让 NapCatApi 完成超时/错误处理
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
                            log.debug("[{}] HTTP response: {}", getId(),
                                    respJson.length() > 200 ? respJson.substring(0, 200) + "..." : respJson);
                            if (messageHandler != null) {
                                messageHandler.accept(respJson);
                            }
                        }
                    }
                }
            });
        } catch (Exception e) {
            log.error("[{}] Failed to send request action={}", getId(), request.getAction(), e);
        }
    }

    @Override
    public void setMessageHandler(Consumer<String> handler) {
        this.messageHandler = handler;
    }
}

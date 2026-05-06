package com.napcat.core.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.napcat.core.api.ApiRequest;
import com.napcat.core.api.ApiResponse;
import com.napcat.core.event.OB11Event;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.function.Consumer;

@Slf4j
public class HttpClientAdapter implements BotAdapter {

    private final String baseUrl;
    private final String token;
    private final long timeout;
    private final ObjectMapper mapper;
    private final OkHttpClient client;

    private Consumer<OB11Event> eventConsumer;
    private Consumer<ApiResponse> responseConsumer;

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
    public void sendApiRequest(ApiRequest<?> request, Consumer<ApiResponse> callback) {
        try {
            String json = mapper.writeValueAsString(request);
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
                    log.error("[{}] HTTP request failed", getId(), e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (ResponseBody body = response.body()) {
                        if (body != null) {
                            String respJson = body.string();
                            ApiResponse resp = mapper.readValue(respJson, ApiResponse.class);
                            callback.accept(resp);
                        }
                    }
                }
            });
        } catch (Exception e) {
            log.error("[{}] Failed to send request", getId(), e);
        }
    }

    @Override
    public void setEventConsumer(Consumer<OB11Event> consumer) {
        this.eventConsumer = consumer;
    }
}

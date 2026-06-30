package com.dingdong.qqofficial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * QQ 官方 Token 管理器。
 * 自动获取和刷新 access_token。
 * QQ 官方仅在 token 过期前最后 60 秒内返回新 token，
 * 因此刷新时机设为过期前 50 秒（留 10 秒网络缓冲）。
 */
@Slf4j
public class QqOfficialTokenManager {

    private static final String TOKEN_URL = "https://bots.qq.com/app/getAppAccessToken";
    /** 刷新提前量：50 秒（官方窗口为最后 60 秒，留 10 秒缓冲） */
    private static final long REFRESH_MARGIN_MS = 50 * 1000L;
    /**
     * 当 Token 剩余有效期已不足一次刷新余量时，多久（毫秒）后立即刷新。
     * 避免"剩余有效期 < clamp 最小延迟"导致 Token 过期后仍拖到下次计划刷新。
     */
    private static final long IMMEDIATE_REFRESH_DELAY_MS = 1000;

    private final String appId;
    private final String appSecret;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile String accessToken;
    private volatile long expiresAtMs;
    private volatile ScheduledExecutorService scheduler;
    private Runnable onRefreshedCallback;

    public QqOfficialTokenManager(String appId, String appSecret) {
        this(appId, appSecret, new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build());
    }

    public QqOfficialTokenManager(String appId, String appSecret, OkHttpClient httpClient) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    public void setOnRefreshedCallback(Runnable callback) {
        this.onRefreshedCallback = callback;
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "qq-official-token-refresher");
            t.setDaemon(true);
            return t;
        });
        forceRefresh()
            .thenRun(this::scheduleNextRefresh)
            .exceptionally(ex -> {
                log.warn("Initial token refresh failed, retrying in 60s: {}", ex.getMessage());
                scheduler.schedule(this::scheduleNextRefresh, 60, TimeUnit.SECONDS);
                return null;
            });
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public String getToken() {
        return accessToken;
    }

    public boolean isTokenValid() {
        return accessToken != null && System.currentTimeMillis() < expiresAtMs - REFRESH_MARGIN_MS;
    }

    public CompletableFuture<String> forceRefresh() {
        return CompletableFuture.supplyAsync(() -> {
            lock.lock();
            try {
                // QQ 官方机器人 Token API：POST + JSON body (appId + clientSecret)
                String reqBody = objectMapper.writeValueAsString(
                        java.util.Map.of("appId", appId, "clientSecret", appSecret));
                Request request = new Request.Builder()
                        .url(TOKEN_URL)
                        .post(RequestBody.create(reqBody, MediaType.get("application/json")))
                        .build();
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        throw new RuntimeException("Token request failed: " + response.code());
                    }
                    String respBody = response.body().string();
                    JsonNode json = objectMapper.readTree(respBody);
                    if (json.has("code") && json.get("code").asInt() != 0) {
                        throw new RuntimeException("Token error: " + json.path("message").asText());
                    }
                    this.accessToken = json.get("access_token").asText();
                    int expiresIn = json.get("expires_in").asInt(7200);
                    this.expiresAtMs = System.currentTimeMillis() + expiresIn * 1000L;
                    log.info("QQ official token refreshed, expires in {}s", expiresIn);
                    if (onRefreshedCallback != null) {
                        try { onRefreshedCallback.run(); } catch (Exception e) {
                            log.warn("Token refreshed callback failed", e);
                        }
                    }
                    return this.accessToken;
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to refresh token: " + e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        });
    }

    private void scheduleNextRefresh() {
        long remainingMs = expiresAtMs - System.currentTimeMillis();
        long delay;
        if (remainingMs <= REFRESH_MARGIN_MS) {
            // Token 已进入（或低于）官方刷新窗口：尽快刷新，避免Token已过期仍在等计划刷新
            // （例如重启后 API 返回旧 Token，剩余有效期可能 < 50 秒）
            delay = IMMEDIATE_REFRESH_DELAY_MS;
            log.warn("Token remaining {}ms <= margin {}ms, refreshing in {}ms", remainingMs, REFRESH_MARGIN_MS, delay);
        } else {
            // 正常情况：在过期前 REFRESH_MARGIN_MS 时触发刷新
            delay = remainingMs - REFRESH_MARGIN_MS;
        }
        scheduler.schedule(() -> {
            forceRefresh().thenRun(this::scheduleNextRefresh)
                    .exceptionally(ex -> {
                        log.warn("Token refresh failed, retrying in 60s: {}", ex.getMessage());
                        scheduler.schedule(this::scheduleNextRefresh, 60, TimeUnit.SECONDS);
                        return null;
                    });
        }, delay, TimeUnit.MILLISECONDS);
    }
}

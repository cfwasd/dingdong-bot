package com.napcat.starter.qqofficial;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * QQ 官方 Bot API v2 HTTP 客户端。
 * 消息发送走 REST API，鉴权用 Access Token。
 */
@Slf4j
public class QqOfficialApi {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String SANDBOX_BASE = "https://sandbox.api.sgroup.qq.com";
    private static final String PROD_BASE = "https://api.sgroup.qq.com";

    private final String baseUrl;
    private final String appId;
    private final String appSecret;
    private final OkHttpClient client;
    private final ObjectMapper mapper;

    private volatile String accessToken;
    private volatile long tokenExpiresAt;
    private final AtomicInteger msgSeqCounter = new AtomicInteger(0);

    public QqOfficialApi(String appId, String appSecret, boolean sandbox, Duration timeout, ObjectMapper mapper) {
        this.appId = Objects.requireNonNull(appId, "appId must not be null");
        this.appSecret = Objects.requireNonNull(appSecret, "appSecret must not be null");
        this.baseUrl = sandbox ? SANDBOX_BASE : PROD_BASE;
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
        this.client = new OkHttpClient.Builder()
                .connectTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    // ================================================================
    // Access Token
    // ================================================================

    public String getAccessToken() throws IOException {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiresAt - 60_000) {
            return accessToken;
        }
        synchronized (this) {
            if (accessToken != null && System.currentTimeMillis() < tokenExpiresAt - 60_000) {
                return accessToken;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("appId", appId);
            body.put("clientSecret", appSecret);

            Request request = new Request.Builder()
                    .url("https://bots.qq.com/app/getAppAccessToken")
                    .post(RequestBody.create(mapper.writeValueAsString(body), JSON))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    throw new IOException("Failed to get access token: " + response.code() + " " + respBody);
                }
                Map<String, Object> result = mapper.readValue(respBody, new TypeReference<Map<String, Object>>() {});
                accessToken = (String) result.get("access_token");
                Object expiresIn = result.get("expires_in");
                long expires = expiresIn instanceof Number ? ((Number) expiresIn).longValue() : 7200;
                tokenExpiresAt = System.currentTimeMillis() + expires * 1000;
                log.info("QQ official access token refreshed, expires in {}s", expires);
                return accessToken;
            }
        }
    }

    // ================================================================
    // 消息发送
    // ================================================================

    /** 发送单聊消息 */
    public void sendC2cMessage(String openid, String content, String msgId) throws IOException {
        Map<String, Object> body = buildTextBody(content, msgId);
        post("/v2/users/" + encode(openid) + "/messages", body);
    }

    /** 发送群聊消息 */
    public void sendGroupMessage(String groupOpenid, String content, String msgId) throws IOException {
        Map<String, Object> body = buildTextBody(content, msgId);
        post("/v2/groups/" + encode(groupOpenid) + "/messages", body);
    }

    /** 发送单聊 Markdown 消息 */
    public void sendC2cMarkdown(String openid, String markdownContent, String msgId) throws IOException {
        Map<String, Object> markdown = new LinkedHashMap<>();
        markdown.put("content", markdownContent);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg_type", 2);
        body.put("markdown", markdown);
        body.put("msg_id", msgId);
        post("/v2/users/" + encode(openid) + "/messages", body);
    }

    /** 发送群聊 Markdown 消息 */
    public void sendGroupMarkdown(String groupOpenid, String markdownContent, String msgId) throws IOException {
        Map<String, Object> markdown = new LinkedHashMap<>();
        markdown.put("content", markdownContent);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg_type", 2);
        body.put("markdown", markdown);
        body.put("msg_id", msgId);
        post("/v2/groups/" + encode(groupOpenid) + "/messages", body);
    }

    /** 发送单聊图片（通过 URL） */
    public void sendC2cImage(String openid, String imageUrl, String msgId) throws IOException {
        Map<String, Object> media = new LinkedHashMap<>();
        media.put("file_info", imageUrl);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg_type", 7);
        body.put("media", media);
        body.put("msg_id", msgId);
        post("/v2/users/" + encode(openid) + "/messages", body);
    }

    // ================================================================
    // 富媒体上传
    // ================================================================

    /** 上传单聊文件，返回 file_info */
    public String uploadC2cFile(String openid, int fileType, String url) throws IOException {
        return uploadFile("/v2/users/" + encode(openid) + "/files", fileType, url);
    }

    /** 上传群聊文件，返回 file_info */
    public String uploadGroupFile(String groupOpenid, int fileType, String url) throws IOException {
        return uploadFile("/v2/groups/" + encode(groupOpenid) + "/files", fileType, url);
    }

    private String uploadFile(String path, int fileType, String url) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("file_type", fileType);
        body.put("url", url);
        body.put("srv_send_msg", false);

        String json = mapper.writeValueAsString(body);
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", "QQBot " + getAccessToken())
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Upload failed: " + response.code() + " " + respBody);
            }
            Map<String, Object> result = mapper.readValue(respBody, new TypeReference<Map<String, Object>>() {});
            return (String) result.get("file_info");
        }
    }

    /** 发送富媒体消息（需先上传获得 file_info） */
    public void sendC2cMedia(String openid, String fileInfo, String msgId) throws IOException {
        Map<String, Object> media = new LinkedHashMap<>();
        media.put("file_info", fileInfo);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg_type", 7);
        body.put("media", media);
        body.put("msg_id", msgId);
        post("/v2/users/" + encode(openid) + "/messages", body);
    }

    /** 发送群聊富媒体消息 */
    public void sendGroupMedia(String groupOpenid, String fileInfo, String msgId) throws IOException {
        Map<String, Object> media = new LinkedHashMap<>();
        media.put("file_info", fileInfo);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg_type", 7);
        body.put("media", media);
        body.put("msg_id", msgId);
        post("/v2/groups/" + encode(groupOpenid) + "/messages", body);
    }

    // ================================================================
    // 网关地址
    // ================================================================

    public String getGatewayUrl() throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/gateway")
                .header("Authorization", "QQBot " + getAccessToken())
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get gateway: " + response.code() + " " + body);
            }
            Map<String, Object> result = mapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            return (String) result.get("url");
        }
    }

    // ================================================================
    // 内部
    // ================================================================

    private Map<String, Object> buildTextBody(String content, String msgId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", content);
        body.put("msg_type", 0);
        body.put("msg_id", msgId);
        return body;
    }

    private void post(String path, Map<String, Object> body) throws IOException {
        // 同一 msg_id 发送多条消息时必须分配不同的 msg_seq，否则会被去重
        if (body.containsKey("msg_id") && !body.containsKey("msg_seq")) {
            body.put("msg_seq", msgSeqCounter.incrementAndGet());
        }
        String url = baseUrl + path;
        String json = mapper.writeValueAsString(body);
        try {
            executePost(url, json);
        } catch (IOException e) {
            if (isTokenExpired(e.getMessage())) {
                log.info("QQ official token expired on {}, refreshing and retrying", path);
                forceRefreshToken();
                executePost(url, json);
            } else {
                throw e;
            }
        }
    }

    private void executePost(String url, String json) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "QQBot " + getAccessToken())
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.warn("QQ official API POST failed: {} {}", response.code(), respBody);
                throw new IOException("API call failed: " + response.code() + " " + respBody);
            }
            log.debug("QQ official API POST OK");
        }
    }

    private boolean isTokenExpired(String errorMessage) {
        return errorMessage != null && errorMessage.contains("11244");
    }

    private void forceRefreshToken() throws IOException {
        synchronized (this) {
            accessToken = null;
            tokenExpiresAt = 0;
            getAccessToken();
        }
    }

    private static String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}

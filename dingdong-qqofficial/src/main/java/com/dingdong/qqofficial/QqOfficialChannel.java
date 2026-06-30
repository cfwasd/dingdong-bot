package com.dingdong.qqofficial;

import com.dingdong.channel.api.BotChannel;
import com.dingdong.channel.api.ChannelEvent;
import com.dingdong.channel.api.MessageSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * QQ 官方通道实现。
 */
@Slf4j
public class QqOfficialChannel implements BotChannel {

    /**
     * 主动重连间隔：25 分钟。
     * QQ 官方网关会在连接建立约 30 分钟后强制关闭（code=4009 Session timed out）。
     * 为避免被服务端强制断开（用户感知掉线），在 25 分钟时主动断开并用当前 token 重连，
     * 把不可控的 4009 断开转为可控的平滑切换。
     */
    private static final long PROACTIVE_RECONNECT_INTERVAL_MS = 25 * 60 * 1000L;

    private final QqOfficialProperties properties;
    private final QqOfficialTokenManager tokenManager;
    private final QqOfficialApi api;
    private final QqOfficialIdMapper idMapper;
    private final ObjectMapper objectMapper;
    private volatile Consumer<ChannelEvent> eventConsumer;
    private volatile boolean running;
    private QqOfficialWsClient wsClient;
    private QqOfficialDispatcher.AgentInvoker agentInvoker;
    private volatile ScheduledExecutorService reconnectScheduler;
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private volatile boolean intentionallyReconnecting = false;
    private ScheduledExecutorService healthCheckScheduler;
    /** 主动重连调度：连接建立后 PROACTIVE_RECONNECT_INTERVAL_MS 触发一次 */
    private final ScheduledExecutorService proactiveReconnectScheduler;
    private volatile ScheduledFuture<?> proactiveReconnectFuture;

    public QqOfficialChannel(QqOfficialProperties properties) {
        this.properties = properties;
        this.tokenManager = new QqOfficialTokenManager(properties.getAppId(), properties.getAppSecret());
        this.api = new QqOfficialApi(properties.getAppId(), tokenManager, properties.isSandbox());
        this.idMapper = new QqOfficialIdMapper();
        this.objectMapper = new ObjectMapper();
        this.proactiveReconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "qq-official-proactive-reconnect");
            t.setDaemon(true);
            return t;
        });
    }

    @Override public String getChannelId() { return "qqofficial"; }

    public void setAgentInvoker(QqOfficialDispatcher.AgentInvoker agentInvoker) {
        this.agentInvoker = agentInvoker;
    }

    @Override
    public void start() {
        log.info("Starting QQ Official channel...");
        tokenManager.start();

        // 等待 token 就绪
        int retries = 0;
        while (!tokenManager.isTokenValid() && retries < 30) {
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            retries++;
        }
        if (!tokenManager.isTokenValid()) {
            log.error("QQ Official token not available after 30s, aborting start");
            return;
        }

        // Token 刷新后触发重连（新 token 需要重新建 WS 连接）
        tokenManager.setOnRefreshedCallback(() -> {
            log.info("Token refreshed, reconnecting WebSocket...");
            doReconnect();
        });

        running = true;
        connectWs();

        // 每 5 分钟检查一次 token 健康状态，防止假在线
        healthCheckScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "qq-official-health-check");
            t.setDaemon(true);
            return t;
        });
        healthCheckScheduler.scheduleAtFixedRate(this::checkTokenHealth, 5, 5, TimeUnit.MINUTES);
    }

    private synchronized void connectWs() {
        if (!running) return;
        try {
            String gatewayUrl = api.getGatewayUrl();
            log.info("QQ Official gateway URL: {}", gatewayUrl);

            Consumer<ChannelEvent> consumer = eventConsumer;
            QqOfficialDispatcher dispatcher = new QqOfficialDispatcher(this, consumer, agentInvoker);

            wsClient = new QqOfficialWsClient(new URI(gatewayUrl), tokenManager.getToken(),
                    properties.getIntents(), dispatcher, this::onWsClose);
            // Identify 发送后（服务端会话建立），启动主动重连定时器，
            // 在服务端强制 4009 断开前主动重连，避免用户感知掉线
            wsClient.setOnIdentify(this::scheduleProactiveReconnect);
            wsClient.connect();
            reconnectAttempts.set(0);
            log.info("QQ Official WS connecting...");
        } catch (Exception e) {
            log.error("Failed to connect QQ Official WS", e);
            scheduleReconnect();
        }
    }

    /**
     * 安排主动重连：在 PROACTIVE_RECONNECT_INTERVAL_MS 后主动断开并重建连接。
     * 仅当没有被其他重连流程（token 刷新/close 触发）抢占时才执行。
     */
    private void scheduleProactiveReconnect() {
        if (!running) return;
        synchronized (this) {
            if (proactiveReconnectFuture != null) {
                proactiveReconnectFuture.cancel(false);
            }
            proactiveReconnectFuture = proactiveReconnectScheduler.schedule(() -> {
                log.info("Proactive reconnect triggered ({}min after identify), reconnecting before server timeout...",
                        PROACTIVE_RECONNECT_INTERVAL_MS / 60000);
                doReconnect();
            }, PROACTIVE_RECONNECT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }
        log.info("Proactive reconnect scheduled in {}ms ({}min)", PROACTIVE_RECONNECT_INTERVAL_MS, PROACTIVE_RECONNECT_INTERVAL_MS / 60000);
    }

    private void onWsClose(int code, String reason) {
        if (!running) return;
        // 连接已关闭，主动重连定时器失去意义（会由 close 流程触发重连），取消它
        cancelProactiveReconnect();
        if (intentionallyReconnecting) {
            log.info("WS closed due to intentional reconnect, skipping scheduled reconnect");
            return;
        }
        if ("Heartbeat timeout".equals(reason)) {
            log.warn("QQ Official WS closed due to heartbeat timeout, forcing token refresh and reconnect...");
            doReconnectWithTokenRefresh();
            return;
        }
        log.warn("QQ Official WS closed, scheduling reconnect... code={}, reason={}", code, reason);
        scheduleReconnect();
    }

    private synchronized void cancelProactiveReconnect() {
        if (proactiveReconnectFuture != null) {
            proactiveReconnectFuture.cancel(false);
            proactiveReconnectFuture = null;
        }
    }

    private synchronized void scheduleReconnect() {
        if (!running || reconnectScheduler != null) return;
        reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "qq-official-reconnect");
            t.setDaemon(true);
            return t;
        });
        int attempt = reconnectAttempts.incrementAndGet();
        long delay = Math.min(attempt * 5L, 60L); // 5s, 10s, 15s... max 60s
        log.info("QQ Official WS reconnect scheduled in {}s (attempt {})", delay, attempt);
        reconnectScheduler.schedule(() -> {
            reconnectScheduler.shutdownNow();
            reconnectScheduler = null;
            connectWs();
        }, delay, TimeUnit.SECONDS);
    }

    private synchronized void doReconnect() {
        intentionallyReconnecting = true;
        try {
            if (wsClient != null) {
                try { wsClient.closeBlocking(); } catch (Exception ignored) {}
            }
            // closeBlocking 会触发 onClose → cancelProactiveReconnect，
            // 取消这个误触发的定时重连，避免短时间内重复连接
            cancelProactiveReconnect();
            if (reconnectScheduler != null) {
                reconnectScheduler.shutdownNow();
                reconnectScheduler = null;
            }
            connectWs();
        } finally {
            intentionallyReconnecting = false;
        }
    }

    private void doReconnectWithTokenRefresh() {
        try {
            tokenManager.forceRefresh().join();
            log.info("Token refreshed after heartbeat timeout, reconnecting...");
        } catch (Exception e) {
            log.error("Token refresh after heartbeat timeout failed, reconnecting with existing token anyway", e);
        }
        doReconnect();
    }

    private void checkTokenHealth() {
        if (!running) return;
        if (!tokenManager.isTokenValid()) {
            log.warn("Token health check failed: token invalid, forcing refresh and reconnect...");
            try {
                tokenManager.forceRefresh().join();
                doReconnect();
            } catch (Exception e) {
                log.error("Token health check refresh failed", e);
            }
        }
    }

    @Override
    public void stop() {
        running = false;
        tokenManager.setOnRefreshedCallback(null);
        // 取消主动重连定时器，避免 stop 后仍然触发重连
        cancelProactiveReconnect();
        if (proactiveReconnectScheduler != null) {
            proactiveReconnectScheduler.shutdownNow();
        }
        if (healthCheckScheduler != null) {
            healthCheckScheduler.shutdownNow();
            healthCheckScheduler = null;
        }
        if (reconnectScheduler != null) {
            reconnectScheduler.shutdownNow();
            reconnectScheduler = null;
        }
        if (wsClient != null) {
            try { wsClient.closeBlocking(); } catch (Exception e) { log.warn("WS close error", e); }
        }
        tokenManager.stop();
        log.info("QQ Official channel stopped");
    }

    @Override public boolean isConnected() { return running && wsClient != null && wsClient.isOpen(); }

    @Override public void setEventConsumer(Consumer<ChannelEvent> consumer) { this.eventConsumer = consumer; }

    @Override
    public MessageSender getMessageSender() {
        return new QqOfficialMessageSender(api, idMapper, properties);
    }

    public QqOfficialApi getApi() { return api; }
    public QqOfficialIdMapper getIdMapper() { return idMapper; }
    public QqOfficialTokenManager getTokenManager() { return tokenManager; }
    public QqOfficialProperties getProperties() { return properties; }
}

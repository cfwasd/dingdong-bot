package com.napcat.starter.config;

import com.napcat.agent.agent.NapCatAgent;
import com.napcat.agent.memory.DailyMemorySummarizer;
import com.napcat.agent.scheduler.ScheduleTool;
import com.napcat.agent.tool.ToolRegistry;
import com.napcat.core.adapter.BotAdapter;
import com.napcat.core.adapter.MessageRouter;
import com.napcat.core.api.NapCatApi;
import com.napcat.core.config.BotProperties;
import com.napcat.core.context.EventContext;
import com.napcat.core.context.EventContextHolder;
import com.napcat.core.event.GroupMessageEvent;
import com.dingdong.channel.api.ChannelEvent;
import com.napcat.core.handler.EventDispatcher;
import com.napcat.core.handler.HandlerRegistry;
import com.napcat.core.message.MessageChain;
import com.napcat.core.scheduler.SchedulePoller;
import com.napcat.core.scheduler.ScheduleStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Spring 生命周期管理：在所有 Bean 初始化完成后启动适配器，
 * 绑定 MessageRouter → EventDispatcher 的事件管道。
 */
@Slf4j
public class NapCatLifecycle implements SmartLifecycle {

    private final List<BotAdapter> adapters;
    private final EventDispatcher dispatcher;
    private final NapCatApi api;
    private final HandlerRegistry registry;
    private final MessageRouter messageRouter;
    private final BotProperties botProperties;
    private final ObjectProvider<NapCatAgent> agentProvider;
    private final ApplicationContext ctx;
    private volatile boolean running = false;
    private ScheduledExecutorService sessionCleanupExecutor;

    public NapCatLifecycle(List<BotAdapter> adapters, EventDispatcher dispatcher,
                           NapCatApi api, HandlerRegistry registry,
                           MessageRouter messageRouter,
                           BotProperties botProperties,
                           ObjectProvider<NapCatAgent> agentProvider,
                           ApplicationContext ctx) {
        this.adapters = adapters;
        this.dispatcher = dispatcher;
        this.api = api;
        this.registry = registry;
        this.messageRouter = messageRouter;
        this.botProperties = botProperties;
        this.agentProvider = agentProvider;
        this.ctx = ctx;
    }

    @Override
    public void start() {
        // 事件管道：MessageRouter → EventDispatcher
        messageRouter.setEventConsumer(this::onEvent);

        // 启动调度器
        try {
            SchedulePoller poller = ctx.getBeanProvider(SchedulePoller.class).getIfAvailable();
            if (poller != null) {
                poller.start();
                log.info("SchedulePoller started");
            }

            // 注册 ScheduleTool 到 ToolRegistry
            ScheduleTool scheduleTool = ctx.getBeanProvider(ScheduleTool.class).getIfAvailable();
            ToolRegistry toolRegistry = ctx.getBeanProvider(ToolRegistry.class).getIfAvailable();
            if (scheduleTool != null && toolRegistry != null) {
                toolRegistry.register(scheduleTool);
                log.info("ScheduleTool registered with ToolRegistry");
            }

            // 注册每日记忆归纳定时任务（凌晨 1 点）
            DailyMemorySummarizer summarizer = ctx.getBeanProvider(DailyMemorySummarizer.class).getIfAvailable();
            ScheduleStore scheduleStore = ctx.getBeanProvider(ScheduleStore.class).getIfAvailable();
            if (summarizer != null && scheduleStore != null) {
                ScheduleStore.ScheduleEntry dailyTask = new ScheduleStore.ScheduleEntry();
                dailyTask.setName("每日记忆归纳");
                dailyTask.setCron("0 0 1 * * ?");
                dailyTask.setAction("custom");
                dailyTask.setTargetType("system");
                dailyTask.setTargetId(0);
                dailyTask.setEnabled(true);
                dailyTask.setRecurring(true);
                String taskId = scheduleStore.insertOrIgnoreByName(dailyTask);
                if (taskId != null && poller != null) {
                    dailyTask.setId(taskId);
                    poller.scheduleNow(dailyTask);
                }
                log.info("Daily memory summary schedule registered");
            }

            // 启动会话过期清理（每 30 分钟）
            com.napcat.agent.session.SessionManager sessionManager =
                    ctx.getBeanProvider(com.napcat.agent.session.SessionManager.class).getIfAvailable();
            if (sessionManager != null) {
                sessionCleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "napcat-session-cleanup");
                    t.setDaemon(true);
                    return t;
                });
                sessionCleanupExecutor.scheduleWithFixedDelay(
                        sessionManager::clearExpired,
                        30, 30, TimeUnit.MINUTES);
            }

            // 注入测试记忆数据（仅当 napcat.memory.test-data-enabled=true 时）
            com.napcat.agent.memory.MemoryTestDataInjector testInjector =
                    ctx.getBeanProvider(com.napcat.agent.memory.MemoryTestDataInjector.class).getIfAvailable();
            if (testInjector != null) {
                testInjector.injectTestData();
            }
        } catch (Exception e) {
            log.warn("Failed to start scheduler: {}", e.getMessage());
        }

        // at-me-trigger 兜底：被 @ 时自动走 Agent
        if (botProperties.isAtMeTrigger()) {
            NapCatAgent agent = agentProvider.getIfAvailable();
            if (agent != null) {
                registry.setFallbackHandler(event -> {
                    if (event instanceof GroupMessageEvent ge
                            && (ge.getMessage().isAt(botProperties.getSelfId())
                                || botProperties.matchesWakeWord(ge.getMessage().toAgentPrompt()))) {
                        String prompt = ge.getMessage().toAgentPrompt();
                        List<String> processSteps = Collections.synchronizedList(new ArrayList<>());
                        com.napcat.agent.agent.AgentConfig config =
                                com.napcat.agent.agent.AgentConfig.builder()
                                        .showToolProcess(true)
                                        .ackCallback(() -> ge.reply(MessageChain.ofFace(277)))
                                        .build();
                        agent.chat(ge.getUserId(), ge.getGroupId(), prompt, config,
                                processSteps::add)
                                .thenAccept(reply -> {
                                    ge.reply(reply);
                                    // 将思考过程以合并转发发送
                                    if (!processSteps.isEmpty()) {
                                        try {
                                            long selfId = botProperties.getSelfId();
                                            String nickname = String.valueOf(selfId);
                                            // 使用 raw API 发送合并转发
                                            List<Map<String, Object>> messages = new ArrayList<>();
                                            for (String step : processSteps) {
                                                Map<String, Object> textSeg = new java.util.LinkedHashMap<>();
                                                textSeg.put("type", "text");
                                                textSeg.put("data", Map.of("text", step));
                                                Map<String, Object> nodeData = new java.util.LinkedHashMap<>();
                                                nodeData.put("name", nickname);
                                                nodeData.put("uin", String.valueOf(selfId));
                                                nodeData.put("content", List.of(textSeg));
                                                Map<String, Object> node = new java.util.LinkedHashMap<>();
                                                node.put("type", "node");
                                                node.put("data", nodeData);
                                                messages.add(node);
                                            }
                                            api.call("send_group_forward_msg",
                                                    "group_id", ge.getGroupId(),
                                                    "messages", messages);
                                        } catch (Exception e) {
                                            log.warn("Failed to send process forward", e);
                                            for (String step : processSteps) {
                                                ge.reply(step);
                                            }
                                        }
                                    }
                                });
                    }
                });
            }
        }

        for (BotAdapter adapter : adapters) {
            adapter.setMessageHandler(messageRouter);
            adapter.start();
        }
        running = true;
    }

    private void onEvent(ChannelEvent event) {
        // 注入 API 到事件对象，确保异步回调（Agent 等）跨线程 reply 可用
        if (event instanceof com.napcat.core.event.MessageEvent me) {
            me.setNapCatApi(api);
        }
        EventContextHolder.set(new EventContext(event, api));
        try {
            dispatcher.dispatch(event);
        } finally {
            EventContextHolder.clear();
        }
    }

    @Override
    public void stop() {
        // 关闭前先将所有内存会话全量持久化
        persistAllSessionsBeforeShutdown();

        try {
            SchedulePoller poller = ctx.getBeanProvider(SchedulePoller.class).getIfAvailable();
            if (poller != null) {
                poller.stop();
            }
        } catch (Exception e) {
            log.warn("Failed to stop scheduler: {}", e.getMessage());
        }

        try {
            api.shutdown();
        } catch (Exception e) {
            log.warn("Failed to shutdown NapCatApi: {}", e.getMessage());
        }

        if (sessionCleanupExecutor != null && !sessionCleanupExecutor.isShutdown()) {
            sessionCleanupExecutor.shutdown();
            try {
                if (!sessionCleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    sessionCleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                sessionCleanupExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        for (BotAdapter adapter : adapters) {
            adapter.stop();
        }
        running = false;
        log.info("NapCat stopped");
    }

    private void persistAllSessionsBeforeShutdown() {
        try {
            com.napcat.agent.session.SessionManager sessionManager =
                    ctx.getBeanProvider(com.napcat.agent.session.SessionManager.class).getIfAvailable();
            com.napcat.agent.memory.MemoryStore memoryStore =
                    ctx.getBeanProvider(com.napcat.agent.memory.MemoryStore.class).getIfAvailable();
            if (sessionManager == null || memoryStore == null) return;

            int persisted = 0;
            for (com.napcat.agent.session.SessionKey key : sessionManager.getAllKeys()) {
                com.napcat.agent.session.Session session = sessionManager.getIfPresent(key);
                if (session != null && !session.getHistory().isEmpty()) {
                    memoryStore.persistFullSession(key, formatSessionHistory(session));
                    persisted++;
                }
            }
            if (persisted > 0) {
                log.info("Persisted {} sessions before shutdown", persisted);
            }
        } catch (Exception e) {
            log.warn("Failed to persist sessions before shutdown: {}", e.getMessage());
        }
    }

    private String formatSessionHistory(com.napcat.agent.session.Session session) {
        return session.getFormattedHistory();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }
}

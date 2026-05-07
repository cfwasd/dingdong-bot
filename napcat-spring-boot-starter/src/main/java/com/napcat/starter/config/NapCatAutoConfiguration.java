package com.napcat.starter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.napcat.agent.agent.NapCatAgent;
import com.napcat.agent.llm.LlmProvider;
import com.napcat.agent.session.SessionManager;
import com.napcat.agent.tool.ToolRegistry;
import com.napcat.core.adapter.*;
import com.napcat.core.api.NapCatApi;
import com.napcat.core.config.BotProperties;
import com.napcat.core.handler.EventDispatcher;
import com.napcat.core.handler.HandlerRegistry;
import com.napcat.starter.adapter.HttpServerAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(NapCatProperties.class)
public class NapCatAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper napcatObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public BotProperties botProperties(NapCatProperties props) {
        BotProperties bp = new BotProperties();
        bp.setSelfId(props.getBot().getSelfId());
        bp.setCommandPrefix(props.getBot().getCommandPrefix());
        bp.setAtMeTrigger(props.getBot().isAtMeTrigger());
        bp.setIgnoreSelfMessage(props.getBot().isIgnoreSelfMessage());
        bp.setSuperUsers(props.getBot().getSuperUsers());
        return bp;
    }

    // ================================================================
    // Adapter
    // ================================================================

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "napcat.adapter", name = "type", havingValue = "websocket-client", matchIfMissing = true)
    public BotAdapter wsClientBotAdapter(NapCatProperties props, ObjectMapper mapper) {
        var c = props.getAdapter().getWebsocketClient();
        return new WsClientAdapter(c.getUrl(), c.getToken(), c.getReconnectInterval(), mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "napcat.adapter", name = "type", havingValue = "http-client")
    public BotAdapter httpClientBotAdapter(NapCatProperties props, ObjectMapper mapper) {
        var c = props.getAdapter().getHttpClient();
        return new HttpClientAdapter(c.getUrl(), c.getToken(), c.getTimeout(), mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "napcat.adapter", name = "type", havingValue = "websocket-server")
    public BotAdapter wsServerBotAdapter(NapCatProperties props, ObjectMapper mapper) {
        var c = props.getAdapter().getWebsocketServer();
        return new WsServerAdapter(c.getHost(), c.getPort(), c.getToken(), mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "napcat.adapter", name = "type", havingValue = "http-server")
    public BotAdapter httpServerBotAdapter(NapCatProperties props, ObjectMapper mapper) {
        var c = props.getAdapter().getHttpServer();
        return new HttpServerAdapter(mapper,
                c.getPath(), c.getToken(),
                c.getApiUrl(), c.getApiToken(), c.getApiTimeout());
    }

    // ================================================================
    // API + Router + Dispatcher
    // ================================================================

    @Bean
    @ConditionalOnMissingBean
    public NapCatApi napCatApi(List<BotAdapter> adapters, ObjectMapper mapper) {
        return new NapCatApi(adapters.isEmpty() ? null : adapters.get(0), mapper, 30000);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageRouter messageRouter(ObjectMapper mapper, NapCatApi api) {
        return new MessageRouter(mapper, api);
    }

    @Bean
    @ConditionalOnMissingBean
    public HandlerRegistry handlerRegistry(BotProperties botProperties) {
        return new HandlerRegistry(botProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public EventDispatcher eventDispatcher(HandlerRegistry registry, BotProperties botProperties,
                                           NapCatApi api, NapCatProperties props) {
        var execProps = props.getCore().getEventExecutor();
        boolean sync = props.getCore().isSyncEventProcessing();
        Executor executor = new ThreadPoolExecutor(
                execProps.getCorePoolSize(),
                execProps.getMaxPoolSize(),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(execProps.getQueueCapacity()),
                r -> {
                    Thread t = new Thread(r, "napcat-event-pool");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        return new EventDispatcher(registry, botProperties, api, sync, executor);
    }

    // ================================================================
    // Agent
    // ================================================================

    @Bean
    @ConditionalOnProperty(prefix = "napcat.agent", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry(ApplicationContext ctx) {
        ToolRegistry registry = new ToolRegistry();
        Map<String, Object> tools = ctx.getBeansWithAnnotation(com.napcat.core.annotation.Tool.class);
        for (Object bean : tools.values()) {
            registry.register(bean);
        }
        return registry;
    }

    @Bean
    @ConditionalOnProperty(prefix = "napcat.agent", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public SessionManager sessionManager(NapCatProperties props) {
        return new SessionManager(props.getAgent().getSessionTtl());
    }

    @Bean
    @ConditionalOnProperty(prefix = "napcat.agent", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public NapCatAgent napCatAgent(ObjectProvider<LlmProvider> llmProvider, ToolRegistry toolRegistry,
                                    SessionManager sessionManager, NapCatProperties props) {
        LlmProvider provider = llmProvider.getIfAvailable();
        if (provider == null) {
            throw new IllegalStateException("No LlmProvider bean found. Please add a provider dependency like napcat-llm-openai.");
        }
        return new NapCatAgent(provider, toolRegistry, sessionManager,
                props.getAgent().getSystemPrompt(), props.getAgent().getMaxReactRounds());
    }

    // ================================================================
    // 后处理器 + 生命周期
    // ================================================================

    @Bean
    public NapCatBeanPostProcessor napCatBeanPostProcessor(HandlerRegistry registry, ApplicationContext ctx) {
        return new NapCatBeanPostProcessor(registry, ctx);
    }

    @Bean
    public NapCatLifecycle napCatLifecycle(List<BotAdapter> adapters, EventDispatcher dispatcher,
                                           NapCatApi api, HandlerRegistry registry,
                                           MessageRouter messageRouter,
                                           ApplicationContext ctx) {
        return new NapCatLifecycle(adapters, dispatcher, api, registry, messageRouter, ctx);
    }
}

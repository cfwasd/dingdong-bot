package com.dingdong.boot.starter.config;

import com.dingdong.channel.api.BotChannel;
import com.dingdong.core.DingDongCoreChannel;
import com.dingdong.core.adapter.BotAdapter;
import com.dingdong.core.handler.EventDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 叮咚 (DingDong) 自动配置。
 * 装配 DingDong BotChannel 通道层。
 */
@Slf4j
@AutoConfiguration
public class DingDongAutoConfiguration {

    // ============================================================
    // OneBot11 通道
    // ============================================================

    @Bean
    @ConditionalOnProperty(prefix = "dingdong.qq", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public DingDongCoreChannel oneBotChannel(BotAdapter adapter) {
        return new DingDongCoreChannel(adapter);
    }

    // ============================================================
    // DingDongLifecycle
    // ============================================================

    @Bean
    @ConditionalOnMissingBean
    public DingDongLifecycle dingDongLifecycle(
            java.util.List<BotChannel> channels,
            EventDispatcher eventDispatcher) {
        return new DingDongLifecycle(channels, eventDispatcher);
    }

    /**
     * QQ 官方通道配置，仅在 dingdong-qqofficial 在 classpath 时生效。
     * 使用 {@code @ConditionalOnClass(name = "...")} 字符串形式避免
     * optional 依赖缺失时产生 NoClassDefFoundError。
     */
    @AutoConfiguration
    @ConditionalOnClass(name = "com.dingdong.qqofficial.QqOfficialChannel")
    @EnableConfigurationProperties
    @ConditionalOnProperty(prefix = "dingdong.qq-official", name = "enabled", havingValue = "true")
    public static class QqOfficialConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public com.dingdong.qqofficial.QqOfficialProperties qqOfficialProperties() {
            return new com.dingdong.qqofficial.QqOfficialProperties();
        }

        @Bean
        @ConditionalOnMissingBean
        public com.dingdong.qqofficial.QqOfficialChannel qqOfficialChannel(
                com.dingdong.qqofficial.QqOfficialProperties properties,
                org.springframework.beans.factory.ObjectProvider<com.dingdong.agent.agent.NapCatAgent> agentProvider) {
            com.dingdong.qqofficial.QqOfficialChannel channel =
                    new com.dingdong.qqofficial.QqOfficialChannel(properties);
            com.dingdong.agent.agent.NapCatAgent agent = agentProvider.getIfAvailable();
            if (agent != null) {
                channel.setAgentInvoker(agent::chat);
            }
            return channel;
        }
    }
}

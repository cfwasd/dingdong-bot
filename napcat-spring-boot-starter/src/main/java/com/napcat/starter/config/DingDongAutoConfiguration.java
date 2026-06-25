package com.napcat.starter.config;

import com.dingdong.channel.api.BotChannel;
import com.dingdong.qqofficial.QqOfficialChannel;
import com.dingdong.qqofficial.QqOfficialProperties;
import com.napcat.core.DingDongCoreChannel;
import com.napcat.core.adapter.BotAdapter;
import com.napcat.core.handler.EventDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    @ConditionalOnProperty(prefix = "napcat.qq", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public DingDongCoreChannel oneBotChannel(BotAdapter adapter) {
        return new DingDongCoreChannel(adapter);
    }

    // ============================================================
    // QQ 官方通道
    // ============================================================

    @Bean
    @ConditionalOnProperty(prefix = "napcat.qq-official", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public QqOfficialProperties qqOfficialProperties() {
        return new QqOfficialProperties();
    }

    @Bean
    @ConditionalOnProperty(prefix = "napcat.qq-official", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public QqOfficialChannel qqOfficialChannel(QqOfficialProperties properties) {
        return new QqOfficialChannel(properties);
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
}

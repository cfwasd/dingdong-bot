package com.napcat.llm.anthropic;

import com.napcat.agent.llm.LlmProvider;
import com.napcat.starter.config.NapCatProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "napcat.llm", name = "provider", havingValue = "anthropic")
public class AnthropicProviderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LlmProvider.class)
    public LlmProvider anthropicLlmProvider(NapCatProperties props) {
        var c = props.getLlm().getAnthropic();
        return new AnthropicProvider(
                c.getBaseUrl(), c.getApiKey(), c.getModel(),
                c.getMaxTokens(), c.getTemperature(), c.getTimeout()
        );
    }
}

package com.dingdong.llm.ollama;

import com.dingdong.agent.llm.LlmProvider;
import com.dingdong.boot.starter.config.NapCatProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "dingdong.llm", name = "provider", havingValue = "ollama")
public class OllamaProviderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LlmProvider.class)
    public LlmProvider ollamaLlmProvider(NapCatProperties props) {
        var c = props.getLlm().getOllama();
        return new OllamaProvider(c.getBaseUrl(), c.getModel(), c.getTimeout());
    }
}

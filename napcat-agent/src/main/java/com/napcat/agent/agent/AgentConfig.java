package com.napcat.agent.agent;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentConfig {
    @Builder.Default
    private int maxRounds = 5;
    private String systemPrompt;
    @Builder.Default
    private long timeoutPerRound = 30000;
}

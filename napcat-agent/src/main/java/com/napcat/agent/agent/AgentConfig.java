package com.napcat.agent.agent;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentConfig {
    private int maxRounds = 5;
    private String systemPrompt;
    private long timeoutPerRound = 30000;
}

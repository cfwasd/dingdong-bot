package com.napcat.agent.llm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String role;    // system / user / assistant / tool
    private String content;
    private String name;    // tool name (for tool role)
}

package com.napcat.admin.bot;

import com.napcat.agent.agent.NapCatAgent;
import com.napcat.core.annotation.MentionFilter;
import com.napcat.core.annotation.OnGroupMessage;
import com.napcat.core.event.GroupMessageEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class AgentDemoBot {

    @Autowired(required = false)
    @Lazy
    private NapCatAgent agent;

    @OnGroupMessage
    @MentionFilter
    public void onAt(GroupMessageEvent event) {
        if (agent == null) return;
        String plainText = event.getMessage().toPlainText();
        agent.chat(event.getUserId(), plainText)
                .thenAccept(event::reply);
    }
}

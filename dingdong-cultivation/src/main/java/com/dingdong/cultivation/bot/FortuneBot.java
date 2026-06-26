package com.dingdong.cultivation.bot;

import com.dingdong.channel.api.ChannelEvent;
import com.dingdong.channel.api.ChannelMessageEvent;
import com.dingdong.core.annotation.Command;
import com.dingdong.core.annotation.OnGroupMessage;
import com.dingdong.core.annotation.OnPrivateMessage;
import com.dingdong.core.event.MessageEvent;
import com.dingdong.cultivation.tool.FortuneTool;
import org.springframework.stereotype.Component;

@Component
public class FortuneBot {

    private final FortuneTool fortuneTool;

    public FortuneBot(FortuneTool fortuneTool) {
        this.fortuneTool = fortuneTool;
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/运势", description = "今日运势")
    public String fortune(ChannelEvent event) {
        String name = resolveUserName(event);
        return fortuneTool.fortune(name);
    }

    private String resolveUserName(ChannelEvent event) {
        if (event instanceof MessageEvent me) {
            return me.getSenderObj() != null ? me.getSenderObj().getNickname() : "";
        }
        if (event instanceof ChannelMessageEvent chMsg) {
            return chMsg.getSender() != null ? chMsg.getSender().getNickname() : "";
        }
        return "";
    }
}

package com.dingdong.cultivation.bot;

import com.dingdong.channel.api.ChannelEvent;
import com.dingdong.channel.api.ChannelMessageEvent;
import com.dingdong.core.annotation.Command;
import com.dingdong.core.annotation.OnGroupMessage;
import com.dingdong.core.annotation.OnPrivateMessage;
import com.dingdong.core.event.GroupMessageEvent;
import com.dingdong.core.event.MessageEvent;
import com.dingdong.cultivation.tool.MarriageTool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MarriageBot {

    private final MarriageTool marriageTool;

    public MarriageBot(MarriageTool marriageTool) {
        this.marriageTool = marriageTool;
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/求婚", description = "向TA求婚")
    @Command(value = "求婚", description = "向TA求婚")
    public String propose(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        long targetId = resolveMentionTargetId(event);
        return marriageTool.propose(
            String.valueOf(userId), String.valueOf(targetId), String.valueOf(groupId), userName, "");
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/同意求婚", description = "接受求婚")
    @Command(value = "同意求婚", description = "接受求婚")
    public String accept(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return marriageTool.accept(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/离婚", description = "解除婚姻")
    @Command(value = "离婚", description = "解除婚姻")
    public String divorce(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return marriageTool.divorce(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/我的CP", description = "查看CP状态")
    @Command(value = "我的CP", description = "查看CP状态")
    public String cpStatus(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return marriageTool.cpStatus(
            String.valueOf(userId), String.valueOf(groupId), null, userName);
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/双修", description = "道侣双修（24h冷却）")
    @Command(value = "双修", description = "道侣双修（24h冷却）")
    public String dualCultivate(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return marriageTool.dualCultivate(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }

    private long resolveUserId(ChannelEvent event) {
        if (event instanceof MessageEvent me) return me.getUserId();
        if (event instanceof ChannelMessageEvent chMsg) return chMsg.getUserId();
        return 0;
    }

    private long resolveGroupId(ChannelEvent event) {
        if (event instanceof GroupMessageEvent ge) return ge.getGroupId();
        if (event instanceof ChannelMessageEvent chMsg) return chMsg.getGroupId();
        return 0;
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

    private long resolveMentionTargetId(ChannelEvent event) {
        if (event instanceof MessageEvent me) {
            try {
                List<Long> ats = me.getMessage().getAts();
                if (ats != null && !ats.isEmpty()) return ats.get(0);
            } catch (Exception ignored) {}
        }
        if (event instanceof ChannelMessageEvent chMsg) {
            String text = chMsg.getPlainText();
            if (text != null) {
                int start = text.indexOf("<@");
                if (start >= 0) {
                    int end = text.indexOf(">", start);
                    if (end > start + 2) {
                        try {
                            return Long.parseLong(text.substring(start + 2, end));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
        return 0;
    }
}

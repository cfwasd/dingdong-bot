package com.dingdong.cultivation.bot;

import com.dingdong.channel.api.ChannelEvent;
import com.dingdong.channel.api.ChannelMessageEvent;
import com.dingdong.core.annotation.Command;
import com.dingdong.core.annotation.OnGroupMessage;
import com.dingdong.core.annotation.OnPrivateMessage;
import com.dingdong.core.event.GroupMessageEvent;
import com.dingdong.core.event.MessageEvent;
import com.dingdong.cultivation.tool.CheckInTool;
import org.springframework.stereotype.Component;

@Component
public class CheckInBot {

    private final CheckInTool checkInTool;

    public CheckInBot(CheckInTool checkInTool) {
        this.checkInTool = checkInTool;
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/签到", description = "每日签到（修仙者额外修为）")
    @Command(value = "签到", description = "每日签到（修仙者额外修为）")
    public String checkin(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return checkInTool.checkin(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/签到状态", description = "签到记录和排名")
    @Command(value = "签到状态", description = "签到记录和排名")
    public String checkinStatus(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return checkInTool.status(
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
}

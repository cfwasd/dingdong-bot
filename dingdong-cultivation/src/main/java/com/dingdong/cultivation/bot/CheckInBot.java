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
    public String checkin(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return wrapWithKeyboard(event, checkInTool.checkin(
            String.valueOf(userId), String.valueOf(groupId), userName));
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/签到状态", description = "签到记录和排名")
    public String checkinStatus(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return wrapWithKeyboard(event, checkInTool.status(
            String.valueOf(userId), String.valueOf(groupId), userName));
    }

    /**
     * QQ 官方渠道下，将文本结果和按钮面板一起发送。
     */
    private String wrapWithKeyboard(ChannelEvent event, String text) {
        if (text == null || !"qqofficial".equals(event.getChannelId())) return text;
        if (!(event instanceof ChannelMessageEvent chMsg) || chMsg.getApi() == null) return text;

        String markdown = text.replaceAll("(?m)^", "> ").trim();
        String keyboardJson = buildCheckInKeyboard();

        boolean sent = false;
        try {
            Object api = chMsg.getApi();
            java.lang.reflect.Method m = api.getClass().getMethod("replyWithKeyboard", String.class, String.class);
            m.invoke(api, markdown, keyboardJson);
            sent = true;
        } catch (NoSuchMethodException e) {
            // fallback
        } catch (Exception e) {
            // fallback
        }
        if (!sent) {
            chMsg.getApi().reply(text);
        }
        return null;
    }

    private String buildCheckInKeyboard() {
        return """
            {"content":{"rows":[
              {"buttons":[
                {"render_data":{"label":"📅 签到","style":1},"action":{"type":2,"permission":{"type":2},"data":"/签到"}},
                {"render_data":{"label":"🔮 运势","style":0},"action":{"type":2,"permission":{"type":2},"data":"/运势"}},
                {"render_data":{"label":"📊 修仙状态","style":0},"action":{"type":2,"permission":{"type":2},"data":"/修仙状态"}}
              ]},
              {"buttons":[
                {"render_data":{"label":"🗡️ 修炼","style":0},"action":{"type":2,"permission":{"type":2},"data":"/修炼"}},
                {"render_data":{"label":"💊 丹药商店","style":0},"action":{"type":2,"permission":{"type":2},"data":"/丹药商店"}},
                {"render_data":{"label":"❤️ 我的CP","style":0},"action":{"type":2,"permission":{"type":2},"data":"/我的CP"}}
              ]}
            ]}}
            """.replaceAll("\\s*\\n\\s*", "");
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

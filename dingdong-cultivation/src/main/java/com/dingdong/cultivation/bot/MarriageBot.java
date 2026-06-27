package com.dingdong.cultivation.bot;

import com.dingdong.channel.api.ChannelEvent;
import com.dingdong.channel.api.ChannelMessageEvent;
import com.dingdong.core.annotation.Command;
import com.dingdong.core.annotation.OnGroupMessage;
import com.dingdong.core.annotation.OnPrivateMessage;
import com.dingdong.core.config.BotProperties;
import com.dingdong.core.event.GroupMessageEvent;
import com.dingdong.core.event.MessageEvent;
import com.dingdong.cultivation.tool.MarriageTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class MarriageBot {

    private final MarriageTool marriageTool;
    private final BotProperties botProperties;

    public MarriageBot(MarriageTool marriageTool, BotProperties botProperties) {
        this.marriageTool = marriageTool;
        this.botProperties = botProperties;
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/求婚", description = "向TA求婚")
    public String propose(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        long targetId = resolveMentionTargetId(event);
        String targetName = "";

        // QQ 官方渠道：API 不推送 @ 信息，从 content 中解析目标名字
        if (targetId == 0 && "qqofficial".equals(event.getChannelId())) {
            String text = getPlainText(event);
            if (text != null && !text.isBlank()) {
                String args = text.replaceFirst("^/求婚\\s*", "").trim();
                if (!args.isBlank()) {
                    targetName = args;
                    targetId = Math.abs(targetName.hashCode());
                    if (targetId == 0) targetId = 1;
                    log.debug("[QQ官方/求婚] 从文本解析目标: name={}, hashId={}", targetName, targetId);
                }
            }
            if (targetId == 0) {
                return "💍 请发送 `/求婚 对方名字` 来向TA求婚~\n（QQ官方渠道不支持@功能）";
            }
        }

        return wrapWithKeyboard(event, marriageTool.propose(
            String.valueOf(userId), String.valueOf(targetId), String.valueOf(groupId), userName, targetName));
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/同意求婚", description = "接受求婚")
    public String accept(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return wrapWithKeyboard(event, marriageTool.accept(
            String.valueOf(userId), String.valueOf(groupId), userName));
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/离婚", description = "解除婚姻")
    public String divorce(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return wrapWithKeyboard(event, marriageTool.divorce(
            String.valueOf(userId), String.valueOf(groupId), userName));
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/我的CP", description = "查看CP状态")
    public String cpStatus(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return wrapWithKeyboard(event, marriageTool.cpStatus(
            String.valueOf(userId), String.valueOf(groupId), null, userName));
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/双修", description = "道侣双修（24h冷却）")
    public String dualCultivate(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return wrapWithKeyboard(event, marriageTool.dualCultivate(
            String.valueOf(userId), String.valueOf(groupId), userName));
    }

    /**
     * 【QQ官方】婚姻面板：显示婚姻状态 + 婚姻专属按钮。
     */
    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/婚姻面板", description = "【QQ官方】婚姻状态面板")
    public String marriagePanel(ChannelEvent event) {
        if (!"qqofficial".equals(event.getChannelId())) {
            return "💡 此功能仅在 QQ 官方渠道可用~";
        }
        if (!(event instanceof ChannelMessageEvent chMsg) || chMsg.getApi() == null) {
            return "❌ 面板发送失败";
        }

        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);

        // 获取婚姻状态文本
        String statusText = marriageTool.cpStatus(
            String.valueOf(userId), String.valueOf(groupId), null, userName);

        String markdown = "> **💒 姻缘殿**\n\n"
                + "> " + statusText.replace("\n", "\n> ") + "\n\n"
                + "> 点击下方按钮操作 👇";

        String keyboardJson = buildMarriagePanelKeyboard();

        boolean sent = false;
        try {
            Object api = chMsg.getApi();
            java.lang.reflect.Method m = api.getClass().getMethod("replyWithKeyboard", String.class, String.class);
            m.invoke(api, markdown, keyboardJson);
            sent = true;
        } catch (Exception e) {
            log.debug("replyWithKeyboard failed for marriage panel", e);
        }
        if (!sent) {
            chMsg.getApi().reply(statusText);
        }
        return null;
    }

    /**
     * QQ 官方渠道下，将文本结果和按钮面板一起发送。
     */
    private String wrapWithKeyboard(ChannelEvent event, String text) {
        if (text == null || !"qqofficial".equals(event.getChannelId())) return text;
        if (!(event instanceof ChannelMessageEvent chMsg) || chMsg.getApi() == null) return text;

        String markdown = text.replaceAll("(?m)^", "> ").trim();
        String keyboardJson = buildMarriageKeyboard();

        boolean sent = false;
        try {
            Object api = chMsg.getApi();
            java.lang.reflect.Method m = api.getClass().getMethod("replyWithKeyboard", String.class, String.class);
            m.invoke(api, markdown, keyboardJson);
            sent = true;
        } catch (NoSuchMethodException e) {
            log.debug("replyWithKeyboard not available");
        } catch (Exception e) {
            log.warn("replyWithKeyboard failed", e);
        }
        if (!sent) {
            chMsg.getApi().reply(text);
        }
        return null;
    }

    private String buildMarriageKeyboard() {
        return """
            {"content":{"rows":[
              {"buttons":[
                {"render_data":{"label":"💍 求婚","style":1},"action":{"type":2,"permission":{"type":2},"data":"/求婚"}},
                {"render_data":{"label":"❤️ 我的CP","style":0},"action":{"type":2,"permission":{"type":2},"data":"/我的CP"}},
                {"render_data":{"label":"💋 双修","style":0},"action":{"type":2,"permission":{"type":2},"data":"/双修"}}
              ]},
              {"buttons":[
                {"render_data":{"label":"📊 修仙状态","style":0},"action":{"type":2,"permission":{"type":2},"data":"/修仙状态"}},
                {"render_data":{"label":"🗡️ 修炼","style":0},"action":{"type":2,"permission":{"type":2},"data":"/修炼"}},
                {"render_data":{"label":"📅 签到","style":0},"action":{"type":2,"permission":{"type":2},"data":"/签到"}}
              ]}
            ]}}
            """.replaceAll("\\s*\\n\\s*", "");
    }

    private String buildMarriagePanelKeyboard() {
        return """
            {"content":{"rows":[
              {"buttons":[
                {"render_data":{"label":"💍 求婚","style":1},"action":{"type":2,"permission":{"type":2},"data":"/求婚"}},
                {"render_data":{"label":"❤️ 我的CP","style":0},"action":{"type":2,"permission":{"type":2},"data":"/我的CP"}},
                {"render_data":{"label":"💋 双修","style":0},"action":{"type":2,"permission":{"type":2},"data":"/双修"}},
                {"render_data":{"label":"💔 离婚","style":0},"action":{"type":2,"permission":{"type":2},"data":"/离婚"}}
              ]},
              {"buttons":[
                {"render_data":{"label":"🗡️ 修炼","style":0},"action":{"type":2,"permission":{"type":2},"data":"/修炼"}},
                {"render_data":{"label":"📊 修仙状态","style":0},"action":{"type":2,"permission":{"type":2},"data":"/修仙状态"}},
                {"render_data":{"label":"📜 修仙菜单","style":0},"action":{"type":2,"permission":{"type":2},"data":"/修仙菜单"}}
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

    private long resolveMentionTargetId(ChannelEvent event) {
        long selfId = botProperties.getSelfId();

        // OneBot 渠道：从 MessageChain 获取
        if (event instanceof MessageEvent me) {
            try {
                List<Long> ats = me.getMessage().getAts();
                if (ats != null && !ats.isEmpty()) {
                    for (Long at : ats) {
                        if (at != null && at != selfId && at > 0) {
                            return at;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // QQ 官方 / 通用渠道：从 ChannelMessageEvent 获取
        if (event instanceof ChannelMessageEvent chMsg) {
            // 1. 优先使用 mentions 列表（dispatcher 已过滤 bot）
            List<Long> mentions = chMsg.getMentions();
            if (mentions != null && !mentions.isEmpty()) {
                for (Long m : mentions) {
                    if (m != null && m > 0) {
                        return m;
                    }
                }
            }

            // 2. 后备：从原始 content 解析 <@!id> / <@id>（QQ 官方格式）
            String rawContent = chMsg.getRawContent();
            if (rawContent != null && !rawContent.isBlank()) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("<@!?([^>]+)>").matcher(rawContent);
                while (matcher.find()) {
                    String atId = matcher.group(1).trim();
                    if (!atId.isBlank()) {
                        try {
                            long id = Long.parseLong(atId);
                            if (id > 0 && id != selfId) {
                                return id;
                            }
                        } catch (NumberFormatException e) {
                            // openid 是字符串，无法解析为 long，跳过
                        }
                    }
                }
            }

            // 3. 最后尝试从 plainText 解析（兼容旧格式）
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

    private String getPlainText(ChannelEvent event) {
        if (event instanceof MessageEvent me) return me.getPlainText();
        if (event instanceof ChannelMessageEvent chMsg) return chMsg.getPlainText();
        return "";
    }
}

package com.dingdong.cultivation.bot;

import com.dingdong.channel.api.ChannelEvent;
import com.dingdong.channel.api.ChannelMessageEvent;
import com.dingdong.core.annotation.Command;
import com.dingdong.core.annotation.OnGroupMessage;
import com.dingdong.core.annotation.OnPrivateMessage;
import com.dingdong.core.annotation.Param;
import com.dingdong.core.config.BotProperties;
import com.dingdong.core.event.GroupMessageEvent;
import com.dingdong.core.event.MessageEvent;
import com.dingdong.cultivation.tool.CultivationTool;
import com.dingdong.cultivation.tool.SectTool;
import com.dingdong.cultivation.tool.PillShopTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class CultivationBot {

    private final CultivationTool cultivationTool;
    private final SectTool sectTool;
    private final PillShopTool pillShopTool;
    private final BotProperties botProperties;

    public CultivationBot(CultivationTool cultivationTool, SectTool sectTool, PillShopTool pillShopTool, BotProperties botProperties) {
        this.cultivationTool = cultivationTool;
        this.sectTool = sectTool;
        this.pillShopTool = pillShopTool;
        this.botProperties = botProperties;
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/修仙", description = "开启修仙之路（随机天赋）")
    public String startCultivation(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        String result = cultivationTool.startCultivation(
            String.valueOf(userId), String.valueOf(groupId), userName);
        return wrapWithKeyboard(event, result);
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/修炼", description = "主动修炼（1h冷却）")
    public String cultivate(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return wrapWithKeyboard(event, cultivationTool.cultivate(
            String.valueOf(userId), String.valueOf(groupId), userName));
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/突破", description = "突破当前小层")
    public String breakthrough(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return wrapWithKeyboard(event, cultivationTool.breakthrough(
            String.valueOf(userId), String.valueOf(groupId), userName));
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/渡劫", description = "突破大境界时触发天劫")
    public String dujie(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return wrapWithKeyboard(event, cultivationTool.dujie(
            String.valueOf(userId), String.valueOf(groupId), userName));
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/修仙状态", description = "查看修仙面板")
    public String cultivationStatus(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return wrapWithKeyboard(event, cultivationTool.cultivationStatus(
            String.valueOf(userId), String.valueOf(groupId), userName));
    }

    @OnGroupMessage
    @Command(value = "/修仙排行", description = "本群修仙排名")
    public String cultivationRanking(ChannelEvent event) {
        long groupId = resolveGroupId(event);
        return wrapWithKeyboard(event, cultivationTool.cultivationRanking(String.valueOf(groupId)));
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/修仙菜单", description = "显示修仙菜单")
    @Command(value = "/修仙帮助", description = "显示修仙帮助")
    public String cultivationMenu(ChannelEvent event) {
        // QQ 官方渠道原生支持 Markdown，直接发送 Markdown 格式
        if ("qqofficial".equals(event.getChannelId()) && event instanceof com.dingdong.channel.api.ChannelMessageEvent chMsg
                && chMsg.getApi() != null) {
            chMsg.getApi().reply(buildMarkdownMenu());
            return null;
        }
        // OneBot/NapCat 渠道：使用纯文本格式（不支持 markdown 渲染）
        return buildPlainTextMenu();
    }

    /**
     * 【实验】QQ 官方渠道专属：带按钮的 Markdown 面板。
     * 点击按钮会在输入框填入对应指令，用户发送后触发。
     */
    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/修仙面板", description = "【QQ官方】带按钮的修仙面板")
    public String cultivationPanel(ChannelEvent event) {
        if (!"qqofficial".equals(event.getChannelId())) {
            return "💡 此功能仅在 QQ 官方渠道可用~\n请使用 `/修仙菜单` 查看指令";
        }
        if (!(event instanceof com.dingdong.channel.api.ChannelMessageEvent chMsg)
                || chMsg.getApi() == null) {
            return "❌ 面板发送失败";
        }

        // 构建带按钮的 Markdown 内容
        String markdown = """
            **════ 修仙面板 ════**

            💡 **新用户请先点击「🎋 开始修仙」按钮！**
            点击下方按钮快速操作 👇
            """;

        // 构建 keyboard JSON
        String keyboardJson = buildCultivationKeyboard();

        // 尝试调用 QQ 官方专用方法发送（通过反射避免模块间循环依赖）
        boolean sent = false;
        try {
            Object api = chMsg.getApi();
            java.lang.reflect.Method m = api.getClass().getMethod("replyWithKeyboard", String.class, String.class);
            m.invoke(api, markdown, keyboardJson);
            sent = true;
        } catch (NoSuchMethodException e) {
            log.debug("replyWithKeyboard not available, fallback to plain markdown");
        } catch (Exception e) {
            log.warn("replyWithKeyboard failed, fallback to plain markdown", e);
        }
        if (!sent) {
            chMsg.getApi().reply(markdown);
        }
        return null;
    }

    private String buildCultivationKeyboard() {
        // QQ 官方按钮 keyboard 格式
        // style: 0=灰色, 1=蓝色(醒目)
        // action.type: 2=在输入框填入指令（用户需手动发送）
        return """
            {"content":{"rows":[
              {"buttons":[
                {"render_data":{"label":"🎋 开始修仙","style":1},"action":{"type":2,"permission":{"type":2},"data":"/修仙"}},
                {"render_data":{"label":"🗡️ 修炼","style":1},"action":{"type":2,"permission":{"type":2},"data":"/修炼"}},
                {"render_data":{"label":"📊 修仙状态","style":0},"action":{"type":2,"permission":{"type":2},"data":"/修仙状态"}}
              ]},
              {"buttons":[
                {"render_data":{"label":"⚡ 突破","style":0},"action":{"type":2,"permission":{"type":2},"data":"/突破"}},
                {"render_data":{"label":"🌩️ 渡劫","style":0},"action":{"type":2,"permission":{"type":2},"data":"/渡劫"}},
                {"render_data":{"label":"💊 丹药商店","style":0},"action":{"type":2,"permission":{"type":2},"data":"/丹药商店"}}
              ]},
              {"buttons":[
                {"render_data":{"label":"📅 签到","style":0},"action":{"type":2,"permission":{"type":2},"data":"/签到"}},
                {"render_data":{"label":"🔮 运势","style":0},"action":{"type":2,"permission":{"type":2},"data":"/运势"}},
                {"render_data":{"label":"🏯 宗门状态","style":0},"action":{"type":2,"permission":{"type":2},"data":"/宗门状态"}}
              ]},
              {"buttons":[
                {"render_data":{"label":"💒 婚姻面板","style":1},"action":{"type":2,"permission":{"type":2},"data":"/婚姻面板"}},
                {"render_data":{"label":"❤️ 我的CP","style":0},"action":{"type":2,"permission":{"type":2},"data":"/我的CP"}},
                {"render_data":{"label":"💋 双修","style":0},"action":{"type":2,"permission":{"type":2},"data":"/双修"}}
              ]}
            ]}}
            """.replaceAll("\\s*\\n\\s*", "");
    }

    private String buildPillShopKeyboard() {
        // 丹药商店专用按钮：每个按钮直接填入「购买 丹药名」
        return """
            {"content":{"rows":[
              {"buttons":[
                {"render_data":{"label":"💊 购买 培元丹","style":1},"action":{"type":2,"permission":{"type":2},"data":"/购买 培元丹"}},
                {"render_data":{"label":"💊 购买 筑基丹","style":0},"action":{"type":2,"permission":{"type":2},"data":"/购买 筑基丹"}},
                {"render_data":{"label":"💊 购买 渡劫丹","style":0},"action":{"type":2,"permission":{"type":2},"data":"/购买 渡劫丹"}}
              ]},
              {"buttons":[
                {"render_data":{"label":"💊 购买 疗伤丹","style":0},"action":{"type":2,"permission":{"type":2},"data":"/购买 疗伤丹"}},
                {"render_data":{"label":"💊 购买 还魂丹","style":0},"action":{"type":2,"permission":{"type":2},"data":"/购买 还魂丹"}},
                {"render_data":{"label":"📊 修仙状态","style":0},"action":{"type":2,"permission":{"type":2},"data":"/修仙状态"}}
              ]}
            ]}}
            """.replaceAll("\\s*\\n\\s*", "");
    }

    private String buildMarkdownMenu() {
        return """
            **════ 修仙系统 ════**

            **【修炼】**
            `/修仙` · 开启修仙之路（随机天赋）
            `/修炼` · 主动修炼（1h冷却）
            `/突破` · 突破当前小层
            `/渡劫` · 突破大境界时触发天劫
            `/修仙状态` · 查看修仙面板
            `/修仙排行` · 本群修仙排名
            `/修仙菜单` · 显示本菜单

            **【切磋】**
            `/切磋 对方名字` · 发起切磋战斗
            `/应战` · 接受切磋挑战

            **【宗门】**（金丹期可用）
            `/创建宗门 名字` · 创建宗门
            `/加入宗门 名字` · 申请加入宗门
            `/退出宗门` · 退出宗门
            `/踢出宗门 成员名字` · 踢出宗门（宗主）
            `/捐献 数量` · 捐献灵石升级宗门
            `/宗门状态` · 查看宗门信息
            `/宗门排行` · 群内宗门排名

            **【丹药】**
            `/丹药商店` · 查看丹药/价格
            `/购买 丹药名` · 购买丹药

            **【签到运势】**
            `/签到` · 每日签到（修仙者额外修为）
            `/签到状态` · 签到记录和排名
            `/运势` · 今日运势

            **【姻缘双修】**
            `/求婚 对方名字` · 向TA求婚
            `/同意求婚` · 接受求婚
            `/离婚` · 解除婚姻
            `/我的CP` · 查看CP状态
            `/双修` · 道侣双修（24h冷却）

            ════════════════
            群聊需 @bot 或唤醒词 + 指令
            私聊直接发送指令即可""";
    }

    private String buildPlainTextMenu() {
        return """
                \n\n
            ═══ 修仙系统 ═══

            【修炼】
            /修仙 · 开启修仙之路（随机天赋）
            /修炼 · 主动修炼（1h冷却）
            /突破 · 突破当前小层
            /渡劫 · 突破大境界时触发天劫
            /修仙状态 · 查看修仙面板
            /修仙排行 · 本群修仙排名
            /修仙菜单 · 显示本菜单

            【切磋】
            /切磋 @某人 · 发起切磋战斗
            /应战 · 接受切磋挑战

            【宗门】（金丹期可用）
            /创建宗门 名字 · 创建宗门
            /加入宗门 名字 · 申请加入宗门
            /退出宗门 · 退出宗门
            /踢出宗门 @某人 · 踢出宗门（宗主）
            /捐献 数量 · 捐献灵石升级宗门
            /宗门状态 · 查看宗门信息
            /宗门排行 · 群内宗门排名

            【丹药】
            /丹药商店 · 查看丹药/价格
            /购买 丹药名 · 购买丹药

            【签到运势】
            /签到 · 每日签到（修仙者额外修为）
            /签到状态 · 签到记录和排名
            /运势 · 今日运势

            【姻缘双修】
            /求婚 @某人 · 向TA求婚
            /同意求婚 · 接受求婚
            /离婚 · 解除婚姻
            /我的CP · 查看CP状态
            /双修 · 道侣双修（24h冷却）

            ════════════════
            群聊需 @bot 或唤醒词 + 指令
            私聊直接发送指令即可""";
    }

    @OnGroupMessage
    @Command(value = "/切磋", description = "发起切磋挑战")
    public String sparInitiate(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        long targetId = resolveMentionTargetId(event);
        String targetName = "";
        if (targetId == 0) {
            String text = getPlainText(event);
            if (text != null) {
                String arg = extractArg(text, "切磋");
                if (!arg.isEmpty()) targetName = arg;
            }
        }

        // QQ 官方渠道：API 不推送 @ 信息，从 content 中解析目标名字
        if (targetId == 0 && "qqofficial".equals(event.getChannelId())) {
            if (!targetName.isBlank()) {
                targetId = Math.abs(targetName.hashCode());
                if (targetId == 0) targetId = 1;
                log.debug("[QQ官方/切磋] 从文本解析目标: name={}, hashId={}", targetName, targetId);
            } else {
                return "⚔️ 请发送 `/切磋 对方名字` 来发起挑战~\n（QQ官方渠道不支持@功能）";
            }
        }

        return wrapWithKeyboard(event, cultivationTool.sparInitiate(
            String.valueOf(userId), String.valueOf(groupId), userName,
            String.valueOf(targetId), targetName));
    }

    @OnGroupMessage
    @Command(value = "/应战", description = "接受切磋挑战")
    public String sparAccept(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return wrapWithKeyboard(event, cultivationTool.sparAccept(
            String.valueOf(userId), String.valueOf(groupId), userName));
    }

    @OnGroupMessage
    @Command(value = "/创建宗门 {名字}", description = "创建宗门（金丹期+500灵石）")
    public String createSect(ChannelEvent event, @Param("名字") String sectName) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        if (sectName == null || sectName.isBlank()) return "❌ 请输入宗门名称！如：创建宗门 青云宗";
        return wrapWithKeyboard(event, sectTool.createSect(
            String.valueOf(userId), String.valueOf(groupId), userName, sectName.trim()));
    }

    @OnGroupMessage
    @Command(value = "/加入宗门 {名字}", description = "申请加入宗门")
    public String joinSect(ChannelEvent event, @Param("名字") String sectName) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        if (sectName == null || sectName.isBlank()) return "❌ 请输入宗门名称！如：加入宗门 青云宗";
        return wrapWithKeyboard(event, sectTool.joinSect(
            String.valueOf(userId), String.valueOf(groupId), userName, sectName.trim()));
    }

    @OnGroupMessage
    @Command(value = "/退出宗门", description = "退出宗门")
    public String leaveSect(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return wrapWithKeyboard(event, sectTool.leaveSect(
            String.valueOf(userId), String.valueOf(groupId), userName));
    }

    @OnGroupMessage
    @Command(value = "/踢出宗门", description = "踢出宗门成员（宗主）")
    public String kickMember(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        long targetId = resolveMentionTargetId(event);
        String targetName = "";

        // QQ 官方渠道：API 不推送 @ 信息，从 content 中解析目标名字
        if (targetId == 0 && "qqofficial".equals(event.getChannelId())) {
            String text = getPlainText(event);
            if (text != null && !text.isBlank()) {
                String arg = text.replaceFirst("^/踢出宗门\\s*", "").trim();
                if (!arg.isBlank()) {
                    targetName = arg;
                    targetId = Math.abs(targetName.hashCode());
                    if (targetId == 0) targetId = 1;
                    log.debug("[QQ官方/踢出] 从文本解析目标: name={}, hashId={}", targetName, targetId);
                }
            }
            if (targetId == 0) {
                return "❌ 请发送 `/踢出宗门 成员名字` 来踢人~\n（QQ官方渠道不支持@功能）";
            }
        }

        return wrapWithKeyboard(event, sectTool.kickMember(
            String.valueOf(userId), String.valueOf(groupId), String.valueOf(targetId), targetName));
    }

    @OnGroupMessage
    @Command(value = "/捐献 {数量}", description = "捐献灵石升级宗门")
    public String donateSect(ChannelEvent event, @Param("数量") String amount) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        if (amount == null || amount.isBlank()) return "❌ 请输入捐献数量！如：捐献 100";
        return wrapWithKeyboard(event, sectTool.donateSect(
            String.valueOf(userId), String.valueOf(groupId), userName, amount.trim()));
    }

    @OnGroupMessage
    @Command(value = "/宗门状态", description = "查看宗门信息")
    public String sectStatus(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        return wrapWithKeyboard(event, sectTool.sectStatus(String.valueOf(userId), String.valueOf(groupId)));
    }

    @OnGroupMessage
    @Command(value = "/宗门排行", description = "群内宗门排名")
    public String sectRanking(ChannelEvent event) {
        long groupId = resolveGroupId(event);
        return wrapWithKeyboard(event, sectTool.sectRanking(String.valueOf(groupId)));
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/丹药商店", description = "查看丹药/价格")
    public String pillShop(ChannelEvent event) {
        String text = pillShopTool.pillShop();
        // QQ 官方渠道：发送带丹药购买按钮的面板
        if ("qqofficial".equals(event.getChannelId())
                && event instanceof ChannelMessageEvent chMsg
                && chMsg.getApi() != null) {
            String markdown = text.replaceAll("(?m)^", "> ").trim();
            String keyboardJson = buildPillShopKeyboard();
            boolean sent = false;
            try {
                Object api = chMsg.getApi();
                java.lang.reflect.Method m = api.getClass().getMethod("replyWithKeyboard", String.class, String.class);
                m.invoke(api, markdown, keyboardJson);
                sent = true;
            } catch (Exception e) {
                log.debug("replyWithKeyboard failed for pill shop", e);
            }
            if (!sent) {
                chMsg.getApi().reply(text);
            }
            return null;
        }
        return text;
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/购买 {丹药名}", description = "购买丹药")
    public String buyPill(ChannelEvent event, @Param("丹药名") String pillName) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        if (pillName == null || pillName.isBlank()) return "❌ 请输入丹药名称！如：购买 培元丹\n💡 说\"丹药商店\"查看所有丹药";
        return wrapWithKeyboard(event, pillShopTool.buyPill(
            String.valueOf(userId), String.valueOf(groupId), userName, pillName.trim()));
    }

    /**
     * QQ 官方渠道下，将文本结果和按钮面板一起发送。
     * OneBot 渠道直接返回文本。
     */
    private String wrapWithKeyboard(ChannelEvent event, String text) {
        if (text == null || !"qqofficial".equals(event.getChannelId())) return text;
        if (!(event instanceof ChannelMessageEvent chMsg) || chMsg.getApi() == null) return text;

        String markdown = text.replaceAll("(?m)^", "> ").trim();
        String keyboardJson = buildCultivationKeyboard();

        boolean sent = false;
        try {
            Object api = chMsg.getApi();
            java.lang.reflect.Method m = api.getClass().getMethod("replyWithKeyboard", String.class, String.class);
            m.invoke(api, markdown, keyboardJson);
            sent = true;
        } catch (NoSuchMethodException e) {
            log.debug("replyWithKeyboard not available, fallback to plain text");
        } catch (Exception e) {
            log.warn("replyWithKeyboard failed, fallback to plain text", e);
        }
        if (!sent) {
            chMsg.getApi().reply(text);
        }
        return null;
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

    private String getPlainText(ChannelEvent event) {
        if (event instanceof MessageEvent me) return me.getPlainText();
        if (event instanceof ChannelMessageEvent chMsg) return chMsg.getPlainText();
        return "";
    }

    private String extractArg(String text, String cmdPrefix) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.startsWith(cmdPrefix)) {
            return trimmed.substring(cmdPrefix.length()).trim();
        }
        if (trimmed.startsWith("/" + cmdPrefix)) {
            return trimmed.substring(("/" + cmdPrefix).length()).trim();
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
}

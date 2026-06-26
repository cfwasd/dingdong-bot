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
        return cultivationTool.startCultivation(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/修炼", description = "主动修炼（1h冷却）")
    public String cultivate(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return cultivationTool.cultivate(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/突破", description = "突破当前小层")
    public String breakthrough(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return cultivationTool.breakthrough(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/渡劫", description = "突破大境界时触发天劫")
    public String dujie(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return cultivationTool.dujie(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/修仙状态", description = "查看修仙面板")
    public String cultivationStatus(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return cultivationTool.cultivationStatus(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }

    @OnGroupMessage
    @Command(value = "/修仙排行", description = "本群修仙排名")
    public String cultivationRanking(ChannelEvent event) {
        long groupId = resolveGroupId(event);
        return cultivationTool.cultivationRanking(String.valueOf(groupId));
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
                // 用名字 hash 作为稳定的 targetId（确保 > 0）
                targetId = Math.abs(targetName.hashCode());
                if (targetId == 0) targetId = 1;
                log.debug("[QQ官方/切磋] 从文本解析目标: name={}, hashId={}", targetName, targetId);
            } else {
                return "⚔️ 请发送 `/切磋 对方名字` 来发起挑战~\n（QQ官方渠道不支持@功能）";
            }
        }

        return cultivationTool.sparInitiate(
            String.valueOf(userId), String.valueOf(groupId), userName,
            String.valueOf(targetId), targetName);
    }

    @OnGroupMessage
    @Command(value = "/应战", description = "接受切磋挑战")
    public String sparAccept(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return cultivationTool.sparAccept(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }

    @OnGroupMessage
    @Command(value = "/创建宗门 {名字}", description = "创建宗门（金丹期+500灵石）")
    public String createSect(ChannelEvent event, @Param("名字") String sectName) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        if (sectName == null || sectName.isBlank()) return "❌ 请输入宗门名称！如：创建宗门 青云宗";
        return sectTool.createSect(
            String.valueOf(userId), String.valueOf(groupId), userName, sectName.trim());
    }

    @OnGroupMessage
    @Command(value = "/加入宗门 {名字}", description = "申请加入宗门")
    public String joinSect(ChannelEvent event, @Param("名字") String sectName) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        if (sectName == null || sectName.isBlank()) return "❌ 请输入宗门名称！如：加入宗门 青云宗";
        return sectTool.joinSect(
            String.valueOf(userId), String.valueOf(groupId), userName, sectName.trim());
    }

    @OnGroupMessage
    @Command(value = "/退出宗门", description = "退出宗门")
    public String leaveSect(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return sectTool.leaveSect(
            String.valueOf(userId), String.valueOf(groupId), userName);
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

        return sectTool.kickMember(
            String.valueOf(userId), String.valueOf(groupId), String.valueOf(targetId), targetName);
    }

    @OnGroupMessage
    @Command(value = "/捐献 {数量}", description = "捐献灵石升级宗门")
    public String donateSect(ChannelEvent event, @Param("数量") String amount) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        if (amount == null || amount.isBlank()) return "❌ 请输入捐献数量！如：捐献 100";
        return sectTool.donateSect(
            String.valueOf(userId), String.valueOf(groupId), userName, amount.trim());
    }

    @OnGroupMessage
    @Command(value = "/宗门状态", description = "查看宗门信息")
    public String sectStatus(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        return sectTool.sectStatus(String.valueOf(userId), String.valueOf(groupId));
    }

    @OnGroupMessage
    @Command(value = "/宗门排行", description = "群内宗门排名")
    public String sectRanking(ChannelEvent event) {
        long groupId = resolveGroupId(event);
        return sectTool.sectRanking(String.valueOf(groupId));
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/丹药商店", description = "查看丹药/价格")
    public String pillShop() {
        return pillShopTool.pillShop();
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/购买 {丹药名}", description = "购买丹药")
    public String buyPill(ChannelEvent event, @Param("丹药名") String pillName) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        if (pillName == null || pillName.isBlank()) return "❌ 请输入丹药名称！如：购买 培元丹\n💡 说\"丹药商店\"查看所有丹药";
        return pillShopTool.buyPill(
            String.valueOf(userId), String.valueOf(groupId), userName, pillName.trim());
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

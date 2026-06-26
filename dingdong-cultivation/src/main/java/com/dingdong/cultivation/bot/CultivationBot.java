package com.dingdong.cultivation.bot;

import com.dingdong.channel.api.ChannelEvent;
import com.dingdong.channel.api.ChannelMessageEvent;
import com.dingdong.core.annotation.Command;
import com.dingdong.core.annotation.OnGroupMessage;
import com.dingdong.core.annotation.OnPrivateMessage;
import com.dingdong.core.event.GroupMessageEvent;
import com.dingdong.core.event.MessageEvent;
import com.dingdong.cultivation.tool.CultivationTool;
import com.dingdong.cultivation.tool.SectTool;
import com.dingdong.cultivation.tool.PillShopTool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CultivationBot {

    private final CultivationTool cultivationTool;
    private final SectTool sectTool;
    private final PillShopTool pillShopTool;

    public CultivationBot(CultivationTool cultivationTool, SectTool sectTool, PillShopTool pillShopTool) {
        this.cultivationTool = cultivationTool;
        this.sectTool = sectTool;
        this.pillShopTool = pillShopTool;
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/修仙", description = "开启修仙之路（随机天赋）")
    @Command(value = "修仙", description = "开启修仙之路（随机天赋）")
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
    @Command(value = "修炼", description = "主动修炼（1h冷却）")
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
    @Command(value = "突破", description = "突破当前小层")
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
    @Command(value = "渡劫", description = "突破大境界时触发天劫")
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
    @Command(value = "修仙状态", description = "查看修仙面板")
    public String cultivationStatus(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return cultivationTool.cultivationStatus(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }

    @OnGroupMessage
    @Command(value = "/修仙排行", description = "本群修仙排名")
    @Command(value = "修仙排行", description = "本群修仙排名")
    public String cultivationRanking(ChannelEvent event) {
        long groupId = resolveGroupId(event);
        return cultivationTool.cultivationRanking(String.valueOf(groupId));
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/修仙菜单", description = "显示修仙菜单")
    @Command(value = "修仙帮助", description = "显示修仙帮助")
    @Command(value = "修仙菜单", description = "显示修仙菜单")
    @Command(value = "修仙帮助", description = "显示修仙帮助")
    public String cultivationMenu() {
        return """
            ═══════════ 修仙系统 ═══════════

            【修炼】
              @bot 修仙        开启修仙之路（随机天赋）
              @bot 修炼        主动修炼（1h冷却）
              @bot 突破        突破当前小层
              @bot 渡劫        突破大境界时触发天劫
              @bot 修仙状态    查看修仙面板
              @bot 修仙排行    本群修仙排名
              @bot 修仙菜单    显示本菜单

            【切磋】
              @bot 切磋 @某人   发起切磋战斗
              @bot 应战         接受切磋挑战

            【宗门】（金丹期可用）
              @bot 创建宗门 名字    创建宗门
              @bot 加入宗门 名字    申请加入宗门
              @bot 退出宗门        退出宗门
              @bot 踢出宗门 @某人   踢出宗门（宗主）
              @bot 捐献 数量       捐献灵石升级宗门
              @bot 宗门状态        查看宗门信息
              @bot 宗门排行        群内宗门排名

            【丹药】
              @bot 丹药商店    查看丹药/价格
              @bot 购买 丹药名  购买丹药

            【签到运势】
              @bot 签到        每日签到（修仙者额外修为）
              @bot 签到状态    签到记录和排名
              @bot 运势        今日运势

            【姻缘双修】
              @bot 求婚 @某人   向TA求婚
              @bot 同意求婚     接受求婚
              @bot 离婚         解除婚姻
              @bot 我的CP       查看CP状态
              @bot 双修         道侣双修（24h冷却）
            ═══════════════════════════""";
    }

    @OnGroupMessage
    @Command(value = "/切磋", description = "发起切磋挑战")
    @Command(value = "切磋", description = "发起切磋挑战")
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
        return cultivationTool.sparInitiate(
            String.valueOf(userId), String.valueOf(groupId), userName,
            String.valueOf(targetId), targetName);
    }

    @OnGroupMessage
    @Command(value = "/应战", description = "接受切磋挑战")
    @Command(value = "应战", description = "接受切磋挑战")
    public String sparAccept(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return cultivationTool.sparAccept(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }

    @OnGroupMessage
    @Command(value = "/创建宗门", description = "创建宗门（金丹期+500灵石）")
    @Command(value = "创建宗门", description = "创建宗门（金丹期+500灵石）")
    public String createSect(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        String sectName = extractArg(getPlainText(event), "创建宗门");
        if (sectName.isEmpty()) return "❌ 请输入宗门名称！如：创建宗门 青云宗";
        return sectTool.createSect(
            String.valueOf(userId), String.valueOf(groupId), userName, sectName);
    }

    @OnGroupMessage
    @Command(value = "/加入宗门", description = "申请加入宗门")
    @Command(value = "加入宗门", description = "申请加入宗门")
    public String joinSect(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        String sectName = extractArg(getPlainText(event), "加入宗门");
        if (sectName.isEmpty()) return "❌ 请输入宗门名称！如：加入宗门 青云宗";
        return sectTool.joinSect(
            String.valueOf(userId), String.valueOf(groupId), userName, sectName);
    }

    @OnGroupMessage
    @Command(value = "/退出宗门", description = "退出宗门")
    @Command(value = "退出宗门", description = "退出宗门")
    public String leaveSect(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        return sectTool.leaveSect(
            String.valueOf(userId), String.valueOf(groupId), userName);
    }

    @OnGroupMessage
    @Command(value = "/踢出宗门", description = "踢出宗门成员（宗主）")
    @Command(value = "踢出宗门", description = "踢出宗门成员（宗主）")
    public String kickMember(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        long targetId = resolveMentionTargetId(event);
        return sectTool.kickMember(
            String.valueOf(userId), String.valueOf(groupId), String.valueOf(targetId));
    }

    @OnGroupMessage
    @Command(value = "/捐献", description = "捐献灵石升级宗门")
    @Command(value = "捐献", description = "捐献灵石升级宗门")
    public String donateSect(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        String amount = extractArg(getPlainText(event), "捐献");
        if (amount.isEmpty()) return "❌ 请输入捐献数量！如：捐献 100";
        return sectTool.donateSect(
            String.valueOf(userId), String.valueOf(groupId), userName, amount);
    }

    @OnGroupMessage
    @Command(value = "/宗门状态", description = "查看宗门信息")
    @Command(value = "宗门状态", description = "查看宗门信息")
    public String sectStatus(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        return sectTool.sectStatus(String.valueOf(userId), String.valueOf(groupId));
    }

    @OnGroupMessage
    @Command(value = "/宗门排行", description = "群内宗门排名")
    @Command(value = "宗门排行", description = "群内宗门排名")
    public String sectRanking(ChannelEvent event) {
        long groupId = resolveGroupId(event);
        return sectTool.sectRanking(String.valueOf(groupId));
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/丹药商店", description = "查看丹药/价格")
    @Command(value = "丹药商店", description = "查看丹药/价格")
    public String pillShop() {
        return pillShopTool.pillShop();
    }

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/购买", description = "购买丹药")
    @Command(value = "购买", description = "购买丹药")
    public String buyPill(ChannelEvent event) {
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        String userName = resolveUserName(event);
        String pillName = extractArg(getPlainText(event), "购买");
        if (pillName.isEmpty()) return "❌ 请输入丹药名称！如：购买 培元丹\n💡 说\"丹药商店\"查看所有丹药";
        return pillShopTool.buyPill(
            String.valueOf(userId), String.valueOf(groupId), userName, pillName);
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

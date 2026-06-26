
package com.dingdong.admin.bot;

import com.dingdong.channel.api.ChannelEvent;
import com.dingdong.channel.api.ChannelMessageEvent;
import com.dingdong.channel.api.ChannelMessageTarget;
import com.dingdong.core.annotation.Command;
import com.dingdong.core.annotation.OnGroupMessage;
import com.dingdong.core.annotation.OnPrivateMessage;
import com.dingdong.core.config.BotProperties;
import com.dingdong.core.handler.HandlerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 帮助命令：/help
 * 普通成员只显示非管理员命令，超级用户显示全部命令。
 */
@Component
@RequiredArgsConstructor
public class HelpBot {

    private final HandlerRegistry handlerRegistry;
    private final BotProperties botProperties;

    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/help", description = "显示可用命令列表")
    public String showHelp(ChannelEvent event) {
        long userId = extractUserId(event);
        boolean isAdmin = botProperties.getSuperUsers().contains(userId);
        return buildHelpText(isAdmin, event);
    }

    private long extractUserId(ChannelEvent event) {
        if (event instanceof com.dingdong.core.event.MessageEvent me) {
            return me.getUserId();
        }
        if (event instanceof ChannelMessageEvent chMsg) {
            ChannelMessageTarget target = chMsg.getMessageTarget();
            if (target != null && target.getUser() != null) {
                return target.getUser().getMappedId();
            }
        }
        return 0;
    }

    private String buildHelpText(boolean isAdmin, ChannelEvent event) {
        List<HandlerRegistry.CommandHelp> cmds = handlerRegistry.getHelpCommands(isAdmin);
        String channelId = event.getChannelId();
        boolean isOneBot = "onebot".equals(channelId);
        boolean isQqOfficial = "qqofficial".equals(channelId);

        StringBuilder sb = new StringBuilder();
        sb.append(isAdmin ? "【管理员命令列表】\n" : "【命令列表】\n");
        if (cmds.isEmpty()) {
            sb.append("暂无命令\n");
        } else {
            for (HandlerRegistry.CommandHelp cmd : cmds) {
                sb.append(cmd.template());
                if (cmd.description() != null && !cmd.description().isBlank()) {
                    sb.append(" — ").append(cmd.description());
                }
                if (cmd.adminOnly()) {
                    sb.append(" [管]");
                }
                sb.append("\n");
            }
        }

        // 根据渠道显示不同信息
        if (isQqOfficial) {
            sb.append("\n【QQ官方 Bot】\n");
            sb.append("发送命令即可触发，无需 @ 或唤醒词\n");
        } else {
            // OneBot11
            sb.append("\n【AI 能力】\n");
            sb.append("@我 或 喊唤醒词即可对话，支持以下能力：\n");
            sb.append("• 搜索/天气/新闻 — 自动联网查询\n");
            sb.append("• 翻译 — \"用英语说你好\" \"翻译成日语\"\n");
            sb.append("• 讲笑话/段子 — \"来个笑话\"\n");
            sb.append("• 今日运势 — \"今天运势怎样\"\n");
            sb.append("• 塔罗牌占卜 — \"帮我抽塔罗牌\"\n");
            sb.append("• 漂流瓶 — \"扔漂流瓶\" \"捞漂流瓶\"\n");
            sb.append("• 惩罚大转盘 — \"大转盘\" \"抽个惩罚\"\n");
            sb.append("• 石头剪刀布 — \"猜拳\"\n");
            sb.append("• 掷骰子 — \"掷个d20\" \"3d6\"\n");
            sb.append("• 8号球占卜 — \"8号球，今天会下雨吗\"\n");
            sb.append("• 随机选/抽签 — \"帮我选一个\"\n");
            sb.append("• 数学计算 — \"算一下 123*456\"\n");
            sb.append("• 日期倒计时 — \"距离春节还有多少天\"\n");
            sb.append("• 猜数字游戏 — \"来玩猜数字\"\n");
            sb.append("• 真心话大冒险 — \"真心话大冒险\"\n");
            sb.append("• 谜语/绕口令 — \"出个谜语\" \"来个绕口令\"\n");
            sb.append("• 每日签到 — \"签到\" \"签到状态\"\n");
            sb.append("• 群友结婚 — \"嫁给我\" \"我愿意\" \"我的CP\"\n");
            sb.append("• 修仙系统 — \"修仙菜单\"查看全部\n");
            sb.append("• 文字特效 — \"翻转文字\" \"删除线效果\"\n");
            sb.append("• 编码解码 — \"base64编码你好\"\n");
            sb.append("• 文生图 — \"画一只可爱的猫咪\" \"帮我画一张\"\n");
            sb.append("• 定时提醒 — \"10分钟后提醒我开会\"\n");
            sb.append("• 长久记忆 — 聊过的重要事情会记住\n");

            sb.append("\n【人格系统】\n");
            sb.append("/persona — 查看可用人格\n");
            sb.append("/persona 名称 — 切换你的专属人格\n");
            sb.append("每个人格独立设置，所有群通用\n");

            sb.append("\n【语音模式】\n");
            sb.append("/voice — 查看当前语音模式\n");
            sb.append("/voice on — 每次回复都发语音\n");
            sb.append("/voice off — 只发文字不发语音\n");
            sb.append("/voice default — 默认模式（50%概率语音）\n");

            sb.append("\n【其他】\n");
            sb.append("/clear — 清空当前会话记忆\n");
            sb.append("/安静 或 /silent — 开启/关闭群安静模式（3分钟）\n");
            sb.append("  安静模式下仅此命令可用，权限：普通<管理<群主<超管\n");
        }

        return sb.toString().trim();
    }
}

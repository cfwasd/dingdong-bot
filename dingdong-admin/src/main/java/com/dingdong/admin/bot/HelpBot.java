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
 * 根据渠道显示不同的命令列表，AI 能力在两个渠道均可用。
 * QQ 官方渠道使用 Markdown 格式（原生支持渲染），
 * OneBot/NapCat 渠道使用纯文本格式（不支持 markdown）。
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
        String channelId = event.getChannelId();
        List<HandlerRegistry.CommandHelp> cmds = handlerRegistry.getHelpCommands(isAdmin, channelId);

        // QQ 官方渠道原生支持 Markdown，直接发送 Markdown 格式 + 按钮
        if ("qqofficial".equals(channelId) && event instanceof com.dingdong.channel.api.ChannelMessageEvent chMsg
                && chMsg.getApi() != null) {
            String markdown = buildMarkdownHelp(isAdmin, cmds);
            String keyboardJson = buildHelpKeyboard();
            boolean sent = false;
            try {
                Object api = chMsg.getApi();
                java.lang.reflect.Method m = api.getClass().getMethod("replyWithKeyboard", String.class, String.class);
                m.invoke(api, markdown, keyboardJson);
                sent = true;
            } catch (Exception e) {
                // fallback
            }
            if (!sent) {
                chMsg.getApi().reply(markdown);
            }
            return null;
        }
        // OneBot/NapCat 渠道：使用纯文本格式
        return buildPlainTextHelp(isAdmin, cmds);
    }

    /**
     * 【QQ官方】帮助面板：按钮为主的面板，类似修仙面板风格。
     */
    @OnGroupMessage
    @OnPrivateMessage
    @Command(value = "/帮助面板", description = "【QQ官方】帮助面板（按钮为主）")
    public String helpPanel(ChannelEvent event) {
        if (!"qqofficial".equals(event.getChannelId())) {
            return "💡 此功能仅在 QQ 官方渠道可用~";
        }
        if (!(event instanceof ChannelMessageEvent chMsg) || chMsg.getApi() == null) {
            return "❌ 面板发送失败";
        }

        long userId = extractUserId(event);
        boolean isAdmin = botProperties.getSuperUsers().contains(userId);
        String markdown = buildHelpPanelMarkdown(isAdmin);
        String keyboardJson = buildHelpPanelKeyboard();

        boolean sent = false;
        try {
            Object api = chMsg.getApi();
            java.lang.reflect.Method m = api.getClass().getMethod("replyWithKeyboard", String.class, String.class);
            m.invoke(api, markdown, keyboardJson);
            sent = true;
        } catch (Exception e) {
            // fallback
        }
        if (!sent) {
            List<HandlerRegistry.CommandHelp> cmds = handlerRegistry.getHelpCommands(isAdmin, event.getChannelId());
            chMsg.getApi().reply(buildMarkdownHelp(isAdmin, cmds));
        }
        return null;
    }

    private String buildHelpPanelMarkdown(boolean isAdmin) {
        return """
            > **🎯 功能导航**
            >
            > **🎋 修仙面板** — 修仙入口
            > **🤖 Agent 能力** — 画图 · 搜索 · 讲笑话 · 塔罗牌 · 猜拳 · 抽签
            > **⚙️ 系统功能** — 人格 · 语音 · 重置会话 · 修仙菜单 · 安静模式
            >
            > 点击下方按钮使用对应功能 👇
            """.trim();
    }

    // ===================== Markdown 格式（QQ 官方渠道）=====================

    private String buildMarkdownHelp(boolean isAdmin, List<HandlerRegistry.CommandHelp> cmds) {
        StringBuilder sb = new StringBuilder();
        sb.append(isAdmin ? "**【QQ官方 Bot · 管理员命令】**\n" : "**【QQ官方 Bot · 命令列表】**\n");
        sb.append("群聊需 @bot + 指令，私聊直接发送指令\n\n");

        // 基础命令
        sb.append("**【基础命令】**\n");
        boolean hasBase = false;
        for (HandlerRegistry.CommandHelp cmd : cmds) {
            if (isCultivationCmd(cmd.template())) continue;
            if (cmd.template().contains("{") && cmd.template().contains("}")) continue;
            sb.append("`").append(cmd.template()).append("`");
            if (cmd.description() != null && !cmd.description().isBlank()) {
                sb.append(" · ").append(cmd.description());
            }
            if (cmd.adminOnly()) sb.append(" `[管]`");
            sb.append("\n");
            hasBase = true;
        }
        if (!hasBase) sb.append("暂无\n");

        sb.append("\n**【修仙系统】**\n");
        sb.append("发送 `修仙菜单` 查看完整修仙指令\n");
        sb.append("群聊需 @bot + 修仙指令触发\n");

        sb.append("\n**【AI 能力】**\n");
        sb.append("@bot 或喊唤醒词 + 想说的话即可对话：\n");
        appendMarkdownAiCapabilities(sb);

        return sb.toString().trim();
    }

    private void appendMarkdownAiCapabilities(StringBuilder sb) {
        sb.append("• 搜索/天气/新闻 — 自动联网查询\n");
        sb.append("• 翻译 — `\"用英语说你好\"` `\"翻译成日语\"`\n");
        sb.append("• 讲笑话/段子 — `\"来个笑话\"`\n");
        sb.append("• 塔罗牌占卜 — `\"帮我抽塔罗牌\"`\n");
        sb.append("• 漂流瓶 — `\"扔漂流瓶\"` `\"捞漂流瓶\"`\n");
        sb.append("• 大转盘/猜拳/骰子/8号球 — 趣味小游戏\n");
        sb.append("• 随机选/抽签 — `\"帮我选一个\"`\n");
        sb.append("• 数学计算/编码解码/文字特效\n");
        sb.append("• 猜数字/真心话大冒险/谜语/绕口令\n");
        sb.append("• 群友结婚 — `\"嫁给我\"` `\"我的CP\"`\n");
        sb.append("• 文生图 — `\"画一只可爱的猫咪\"`\n");
        sb.append("• 定时提醒 — `\"10分钟后提醒我\"`\n");
        sb.append("• 长久记忆 — 聊过的重要事情会记住\n");
    }

    // ===================== 纯文本格式（OneBot/NapCat 渠道）=====================

    private String buildPlainTextHelp(boolean isAdmin, List<HandlerRegistry.CommandHelp> cmds) {
        StringBuilder sb = new StringBuilder();
        sb.append(isAdmin ? "【管理员命令列表】\n" : "【命令列表】\n");
        sb.append("群聊需 @bot + 指令，私聊直接发送指令\n\n");

        if (cmds.isEmpty()) {
            sb.append("暂无命令\n");
        } else {
            for (HandlerRegistry.CommandHelp cmd : cmds) {
                sb.append(formatCmd(cmd));
            }
        }

        sb.append("\n【修仙系统】\n");
        sb.append("  发送 \"修仙菜单\" 查看完整修仙指令\n");
        sb.append("  群聊需 @bot + 修仙指令触发\n");

        sb.append("\n【AI 能力】\n");
        sb.append("@我 或 喊唤醒词即可对话：\n");
        appendPlainTextAiCapabilities(sb);

        sb.append("\n【人格系统】\n");
        sb.append("  /persona — 查看可用人格\n");
        sb.append("  /persona 名称 — 切换专属人格\n");

        sb.append("\n【语音模式】\n");
        sb.append("  /voice — 查看语音模式\n");
        sb.append("  /voice on/off/default — 切换模式\n");

        sb.append("\n【其他】\n");
        sb.append("  /new — 重置会话上下文\n");
        sb.append("  /安静 /silent — 群安静模式\n");

        return sb.toString().trim();
    }

    private void appendPlainTextAiCapabilities(StringBuilder sb) {
        sb.append("• 搜索/天气/新闻 — 自动联网查询\n");
        sb.append("• 翻译 — \"用英语说你好\" \"翻译成日语\"\n");
        sb.append("• 讲笑话/段子 — \"来个笑话\"\n");
        sb.append("• 塔罗牌占卜 — \"帮我抽塔罗牌\"\n");
        sb.append("• 漂流瓶 — \"扔漂流瓶\" \"捞漂流瓶\"\n");
        sb.append("• 大转盘/猜拳/骰子/8号球 — 趣味小游戏\n");
        sb.append("• 随机选/抽签 — \"帮我选一个\"\n");
        sb.append("• 数学计算/编码解码/文字特效\n");
        sb.append("• 猜数字/真心话大冒险/谜语/绕口令\n");
        sb.append("• 群友结婚 — \"嫁给我\" \"我的CP\"\n");
        sb.append("• 文生图 — \"画一只可爱的猫咪\"\n");
        sb.append("• 定时提醒 — \"10分钟后提醒我\"\n");
        sb.append("• 长久记忆 — 聊过的重要事情会记住\n");
    }

    /**
     * /help 的键盘：作为 Markdown 帮助的补充，提供快捷入口。
     */
    private String buildHelpKeyboard() {
        return """
            {"content":{"rows":[
              {"buttons":[
                {"render_data":{"label":"🎋 修仙面板","style":1},"action":{"type":2,"permission":{"type":2},"data":"/修仙面板"}},
                {"render_data":{"label":"💒 婚姻面板","style":0},"action":{"type":2,"permission":{"type":2},"data":"/婚姻面板"}},
                {"render_data":{"label":"📊 修仙状态","style":0},"action":{"type":2,"permission":{"type":2},"data":"/修仙状态"}}
              ]},
              {"buttons":[
                {"render_data":{"label":"📅 签到","style":0},"action":{"type":2,"permission":{"type":2},"data":"/签到"}},
                {"render_data":{"label":"🔮 运势","style":0},"action":{"type":2,"permission":{"type":2},"data":"/运势"}},
                {"render_data":{"label":"💊 丹药商店","style":0},"action":{"type":2,"permission":{"type":2},"data":"/丹药商店"}}
              ]},
              {"buttons":[
                {"render_data":{"label":"🎭 人格","style":0},"action":{"type":2,"permission":{"type":2},"data":"/persona"}},
                {"render_data":{"label":"🔊 语音","style":0},"action":{"type":2,"permission":{"type":2},"data":"/voice"}},
                {"render_data":{"label":"🔄 重置会话","style":0},"action":{"type":2,"permission":{"type":2},"data":"/new"}}
              ]},
              {"buttons":[
                {"render_data":{"label":"📜 修仙菜单","style":0},"action":{"type":2,"permission":{"type":2},"data":"/修仙菜单"}},
                {"render_data":{"label":"🔕 安静模式","style":0},"action":{"type":2,"permission":{"type":2},"data":"/安静"}},
                {"render_data":{"label":"🎯 帮助面板","style":0},"action":{"type":2,"permission":{"type":2},"data":"/帮助面板"}}
              ]}
            ]}}
            """.replaceAll("\\s*\\n\\s*", "");
    }

    /**
     * /帮助面板 的键盘：按钮为主，一个修仙入口 + Agent 能力 + 系统功能。
     */
    private String buildHelpPanelKeyboard() {
        return """
            {"content":{"rows":[
              {"buttons":[
                {"render_data":{"label":"🎋 修仙面板","style":1},"action":{"type":2,"permission":{"type":2},"data":"/修仙面板"}}
              ]},
              {"buttons":[
                {"render_data":{"label":"🎨 画图","style":0},"action":{"type":2,"permission":{"type":2},"data":"画一只可爱的猫咪"}},
                {"render_data":{"label":"🌐 搜索","style":0},"action":{"type":2,"permission":{"type":2},"data":"帮我搜索今天的新闻"}},
                {"render_data":{"label":"💬 讲笑话","style":0},"action":{"type":2,"permission":{"type":2},"data":"来个笑话"}}
              ]},
              {"buttons":[
                {"render_data":{"label":"🔮 塔罗牌","style":0},"action":{"type":2,"permission":{"type":2},"data":"帮我抽塔罗牌"}},
                {"render_data":{"label":"🎲 猜拳","style":0},"action":{"type":2,"permission":{"type":2},"data":"猜拳"}},
                {"render_data":{"label":"🎯 抽签","style":0},"action":{"type":2,"permission":{"type":2},"data":"帮我抽签"}}
              ]},
              {"buttons":[
                {"render_data":{"label":"🎭 人格","style":0},"action":{"type":2,"permission":{"type":2},"data":"/persona"}},
                {"render_data":{"label":"🔊 语音","style":0},"action":{"type":2,"permission":{"type":2},"data":"/voice"}},
                {"render_data":{"label":"🔄 重置会话","style":0},"action":{"type":2,"permission":{"type":2},"data":"/new"}}
              ]},
              {"buttons":[
                {"render_data":{"label":"📜 修仙菜单","style":0},"action":{"type":2,"permission":{"type":2},"data":"/修仙菜单"}},
                {"render_data":{"label":"🔕 安静模式","style":0},"action":{"type":2,"permission":{"type":2},"data":"/安静"}},
                {"render_data":{"label":"❓ /help","style":0},"action":{"type":2,"permission":{"type":2},"data":"/help"}}
              ]}
            ]}}
            """.replaceAll("\\s*\\n\\s*", "");
    }

    // ===================== 工具方法 =====================

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

    private boolean isCultivationCmd(String template) {
        String t = template.startsWith("/") ? template.substring(1) : template;
        return t.equals("修仙") || t.equals("修炼") || t.equals("突破") || t.equals("渡劫")
                || t.equals("修仙状态") || t.equals("修仙排行") || t.equals("修仙菜单") || t.equals("修仙帮助")
                || t.equals("切磋") || t.equals("应战")
                || t.equals("创建宗门") || t.equals("加入宗门") || t.equals("退出宗门")
                || t.equals("踢出宗门") || t.equals("捐献") || t.equals("宗门状态") || t.equals("宗门排行")
                || t.equals("丹药商店") || t.equals("购买")
                || t.equals("签到") || t.equals("签到状态") || t.equals("运势")
                || t.equals("求婚") || t.equals("同意求婚") || t.equals("离婚") || t.equals("我的CP") || t.equals("双修");
    }

    private String formatCmd(HandlerRegistry.CommandHelp cmd) {
        StringBuilder sb = new StringBuilder();
        sb.append("  ").append(cmd.template());
        if (cmd.description() != null && !cmd.description().isBlank()) {
            sb.append(" — ").append(cmd.description());
        }
        if (cmd.adminOnly()) sb.append(" [管]");
        sb.append("\n");
        return sb.toString();
    }
}
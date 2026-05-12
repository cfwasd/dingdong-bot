package com.napcat.admin.bot;

import com.napcat.core.annotation.Command;
import com.napcat.core.annotation.OnGroupMessage;
import com.napcat.core.annotation.OnPrivateMessage;
import com.napcat.core.config.BotProperties;
import com.napcat.core.event.MessageEvent;
import com.napcat.core.handler.HandlerRegistry;
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
    public String showHelp(MessageEvent event) {
        boolean isAdmin = botProperties.getSuperUsers().contains(event.getUserId());
        return buildHelpText(isAdmin);
    }

    private String buildHelpText(boolean isAdmin) {
        List<HandlerRegistry.CommandHelp> cmds = handlerRegistry.getHelpCommands(isAdmin);
        if (cmds.isEmpty()) {
            return "暂无可用命令";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(isAdmin ? "【管理员命令列表】\n" : "【命令列表】\n");
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
        return sb.toString().trim();
    }
}

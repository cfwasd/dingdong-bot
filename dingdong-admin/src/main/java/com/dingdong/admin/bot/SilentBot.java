package com.dingdong.admin.bot;

import com.dingdong.channel.api.ChannelEvent;
import com.dingdong.channel.api.ChannelMessageEvent;
import com.dingdong.core.annotation.Command;
import com.dingdong.core.annotation.OnGroupMessage;
import com.dingdong.core.config.BotProperties;
import com.dingdong.core.group.GroupPreferenceStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SilentBot {

    @Autowired(required = false)
    private GroupPreferenceStore groupPreferenceStore;

    @Autowired
    private BotProperties botProperties;

    @Command(value = "/安静", description = "开启/关闭安静模式（3分钟）", silentModeAllowed = true)
    @Command(value = "/silent", description = "开启/关闭安静模式（3分钟）", silentModeAllowed = true)
    @OnGroupMessage
    public String toggleSilent(ChannelEvent event) {
        if (groupPreferenceStore == null) {
            return "安静模式功能未启用";
        }

        long groupId = resolveGroupId(event);
        long userId = resolveUserId(event);
        int userPriority = resolvePriority(event);

        GroupPreferenceStore.SilentInfo existing = groupPreferenceStore.getSilentInfo(groupId);

        if (existing != null && !existing.isExpired()) {
            GroupPreferenceStore.DeactivateResult result =
                    groupPreferenceStore.deactivateSilent(groupId, userId, userPriority);
            return switch (result) {
                case SUCCESS -> String.format("🔊 安静模式已关闭（剩余 %d 秒被取消）", existing.getRemainingSeconds());
                case NO_PERMISSION -> String.format("🔒 安静模式由 %s 激活，你的权限不足，无法关闭\n⏳ 剩余 %d 秒",
                        GroupPreferenceStore.SilentInfo.priorityName(existing.priorityLevel),
                        existing.getRemainingSeconds());
                case NOT_ACTIVE -> "当前没有活跃的安静模式";
            };
        }

        boolean activated = groupPreferenceStore.activateSilent(groupId, userId, userPriority, 0);
        if (activated) {
            return String.format("🤫 安静模式已开启（3分钟），期间仅 /安静 命令可用\n⏳ %s 已激活",
                    GroupPreferenceStore.SilentInfo.priorityName(userPriority));
        }
        return "开启安静模式失败";
    }

    private int resolvePriority(ChannelEvent event) {
        long userId = resolveUserId(event);
        if (botProperties.getSuperUsers().contains(userId)) {
            return GroupPreferenceStore.PRIORITY_SUPERUSER;
        }
        if (event instanceof com.dingdong.core.event.GroupMessageEvent ge && ge.getSenderObj() != null) {
            if (ge.getSenderObj().isOwner()) return GroupPreferenceStore.PRIORITY_OWNER;
            if (ge.getSenderObj().isAdmin()) return GroupPreferenceStore.PRIORITY_ADMIN;
        }
        return GroupPreferenceStore.PRIORITY_NORMAL;
    }

    private long resolveUserId(ChannelEvent event) {
        if (event instanceof com.dingdong.core.event.MessageEvent me) return me.getUserId();
        if (event instanceof ChannelMessageEvent chMsg) return chMsg.getUserId();
        return 0;
    }

    private long resolveGroupId(ChannelEvent event) {
        if (event instanceof com.dingdong.core.event.GroupMessageEvent ge) return ge.getGroupId();
        if (event instanceof ChannelMessageEvent chMsg) return chMsg.getGroupId();
        return 0;
    }
}

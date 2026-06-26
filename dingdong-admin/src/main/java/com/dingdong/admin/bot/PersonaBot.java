package com.dingdong.admin.bot;

import com.dingdong.channel.api.ChannelEvent;
import com.dingdong.channel.api.ChannelMessageEvent;
import com.dingdong.agent.agent.PersonaManager;
import com.dingdong.agent.agent.PersonaManager.PersonaDefinition;
import com.dingdong.agent.session.SessionKey;
import com.dingdong.agent.session.SessionManager;
import com.dingdong.core.annotation.Command;
import com.dingdong.core.annotation.OnGroupMessage;
import com.dingdong.core.annotation.OnPrivateMessage;
import com.dingdong.core.annotation.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PersonaBot {

    @Autowired(required = false)
    private PersonaManager personaManager;

    @Autowired(required = false)
    private SessionManager sessionManager;

    @Command(value = "/persona", description = "查看/切换人格")
    @OnGroupMessage
    @OnPrivateMessage
    public String listPersonas(ChannelEvent event) {
        if (personaManager == null || personaManager.isEmpty()) {
            return "没有配置任何人格～";
        }

        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        SessionKey key = new SessionKey(userId, groupId);
        PersonaDefinition active = personaManager.getActivePersona(key);

        StringBuilder sb = new StringBuilder();
        sb.append("可用人格：\n");
        for (PersonaDefinition p : personaManager.listPersonas()) {
            String marker = (active != null && active.getId().equals(p.getId())) ? " ← 当前" : "";
            sb.append("• ").append(p.getName()).append(" (").append(p.getId()).append(")");
            if (p.getDescription() != null && !p.getDescription().isBlank()) {
                sb.append(" - ").append(p.getDescription());
            }
            sb.append(marker).append("\n");
        }
        sb.append("发 /persona 人格名 切换你的专属人格，如 /persona 傲娇\n");
        sb.append("每个用户独立设置，所有群通用");
        return sb.toString().trim();
    }

    @Command(value = "/persona {name}", description = "切换到指定人格")
    @OnGroupMessage
    @OnPrivateMessage
    public String switchPersona(ChannelEvent event, @Param("name") String name) {
        if (personaManager == null || personaManager.isEmpty()) {
            return "没有配置任何人格～";
        }

        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        SessionKey key = new SessionKey(userId, groupId);

        if ("default".equalsIgnoreCase(name) || "默认".equals(name)) {
            personaManager.resetPersona(key);
            clearSession(event);
            PersonaDefinition def = personaManager.getActivePersona(key);
            return "已切换回默认人格：" + (def != null ? def.getName() : "");
        }

        PersonaDefinition target = personaManager.findPersonaByKeyword(name);
        if (target == null) {
            return "找不到人格：" + name + "\n发 /persona 查看可用人格";
        }

        personaManager.switchPersona(key, target.getId());
        clearSession(event);
        return "已切换为：" + target.getName()
                + (target.getDescription() != null ? " (" + target.getDescription() + ")" : "");
    }

    private void clearSession(ChannelEvent event) {
        if (sessionManager == null) return;
        long userId = resolveUserId(event);
        long groupId = resolveGroupId(event);
        sessionManager.getAndRemove(new SessionKey(userId, groupId));
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

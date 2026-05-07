package com.napcat.core.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BotProperties {

    private long selfId = 0;
    private String commandPrefix = "/";
    private boolean atMeTrigger = true;
    private boolean ignoreSelfMessage = true;
    private List<Long> superUsers = new ArrayList<>();
    /** 关键词唤醒列表。消息包含任一唤醒词时视为触发，无需 @。 */
    private List<String> wakeWords = new ArrayList<>();

    /**
     * 检查消息文本是否包含任一唤醒词（忽略大小写）。
     * wakeWords 为空时返回 false。
     */
    public boolean matchesWakeWord(String text) {
        if (text == null || text.isBlank() || wakeWords.isEmpty()) return false;
        String lower = text.toLowerCase();
        return wakeWords.stream().anyMatch(w -> lower.contains(w.toLowerCase()));
    }
}

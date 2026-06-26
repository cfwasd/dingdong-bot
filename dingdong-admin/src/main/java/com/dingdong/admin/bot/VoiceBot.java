package com.dingdong.admin.bot;

import com.dingdong.channel.api.ChannelEvent;
import com.dingdong.channel.api.ChannelMessageEvent;
import com.dingdong.core.annotation.Command;
import com.dingdong.core.annotation.OnGroupMessage;
import com.dingdong.core.annotation.OnPrivateMessage;
import com.dingdong.core.annotation.Param;
import com.dingdong.core.tts.VoicePreferenceStore;
import com.dingdong.core.tts.VoicePreferenceStore.VoiceMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VoiceBot {

    @Autowired(required = false)
    private VoicePreferenceStore voicePreferenceStore;

    @Command(value = "/voice", description = "查看/切换语音模式", channels = {"onebot"})
    @OnGroupMessage
    @OnPrivateMessage
    public String voiceStatus(ChannelEvent event) {
        if (voicePreferenceStore == null) {
            return "语音功能未启用";
        }
        long userId = resolveUserId(event);
        VoiceMode mode = voicePreferenceStore.getVoiceMode(userId);
        return "当前语音模式：" + mode.toDisplayString() + "\n\n" +
                "发 /voice on  切换为语音模式\n" +
                "发 /voice off 切换为文字模式\n" +
                "发 /voice default 切换为默认模式";
    }

    @Command(value = "/voice {mode}", description = "切换语音模式", channels = {"onebot"})
    @OnGroupMessage
    @OnPrivateMessage
    public String voiceSwitch(ChannelEvent event, @Param("mode") String mode) {
        if (voicePreferenceStore == null) {
            return "语音功能未启用";
        }
        long userId = resolveUserId(event);
        VoiceMode targetMode = VoiceMode.fromString(mode);
        voicePreferenceStore.setVoiceMode(userId, targetMode);
        return "语音模式已切换为：" + targetMode.toDisplayString();
    }

    private long resolveUserId(ChannelEvent event) {
        if (event instanceof com.dingdong.core.event.MessageEvent me) return me.getUserId();
        if (event instanceof ChannelMessageEvent chMsg) return chMsg.getUserId();
        return 0;
    }
}

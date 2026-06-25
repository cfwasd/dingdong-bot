package com.dingdong.channel.api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 渠道身份标识。
 * 统一表示用户/群组在某个渠道中的身份。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChannelIdentity {
    /** 所属渠道: "onebot" | "qqofficial" */
    private String channelId;
    /** 渠道原始 ID（openid / QQ号） */
    private String rawId;
    /** 映射后的 long 型 ID（用于 Agent 会话键） */
    private long mappedId;

    public static ChannelIdentity of(String channelId, String rawId, long mappedId) {
        return new ChannelIdentity(channelId, rawId, mappedId);
    }
}

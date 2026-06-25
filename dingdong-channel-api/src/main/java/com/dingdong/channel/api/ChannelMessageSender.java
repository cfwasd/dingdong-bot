package com.dingdong.channel.api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 消息发送者信息。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChannelMessageSender {
    private ChannelIdentity userId;
    private String nickname;
    /** 角色: "owner" | "admin" | "member" */
    private String role;
}

package com.dingdong.channel.api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 消息发送结果。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageResult {
    private boolean success;
    private String messageId;
    private String errorMessage;

    public static MessageResult ok(String messageId) {
        return new MessageResult(true, messageId, null);
    }

    public static MessageResult fail(String error) {
        return new MessageResult(false, null, error);
    }
}

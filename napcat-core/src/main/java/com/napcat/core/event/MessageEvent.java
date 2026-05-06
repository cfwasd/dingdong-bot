package com.napcat.core.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.napcat.core.message.MessageChain;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class MessageEvent extends OB11Event {


    @JsonProperty("message_id")
    private int messageId;

    @JsonProperty("user_id")
    private long userId;

    @JsonProperty("message")
    private MessageChain message;

    @JsonProperty("raw_message")
    private String rawMessage;

    @JsonProperty("font")
    private int font;

    @JsonProperty("sender")
    private Sender sender;

    public String getPlainText() {
        return message == null ? "" : message.toPlainText();
    }

    public void reply(String text) {
        com.napcat.core.context.EventContext ctx = com.napcat.core.context.EventContextHolder.get();
        if (ctx != null && ctx.getApi() != null) {
            if (this instanceof GroupMessageEvent ge) {
                ctx.getApi().sendGroupMessage(ge.getGroupId(), text);
            } else {
                ctx.getApi().sendPrivateMessage(getUserId(), text);
            }
        }
    }

    public void reply(MessageChain chain) {
        com.napcat.core.context.EventContext ctx = com.napcat.core.context.EventContextHolder.get();
        if (ctx != null && ctx.getApi() != null) {
            if (this instanceof GroupMessageEvent ge) {
                ctx.getApi().sendGroupMessage(ge.getGroupId(), chain);
            } else {
                ctx.getApi().sendPrivateMessage(getUserId(), chain);
            }
        }
    }
}

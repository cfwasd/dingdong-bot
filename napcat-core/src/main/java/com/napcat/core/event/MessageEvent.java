package com.napcat.core.event;

import com.dingdong.channel.api.ChannelIdentity;
import com.dingdong.channel.api.ChannelMessageEvent;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.napcat.core.api.NapCatApi;
import com.napcat.core.context.EventContext;
import com.napcat.core.context.EventContextHolder;
import com.napcat.core.message.MessageChain;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class MessageEvent extends ChannelMessageEvent {

    @JsonProperty("self_id")
    private long selfId;

    @JsonProperty("message_id")
    private long messageId;

    @JsonProperty("user_id")
    private long userId;

    @JsonProperty("raw_message")
    private String rawMessage;

    @JsonProperty("message")
    private MessageChain message;

    @JsonProperty("sender")
    private Sender senderObj;

    /**
     * 不参与序列化，由 {@code NapCatLifecycle} 在事件分发前注入。
     * 解决异步回调（Agent/CompletableFuture）中 ThreadLocal EventContext 丢失的问题。
     */
    @JsonIgnore
    private transient NapCatApi napCatApi;

    public MessageEvent() {
        setChannelId("onebot");
    }

    public ChannelIdentity getChannelUser() {
        return ChannelIdentity.of("onebot", String.valueOf(userId), userId);
    }

    public ChannelIdentity getChannelGroup() {
        if (this instanceof GroupMessageEvent ge) {
            long gid = ge.getGroupId();
            return gid > 0 ? ChannelIdentity.of("onebot", String.valueOf(gid), gid) : null;
        }
        return null;
    }

    public String getPlainText() {
        return message == null ? "" : message.toPlainText();
    }

    /**
     * 回复纯文本。优先使用事件自身持有的 api 引用（跨线程安全），
     * 降级使用 ThreadLocal EventContext（同步处理器兼容）。
     */
    public void reply(String text) {
        NapCatApi targetApi = resolveApi();
        if (targetApi == null) return;
        if (this instanceof GroupMessageEvent ge) {
            targetApi.sendGroupMessage(ge.getGroupId(), text);
        } else {
            targetApi.sendPrivateMessage(getUserId(), text);
        }
    }

    public void reply(MessageChain chain) {
        NapCatApi targetApi = resolveApi();
        if (targetApi == null) return;
        if (this instanceof GroupMessageEvent ge) {
            targetApi.sendGroupMessage(ge.getGroupId(), chain);
        } else {
            targetApi.sendPrivateMessage(getUserId(), chain);
        }
    }

    private NapCatApi resolveApi() {
        if (napCatApi != null) return napCatApi;
        EventContext ctx = EventContextHolder.get();
        return ctx != null ? ctx.getApi() : null;
    }
}

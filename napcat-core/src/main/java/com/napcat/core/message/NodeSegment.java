package com.napcat.core.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NodeSegment extends MessageSegment {

    public NodeSegment(long userId, String nickname, MessageChain content) {
        super("node");
        setDataValue("user_id", String.valueOf(userId));
        setDataValue("nickname", nickname);
        setDataValue("content", content);
    }

    @JsonIgnore
    public long getUserId() {
        Object v = getData().get("user_id");
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @JsonIgnore
    public String getNickname() {
        Object v = getData().get("nickname");
        return v == null ? null : v.toString();
    }

    @SuppressWarnings("unchecked")
    @JsonIgnore
    public MessageChain getContent() {
        Object v = getData().get("content");
        if (v instanceof MessageChain) return (MessageChain) v;
        return null;
    }
}

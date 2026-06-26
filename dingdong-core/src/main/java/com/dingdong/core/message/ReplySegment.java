package com.dingdong.core.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReplySegment extends MessageSegment {

    public ReplySegment(int messageId) {
        super("reply");
        setDataValue("id", String.valueOf(messageId));
    }

    @JsonIgnore
    public int getMessageId() {
        Object id = getData().get("id");
        if (id == null) return 0;
        if (id instanceof Number) return ((Number) id).intValue();
        try {
            return Integer.parseInt(id.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

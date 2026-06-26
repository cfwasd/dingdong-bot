package com.dingdong.core.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AtSegment extends MessageSegment {

    public AtSegment(long qq) {
        super("at");
        setDataValue("qq", String.valueOf(qq));
    }

    @JsonIgnore
    public long getQq() {
        Object qq = getData().get("qq");
        if (qq == null) return 0;
        if (qq instanceof Number) return ((Number) qq).longValue();
        try {
            return Long.parseLong(qq.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @JsonIgnore
    public boolean isAtAll() {
        return "all".equals(String.valueOf(getData().get("qq")));
    }
}

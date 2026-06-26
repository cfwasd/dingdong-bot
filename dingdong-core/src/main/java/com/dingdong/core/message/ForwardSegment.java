package com.dingdong.core.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ForwardSegment extends MessageSegment {

    public ForwardSegment(List<NodeSegment> nodes) {
        super("forward");
        setDataValue("content", nodes);
    }

    @SuppressWarnings("unchecked")
    @JsonIgnore
    public List<NodeSegment> getNodes() {
        Object v = getData().get("content");
        if (v instanceof List) return (List<NodeSegment>) v;
        return null;
    }
}

package com.dingdong.core.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MarkdownSegment extends MessageSegment {

    public MarkdownSegment(String content) {
        super("markdown");
        setDataValue("content", content);
    }

    @JsonIgnore
    public String getContent() {
        Object v = getData().get("content");
        return v == null ? null : v.toString();
    }
}

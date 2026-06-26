package com.dingdong.core.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TextSegment extends MessageSegment {

    public TextSegment(String text) {
        super("text");
        setDataValue("text", text);
    }

    @JsonIgnore
    public String getText() {
        Object text = getData().get("text");
        return text == null ? "" : text.toString();
    }
}

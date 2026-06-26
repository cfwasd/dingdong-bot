package com.dingdong.core.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class VideoSegment extends MessageSegment {

    public VideoSegment(String file) {
        super("video");
        setDataValue("file", file);
    }

    @JsonIgnore
    public String getFile() {
        Object v = getData().get("file");
        return v == null ? null : v.toString();
    }
}

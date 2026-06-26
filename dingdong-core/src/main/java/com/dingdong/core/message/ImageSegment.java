package com.dingdong.core.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ImageSegment extends MessageSegment {

    public ImageSegment(String file) {
        super("image");
        setDataValue("file", file);
    }

    @JsonIgnore
    public String getFile() {
        Object v = getData().get("file");
        return v == null ? null : v.toString();
    }

    @JsonIgnore
    public String getUrl() {
        Object v = getData().get("url");
        return v == null ? null : v.toString();
    }
}

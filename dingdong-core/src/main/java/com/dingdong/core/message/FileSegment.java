package com.dingdong.core.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FileSegment extends MessageSegment {

    public FileSegment(String file, String name) {
        super("file");
        setDataValue("file", file);
        setDataValue("name", name);
    }

    @JsonIgnore
    public String getFile() {
        Object v = getData().get("file");
        return v == null ? null : v.toString();
    }

    @JsonIgnore
    public String getName() {
        Object v = getData().get("name");
        return v == null ? null : v.toString();
    }
}

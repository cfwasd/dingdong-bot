package com.napcat.core.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RecordSegment extends MessageSegment {

    public RecordSegment(String file) {
        super("record");
        setDataValue("file", file);
    }

    @JsonIgnore
    public String getFile() {
        Object v = getData().get("file");
        return v == null ? null : v.toString();
    }
}

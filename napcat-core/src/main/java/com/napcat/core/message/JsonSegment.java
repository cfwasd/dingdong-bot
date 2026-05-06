package com.napcat.core.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class JsonSegment extends MessageSegment {

    public JsonSegment(String data) {
        super("json");
        setDataValue("data", data);
    }

    @JsonIgnore
    public String getJsonData() {
        Object v = getData().get("data");
        return v == null ? null : v.toString();
    }
}

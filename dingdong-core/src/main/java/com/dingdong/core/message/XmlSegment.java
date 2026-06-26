package com.dingdong.core.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class XmlSegment extends MessageSegment {

    public XmlSegment(String data) {
        super("xml");
        setDataValue("data", data);
    }

    @JsonIgnore
    public String getXmlData() {
        Object v = getData().get("data");
        return v == null ? null : v.toString();
    }
}

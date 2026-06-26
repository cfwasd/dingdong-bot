package com.dingdong.core.message;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@JsonSerialize(using = MessageSegmentSerializer.class)
public abstract class MessageSegment {

    @JsonProperty("type")
    private String type;

    @JsonIgnore
    private Map<String, Object> data = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getData() {
        return data;
    }

    @JsonAnySetter
    public void setDataValue(String key, Object value) {
        this.data.put(key, value);
    }

    protected MessageSegment(String type) {
        this.type = type;
    }
}

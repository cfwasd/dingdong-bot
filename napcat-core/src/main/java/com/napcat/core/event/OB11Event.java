package com.napcat.core.event;

import com.dingdong.channel.api.ChannelEvent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class OB11Event extends ChannelEvent {

    @JsonProperty("self_id")
    private long selfId;

    @JsonProperty("time")
    private long time;

    public OB11Event() {
        setChannelId("onebot");
    }
}

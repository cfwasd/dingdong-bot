package com.napcat.core.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class HeartbeatEvent extends MetaEvent {

    @JsonProperty("status")
    private Object status;

    @JsonProperty("interval")
    private long interval;
}

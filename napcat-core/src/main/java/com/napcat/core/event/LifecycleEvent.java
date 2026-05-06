package com.napcat.core.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class LifecycleEvent extends MetaEvent {

    @JsonProperty("sub_type")
    private String subType;
}

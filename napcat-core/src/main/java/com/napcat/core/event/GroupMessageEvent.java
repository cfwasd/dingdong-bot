package com.napcat.core.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class GroupMessageEvent extends MessageEvent {

    @JsonProperty("group_id")
    private long groupId;

    @JsonProperty("sub_type")
    private String subType;

    @JsonProperty("message_seq")
    private long messageSeq;

    @JsonProperty("anonymous")
    private Anonymous anonymous;

    public boolean isAnonymous() {
        return anonymous != null;
    }
}

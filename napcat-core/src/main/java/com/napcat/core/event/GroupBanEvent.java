package com.napcat.core.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GroupBanEvent extends NoticeEvent {

    @JsonProperty("group_id")
    private long groupId;

    @JsonProperty("operator_id")
    private long operatorId;

    @JsonProperty("user_id")
    private long userId;

    @JsonProperty("duration")
    private long duration;
}

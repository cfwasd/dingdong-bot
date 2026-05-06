package com.napcat.core.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GroupIncreaseEvent extends NoticeEvent {

    @JsonProperty("group_id")
    private long groupId;

    @JsonProperty("operator_id")
    private long operatorId;

    @JsonProperty("user_id")
    private long userId;

    @JsonProperty("sub_type")
    private String subType;
}

package com.napcat.core.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FriendRecallEvent extends NoticeEvent {

    @JsonProperty("user_id")
    private long userId;

    @JsonProperty("message_id")
    private int messageId;
}

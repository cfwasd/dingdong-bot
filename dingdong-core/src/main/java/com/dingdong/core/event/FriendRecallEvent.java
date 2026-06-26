package com.dingdong.core.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FriendRecallEvent extends NoticeEvent {

    @JsonProperty("user_id")
    private long userId;

    @JsonProperty("message_id")
    private int messageId;
}

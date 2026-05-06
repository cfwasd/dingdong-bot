package com.napcat.core.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FriendRequestEvent extends RequestEvent {

    @JsonProperty("user_id")
    private long userId;

    @JsonProperty("comment")
    private String comment;

    @JsonProperty("flag")
    private String flag;
}

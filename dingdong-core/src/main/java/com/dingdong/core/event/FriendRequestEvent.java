package com.dingdong.core.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FriendRequestEvent extends RequestEvent {

    @JsonProperty("user_id")
    private long userId;

    @JsonProperty("comment")
    private String comment;

    @JsonProperty("flag")
    private String flag;
}

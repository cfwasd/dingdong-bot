package com.dingdong.core.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Sender {

    @JsonProperty("user_id")
    private long userId;

    @JsonProperty("nickname")
    private String nickname;

    @JsonProperty("sex")
    private String sex;

    @JsonProperty("age")
    private int age;

    @JsonProperty("card")
    private String card;

    @JsonProperty("area")
    private String area;

    @JsonProperty("level")
    private String level;

    @JsonProperty("role")
    private String role;

    @JsonProperty("title")
    private String title;

    public boolean isAdmin() {
        return "admin".equals(role) || isOwner();
    }

    public boolean isOwner() {
        return "owner".equals(role);
    }
}

package com.napcat.core.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BotProperties {

    private long selfId = 0;
    private String commandPrefix = "/";
    private boolean atMeTrigger = true;
    private boolean ignoreSelfMessage = true;
    private List<Long> superUsers = new ArrayList<>();
}

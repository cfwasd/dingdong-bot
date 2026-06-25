package com.napcat.core.context;

import com.dingdong.channel.api.ChannelEvent;
import com.napcat.core.api.NapCatApi;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class EventContext {
    private final ChannelEvent event;
    private final NapCatApi api;
    private final Map<String, Object> attrs = new HashMap<>();
}

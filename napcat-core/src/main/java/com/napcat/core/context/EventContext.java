package com.napcat.core.context;

import com.napcat.core.api.NapCatApi;
import com.napcat.core.event.OB11Event;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class EventContext {
    private final OB11Event event;
    private final NapCatApi api;
    private final Map<String, Object> attrs = new HashMap<>();
}

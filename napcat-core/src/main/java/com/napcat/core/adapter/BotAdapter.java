package com.napcat.core.adapter;

import com.napcat.core.api.ApiRequest;
import com.napcat.core.api.ApiResponse;
import com.napcat.core.event.OB11Event;

import java.util.function.Consumer;

public interface BotAdapter {

    String getId();

    void start();

    void stop();

    boolean isConnected();

    void sendApiRequest(ApiRequest<?> request, Consumer<ApiResponse> callback);

    void setEventConsumer(Consumer<OB11Event> consumer);
}

package com.dingdong.core.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class ApiRequest<T> {

    @JsonProperty("action")
    private String action;

    @JsonProperty("params")
    private T params;

    @JsonProperty("echo")
    private String echo;

    public static <T> ApiRequest<T> of(String action, T params) {
        ApiRequest<T> req = new ApiRequest<>();
        req.setAction(action);
        req.setParams(params);
        req.setEcho(UUID.randomUUID().toString());
        return req;
    }

    public static ApiRequest<Map<String, Object>> of(String action, Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must be paired");
        }
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return of(action, params);
    }
}

package com.napcat.core.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiResponse {

    @JsonProperty("status")
    private Object status;

    @JsonProperty("retcode")
    private int retcode;

    @JsonProperty("data")
    private Object data;

    @JsonProperty("echo")
    private String echo;

    @JsonProperty("message")
    private Object message;

    @JsonProperty("wording")
    private String wording;

    public boolean isOk() {
        String statusStr = status instanceof String ? (String) status : (status != null ? status.toString() : null);
        return retcode == 0 || "ok".equalsIgnoreCase(statusStr);
    }

    @SuppressWarnings("unchecked")
    public <T> T getData(Class<T> clazz) {
        if (data == null) return null;
        if (clazz.isInstance(data)) return (T) data;
        return null;
    }

    public String getMessageAsString() {
        if (message instanceof String) {
            return (String) message;
        }
        return null;
    }
}

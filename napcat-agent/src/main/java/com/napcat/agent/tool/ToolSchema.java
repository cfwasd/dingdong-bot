package com.napcat.agent.tool;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ToolSchema {
    private String name;
    private String description;
    private Map<String, ParameterSchema> parameters;
    private List<String> required;

    @Data
    public static class ParameterSchema {
        private String type;
        private String description;
        private List<String> enums;
    }
}

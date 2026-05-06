package com.napcat.agent.tool;

import lombok.Data;

import java.lang.reflect.Method;

@Data
public class ToolMethod {
    private final String name;
    private final String description;
    private final Object bean;
    private final Method method;
}

package com.napcat.core.handler;

import lombok.Data;

import java.lang.reflect.Method;
import java.util.function.Predicate;

@Data
public class HandlerMethod {
    private final Object bean;
    private final Method method;
    private final int priority;
    private final Predicate<Object> condition;
    private final boolean isCommand;
    private final String commandTemplate;
}
